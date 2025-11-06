// src/main/java/com/wino/academyapi/domain/admin/staff/service/StaffAdminService.java
package com.wino.academyapi.domain.admin.staff.service;

import com.wino.academyapi.domain.admin.staff.dto.AdminUserDtos.*;
import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.entity.EmployeeType;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.global.file.PublicUrlHelper;          // ✅ 공개 URL 생성기
import com.wino.academyapi.global.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;                 // ✅ Spring Data Page
import org.springframework.data.domain.PageRequest;       // ✅ Spring Data PageRequest
import org.springframework.data.domain.Pageable;           // ✅ 여기! AWT 말고 Spring Data
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class StaffAdminService {

    private final AdminUserRepository userRepo;
    private final LocalFileStorageService storage;        // ✅ 화이트리스트 + 스니핑 내장
    private final PasswordEncoder passwordEncoder;

    // ✅ 추가: 저장 경로(절대/상대)를 /uploads/** 공개 URL로 바꿔주는 도우미
    private final PublicUrlHelper publicUrlHelper;

    /**
     * ✅ 직원 목록 조회 (필터 + 페이지)
     * - 정렬은 Repository @Query 의 ORDER BY(lower(userName) ASC, id DESC)로 DB에서 수행
     * - 프런트에서 이름순 정렬을 한 번 더 걸어도 되지만, 페이징 일관성을 위해 DB 정렬이 우선
     */
    @Transactional(readOnly = true)
    public Page<StaffSummary> list(String employeeType, String workLocation, String keyword, int page, int size) {
        // ✅ 정렬 없는 Pageable: 정렬은 JPQL ORDER BY에서 처리
        Pageable pageable = PageRequest.of(page, size);

        // "ALL" 또는 공백 → null 처리
        EmployeeType type = null;
        if (employeeType != null && !employeeType.isBlank() && !"ALL".equalsIgnoreCase(employeeType)) {
            try {
                type = EmployeeType.valueOf(employeeType.trim().toUpperCase());
            } catch (IllegalArgumentException ignore) {
                // 잘못된 값이면 필터 미적용
            }
        }
        String wl = (workLocation == null || workLocation.isBlank()) ? null : workLocation.trim().toUpperCase();
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<AdminUser> p = userRepo.search(type, wl, kw, pageable);
        return p.map(this::toSummary);
    }

    /** ✅ 단건 조회(상세) */
    @Transactional(readOnly = true)
    public StaffSummary get(Long id) {
        return userRepo.findById(id).map(this::toSummary).orElseThrow();
    }

    /** ✅ 일반 정보 수정 (EmployeeType 포함) */
    @Transactional
    public void update(Long id, StaffUpdateRequest req) {
        AdminUser u = userRepo.findById(id).orElseThrow();

        // ✅ 직원 구분(STAFF/TEACHER) 반영
        if (req.getEmployeeType() != null) {
            u.setEmployeeType(req.getEmployeeType());
        }

        if (req.getRoleCode() != null)       u.setRole(req.getRoleCode());
        if (req.getWorkLocation() != null)   u.setWorkLocation(req.getWorkLocation());
        if (req.getStatus() != null)         u.setStatus(req.getStatus());
        if (req.getEmail() != null)          u.setEmail(req.getEmail());
        if (req.getPhoneNumber() != null)    u.setPhoneNumber(req.getPhoneNumber());
        if (req.getUserName() != null)       u.setUserName(req.getUserName());

        // ✅ 비상 연락처 반영(프론트에서 보낸 값 저장)
        if (req.getEmergencyContact() != null) u.setEmergencyContact(req.getEmergencyContact());

        if (req.getPostalCode() != null)     u.setPostalCode(req.getPostalCode());
        if (req.getAddress() != null)        u.setAddress(req.getAddress());
        if (req.getDetailAddress() != null)  u.setDetailAddress(req.getDetailAddress());
        // 저장은 트랜잭션 종료 시점에 flush; @LastModifiedBy/@LastModifiedDate 는 JPA Auditing이 기록
    }

    /**
     * ✅ 비밀번호 변경
     * 정책:
     * - LOCKED → ACTIVE 로 전환(계정 잠금 해제)
     * - 실패 카운트 초기화, 잠금 시각 해제
     * - TEMPORARY → ACTIVE 자동전환은 하지 않음(운영 정책에 따라 유지)
     */
    @Transactional
    public void changePassword(Long id, String newPassword) {
        AdminUser u = userRepo.findById(id).orElseThrow();
        u.setPassword(passwordEncoder.encode(newPassword));

        if ("LOCKED".equalsIgnoreCase(u.getStatus())) {
            u.setStatus("ACTIVE");
        }
        u.setFailedLoginCount(0);
        u.setAccountLockedUntil(null);
        // @LastModifiedBy/@LastModifiedDate 자동 기록
    }

    /**
     * ✅ 프로필 이미지 업로드
     * - storage.saveProfileImage(): 화이트리스트, 매직넘버 스니핑, 이미지 파싱검증, UUID 저장, 메타기록
     * - 성공 시 AdminUser.profileImage FK 연결
     * @return 저장된 파일 PK (AttachFile.id)
     */
    @Transactional
    public Long uploadProfile(Long id, MultipartFile file) throws IOException {
        AdminUser u = userRepo.findById(id).orElseThrow();
        AttachFile saved = storage.saveProfileImage(file); // 저장 + 메타 기록(보안 검증 포함)
        u.setProfileImage(saved);                          // FK 연결
        return saved.getId();
    }

    /**
     * ✅ AdminUser → StaffSummary 매핑 (employeeType + 사진 URL 포함)
     *
     * 프론트 규칙:
     *  - 이미지 src는 photoUrl(우선) → 없으면 "/uploads/" + photoPath
     *
     * 계산 규칙:
     *  1) relPath(상대경로) 우선순위
     *     - attach.relativePath
     *     - attach.directory + "/" + attach.savedName
     *  2) publicUrl(공개 URL)
     *     - attach.absolutePath 가 있으면 그것으로
     *     - 없으면 relPath 로
     *     - 둘 중 하나라도 있으면 PublicUrlHelper.toPublicUrl(...) 로 /uploads/** URL 산출
     */
    private StaffSummary toSummary(AdminUser u) {
        AttachFile f = u.getProfileImage();

        Long fileId = (f != null ? f.getId() : null);

        // 상대경로 계산
        String relPath = null;
        if (f != null) {
            if (hasText(f.getRelativePath())) {
                relPath = normalizeSlash(f.getRelativePath());
            } else if (hasText(f.getDirectory()) && hasText(f.getSavedName())) {
                relPath = normalizeSlash(f.getDirectory() + "/" + f.getSavedName());
            }
        }

        // 공개 URL 계산 (/uploads/**)
        String storedForUrl = null;
        if (f != null) {
            if (hasText(f.getAbsolutePath())) {
                storedForUrl = f.getAbsolutePath();                // 절대경로 → helper가 base-path 제거
            } else if (hasText(relPath)) {
                storedForUrl = relPath;                             // 상대경로 → 그대로 /uploads/<rel>
            }
        }
        String publicUrl = (storedForUrl != null ? publicUrlHelper.toPublicUrl(storedForUrl) : null);

        return StaffSummary.builder()
                .id(u.getId())
                .userId(u.getUserId())
                .userName(u.getUserName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .emergencyContact(u.getEmergencyContact())   // ✅ 응답에 포함
                .roleCode(u.getRole())
                .workLocation(u.getWorkLocation())
                .status(u.getStatus())
                .profileImageId(fileId)
                .employeeType(u.getEmployeeType())
                // ✅ 프런트가 기대하는 필드 세팅
                .photoPath(relPath)
                .photoUrl(publicUrl)
                .postalCode(u.getPostalCode())
                .address(u.getAddress())
                .detailAddress(u.getDetailAddress())
                .build();
    }

    // ---------- 내부 유틸 ----------

    /** null/빈문자열이 아닌지 검사 */
    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** 경로 구분자 정규화(윈도우 백슬래시 → 슬래시) */
    private static String normalizeSlash(String path) {
        return path.replace('\\', '/');
    }
}
