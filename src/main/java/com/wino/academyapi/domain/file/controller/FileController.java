// src/main/java/com/wino/academyapi/domain/file/controller/FileController.java
package com.wino.academyapi.domain.file.controller;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.domain.file.repository.AttachFileRepository;
import com.wino.academyapi.global.file.PublicUrlHelper;           // ✅ 추가: /uploads 공개 URL 계산용
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;                 // ✅ 추가
import org.springframework.http.HttpHeaders;                      // ✅ 추가
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;                         // ✅ 추가
import java.nio.file.*;
import java.util.HashMap;                                        // ✅ 추가
import java.util.Map;                                            // ✅ 추가

/**
 * 첨부/이미지 원본 바이너리 응답
 *
 * 경로 구성 우선순위:
 *   1) attach_file.absolute_path
 *   2) storage.attach.base-path + attach_file.relative_path
 *   3) storage.attach.base-path + (attach_file.directory + "/" + attach_file.saved_name)
 *
 * base-path 키: storage.attach.base-path  (DB의 key@profile → DB 공통 → application.yml)
 *   - AppSettingService.getProfileAware(...) 사용
 *   - 기본값: /data/wino-uploads
 *
 * 엔드포인트:
 *   - GET  /api/files/{id}/raw        : 바이너리 응답(이전 호환)
 *   - HEAD /api/files/{id}/raw        : 메타 확인(길이/콘텐트타입/수정시각)
 *   - GET  /api/files/{id}/view       : 브라우저에서 보기(inline)
 *   - GET  /api/files/{id}/download   : 강제 다운로드(attachment)
 *   - GET  /api/files/{id}/public     : 공개 URL/상대경로/메타 JSON 반환(디버깅/프론트 보조)
 */
@Slf4j
@RestController
@RequestMapping("/api/files") // ✅ /v1 없음
@RequiredArgsConstructor
public class FileController {

    private static final String KEY_BASE_PATH = "storage.attach.base-path";
    private static final String DEFAULT_BASE  = "/data/wino-uploads";

    private final AttachFileRepository fileRepo;
    private final AppSettingService settingService;
    private final PublicUrlHelper publicUrlHelper;               // ✅ 추가

