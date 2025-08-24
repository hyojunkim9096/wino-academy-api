package com.wino.academyapi.global.storage;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.domain.file.repository.AttachFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * 로컬 디스크 저장소
 *
 * - 저장 루트: AppSetting(DB) → yml → 기본값 순으로 "storage.attach.base-path" 사용
 *   * dev/prod 별로는 key에 @dev/@prod 오버라이드 적용 (getProfileAware)
 * - 프로필 이미지는 profiles/YYYY/MM 디렉토리 하위에 저장
 * - 보안: 화이트리스트 + 매직넘버 스니핑 + 이미지 파싱 검증
 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageService {

    private final AttachFileRepository fileRepo;
    private final AppSettingService settingService;

    /** 허용 콘텐츠 타입 */
    private static final Set<String> ALLOWED_CT = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "image/webp"
    );

    /** 파일 크기 제한 (20MB) */
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    /** 저장 루트 디렉터리 (설정 or 기본) */
    private Path baseDir() throws IOException {
        // DB(AppSetting) → yml → 기본값("/data/wino-uploads")
        String base = settingService.getProfileAware("storage.attach.base-path", "/data/wino-uploads");
        if (base == null || base.isBlank()) {
            throw new IOException("storage.attach.base-path 설정이 비어있습니다.");
        }
        return Paths.get(base).toAbsolutePath().normalize();
    }

    /** 매직넘버로 1차 스니핑 */
    private static String sniff(byte[] head) {
        if (head == null || head.length < 12) return null;
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) return MediaType.IMAGE_JPEG_VALUE; // JPEG
        if ((head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) return MediaType.IMAGE_PNG_VALUE; // PNG
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F') return MediaType.IMAGE_GIF_VALUE; // GIF
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "image/webp"; // WebP
        return null;
    }

    /**
     * 프로필 이미지 저장
     * @return 저장된 AttachFile 엔티티
     */
    public AttachFile saveProfileImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IOException("빈 파일은 업로드할 수 없습니다.");
        if (file.getSize() > MAX_BYTES) throw new IOException("파일이 너무 큽니다. 최대 20MB 까지 허용됩니다.");

        // 1) 매직넘버 스니핑
        String magic;
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int n = in.read(head);
            magic = (n >= 4) ? sniff(head) : null;
        }

        // 2) Content-Type 헤더와 조합
        String reqCt = file.getContentType();
        String candidate = (magic != null) ? magic : (reqCt != null ? reqCt : "application/octet-stream");
        if (!ALLOWED_CT.contains(candidate)) {
            throw new IOException("이미지 파일만 업로드할 수 있습니다. (jpeg/png/gif/webp)");
        }

        // 3) 이미지 파싱 검증 (무결성 체크)
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("손상되었거나 이미지가 아닙니다.");
        }

        // 4) 디렉토리 및 파일명 결정
        LocalDate now = LocalDate.now();
        String directory = String.format("profiles/%04d/%02d", now.getYear(), now.getMonthValue());
        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String ext = extOf(originalName);
        String finalExt = normalizeExt(candidate, ext); // 매직넘버 기준 표준 확장자
        String savedName = UUID.randomUUID() + (finalExt.isBlank() ? "" : "." + finalExt);

        // 5) 실제 저장
        Path dir = baseDir().resolve(directory).normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(savedName).normalize();
        file.transferTo(target.toFile());

        // 6) 메타 저장 (relativePath 명시 저장)
        String relativePath = directory + "/" + savedName;

        AttachFile af = AttachFile.builder()
                .originalName(originalName)
                .savedName(savedName)
                .ext(finalExt)
                .contentType(candidate)
                .fileSize(file.getSize())      // ✅ 엔티티 필드명에 맞춤 (file_size)
                .directory(directory)
                .relativePath(relativePath)    // ✅ 조회 일관성 강화
                // absolutePath는 로컬 저장소에서는 보통 비워둠(특정 케이스만 채움)
                .build();

        return fileRepo.save(af);
    }

    /** 파일명에서 확장자 추출 */
    private static String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }

    /** 콘텐츠타입 기준 표준 확장자 보정 */
    private static String normalizeExt(String contentType, String fallback) {
        if (MediaType.IMAGE_JPEG_VALUE.equals(contentType)) return "jpg";
        if (MediaType.IMAGE_PNG_VALUE.equals(contentType))  return "png";
        if (MediaType.IMAGE_GIF_VALUE.equals(contentType))  return "gif";
        if ("image/webp".equals(contentType))               return "webp";
        return (fallback == null) ? "" : fallback.toLowerCase();
    }
}