    /** 파일 원본(바이너리) - 호환 유지 */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable Long id) throws IOException {
        AttachFile f = fileRepo.findById(id).orElse(null);
        if (f == null) return ResponseEntity.notFound().build();

        Path path = resolvePathOrThrow(f);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.warn("File not found/unreadable. id={}, path={}", id, path);
            return ResponseEntity.notFound().build();
        }

        byte[] body = Files.readAllBytes(path);
        String ct = decideContentType(path, f);
        long len = body.length;
        long lastMod = Files.getLastModifiedTime(path).toMillis();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .lastModified(lastMod)
                .contentType(MediaType.parseMediaType(ct))
                .contentLength(len)
                .body(body);
    }

    /** 메타 확인용 HEAD (길이/타입/Last-Modified만 응답) */
    @RequestMapping(value = "/{id}/raw", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable Long id) throws IOException {
        AttachFile f = fileRepo.findById(id).orElse(null);
        if (f == null) return ResponseEntity.notFound().build();

        Path path = resolvePathOrThrow(f);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.warn("File not found/unreadable(HEAD). id={}, path={}", id, path);
            return ResponseEntity.notFound().build();
        }

        String ct = decideContentType(path, f);
        long len = Files.size(path);
        long lastMod = Files.getLastModifiedTime(path).toMillis();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .lastModified(lastMod)
                .contentType(MediaType.parseMediaType(ct))
                .contentLength(len)
                .build();
    }

    // ======================================================================
    // ✅ 추가 1) inline 보기 — Content-Disposition: inline; filename="..."
    // ======================================================================
    @GetMapping("/{id}/view")
    public ResponseEntity<byte[]> viewInline(@PathVariable Long id) throws IOException {
        AttachFile f = fileRepo.findById(id).orElse(null);
        if (f == null) return ResponseEntity.notFound().build();

        Path path = resolvePathOrThrow(f);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.warn("File not found/unreadable(view). id={}, path={}", id, path);
            return ResponseEntity.notFound().build();
        }

        byte[] body = Files.readAllBytes(path);
        String ct = decideContentType(path, f);
        long len = body.length;
        long lastMod = Files.getLastModifiedTime(path).toMillis();

        ContentDisposition cd = ContentDisposition.inline()
                .filename(decideFilename(f, path), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .lastModified(lastMod)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(ct))
                .contentLength(len)
                .body(body);
    }

    // ======================================================================
    // ✅ 추가 2) 강제 다운로드 — Content-Disposition: attachment; filename="..."
    // ======================================================================
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws IOException {
        AttachFile f = fileRepo.findById(id).orElse(null);
        if (f == null) return ResponseEntity.notFound().build();

        Path path = resolvePathOrThrow(f);
        if (!Files.exists(path) || !Files.isReadable(path)) {
            log.warn("File not found/unreadable(download). id={}, path={}", id, path);
            return ResponseEntity.notFound().build();
        }

        byte[] body = Files.readAllBytes(path);
        String ct = decideContentType(path, f);
        long len = body.length;
        long lastMod = Files.getLastModifiedTime(path).toMillis();

        ContentDisposition cd = ContentDisposition.attachment()
                .filename(decideFilename(f, path), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .lastModified(lastMod)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(ct))
                .contentLength(len)
                .body(body);
    }

    // ======================================================================
    // ✅ 추가 3) 공개 URL/상대경로/메타 JSON 제공 — 프론트/디버깅 보조
    //    예: GET /api/files/123/public  → { publicUrl:"/uploads/staff/...", relativePath:"staff/...", ... }
    // ======================================================================
    @GetMapping("/{id}/public")
    public ResponseEntity<Map<String, Object>> publicInfo(@PathVariable Long id) throws IOException {
        AttachFile f = fileRepo.findById(id).orElse(null);
        if (f == null) return ResponseEntity.notFound().build();

        Path path;
        boolean exists = true;
        try {
            path = resolvePathOrThrow(f);
            if (!Files.exists(path) || !Files.isReadable(path)) exists = false;
        } catch (Exception e) {
            exists = false;
            path = null;
        }

        String rel = safeRelative(f);
        String storedForUrl = (StringUtils.hasText(f.getAbsolutePath()) ? f.getAbsolutePath() : rel);
        String publicUrl = (storedForUrl != null ? publicUrlHelper.toPublicUrl(storedForUrl) : null);

        Map<String, Object> body = new HashMap<>();
        body.put("id", f.getId());
        body.put("relativePath", rel);
        body.put("publicUrl", publicUrl);
        body.put("contentType", f.getContentType());
        body.put("exists", exists);

        if (exists && path != null) {
            body.put("length", Files.size(path));
            body.put("lastModified", Files.getLastModifiedTime(path).toMillis());
            body.put("filename", decideFilename(f, path));
        }

        return ResponseEntity.ok(body);
    }

    /* ========================= 내부 유틸 ========================= */

    /**
     * 실제 파일 경로를 결정한다.
     * - absolute_path가 있으면 그대로 사용
     * - 아니면 basePath(프로필 인지) + (relative_path 또는 directory/saved_name)
     * - 베이스 디렉터리 밖으로 벗어나면(경로 트래버설) 차단
     */
    private Path resolvePathOrThrow(AttachFile f) throws IOException {
        // 1) 절대 경로 우선
        if (StringUtils.hasText(f.getAbsolutePath())) {
            try {
                Path p = Paths.get(f.getAbsolutePath()).toAbsolutePath().normalize();
                return p;
            } catch (Exception e) {
                log.warn("Invalid absolute_path. id={}, v={}", f.getId(), f.getAbsolutePath());
                // 계속 진행해서 상대경로 방식 시도
            }
        }

        // 2) base + 상대경로
        String baseStr = settingService.getProfileAware(KEY_BASE_PATH, DEFAULT_BASE);
        if (!StringUtils.hasText(baseStr)) {
            log.error("Base path is empty (key={}, default={})", KEY_BASE_PATH, DEFAULT_BASE);
            throw new IOException("Base path is not configured.");
        }
        Path base = Paths.get(baseStr).toAbsolutePath().normalize();

        String relative = f.getSafeRelativePath();
        if (!StringUtils.hasText(relative)) {
            log.warn("Relative path is empty. id={}", f.getId());
            throw new NoSuchFileException("Relative path not found for file id=" + f.getId());
        }

        Path composed = base.resolve(relative).normalize();

        // 2-1) 디렉터리 트래버설 방지: 베이스 밖이면 차단
        if (!composed.startsWith(base)) {
            log.error("Path traversal detected. base={}, rel={}, composed={}", base, relative, composed);
            throw new IOException("Invalid relative path.");
        }
        return composed;
    }

    /** 상대경로 계산: relative_path → 없으면 directory/saved_name → 없으면 null */
    private String safeRelative(AttachFile f) {
        if (StringUtils.hasText(f.getRelativePath())) {
            return normalizeSlash(f.getRelativePath());
        }
        if (StringUtils.hasText(f.getDirectory()) && StringUtils.hasText(f.getSavedName())) {
            return normalizeSlash(f.getDirectory() + "/" + f.getSavedName());
        }
        return null;
    }

    /** Content-Type 결정: DB 메타 → 시스템 추론 → application/octet-stream */
    private String decideContentType(Path path, AttachFile f) throws IOException {
        String ct = f.getContentType();
        if (StringUtils.hasText(ct)) return ct;
        String probed = Files.probeContentType(path);
        return (probed != null ? probed : MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /** 파일명 결정: saved_name → 실제 파일명 */
    private String decideFilename(AttachFile f, Path path) {
        if (StringUtils.hasText(f.getSavedName())) return f.getSavedName();
        if (path != null && path.getFileName() != null) return path.getFileName().toString();
        return "download.bin";
    }

    /** 경로 구분자 정규화(윈도우 백슬래시 → 슬래시) */
    private static String normalizeSlash(String s) {
        return s.replace('\\', '/');
    }
}
