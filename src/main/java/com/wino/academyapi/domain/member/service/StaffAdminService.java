// src/main/java/com/wino/academyapi/domain/member/service/StaffAdminService.java
package com.wino.academyapi.domain.member.service;

import com.wino.academyapi.domain.member.dto.AdminUserDtos.*;
import com.wino.academyapi.domain.member.entity.AdminUser;
import com.wino.academyapi.domain.member.entity.EmployeeType;
import com.wino.academyapi.domain.member.repository.AdminUserRepository;
import com.wino.academyapi.domain.file.entity.AttachFile;
import com.wino.academyapi.global.file.PublicUrlHelper;
import com.wino.academyapi.global.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class StaffAdminService {

    private final AdminUserRepository userRepo;
    private final LocalFileStorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final PublicUrlHelper publicUrlHelper;

    @Transactional(readOnly = true)
    public Page<StaffSummary> list(String employeeType, String workLocation, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        EmployeeType type = null;
        if (employeeType != null && !employeeType.isBlank() && !"ALL".equalsIgnoreCase(employeeType)) {
            try { type = EmployeeType.valueOf(employeeType.trim().toUpperCase()); } catch (Exception ignore) {}
        }
        String wl = (workLocation == null || workLocation.isBlank()) ? null : workLocation.trim().toUpperCase();
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        return userRepo.search(type, wl, kw, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public StaffSummary get(Long id) {
        return userRepo.findById(id).map(this::toSummary).orElseThrow();
    }

    @Transactional
    public void update(Long id, StaffUpdateRequest req) {
        AdminUser u = userRepo.findById(id).orElseThrow();

        if (req.getEmployeeType() != null)   u.setEmployeeType(req.getEmployeeType());
        if (req.getRoleCode() != null)       u.setRole(req.getRoleCode());
        if (req.getWorkLocation() != null)   u.setWorkLocation(req.getWorkLocation());
        if (req.getStatus() != null)         u.setStatus(req.getStatus());
        if (req.getEmail() != null)          u.setEmail(req.getEmail());
        if (req.getPhoneNumber() != null)    u.setPhoneNumber(req.getPhoneNumber());
        if (req.getUserName() != null)       u.setUserName(req.getUserName());

        // ✅ [신규] 생년월일 수정
        if (req.getBirthdate() != null)      u.setBirthdate(req.getBirthdate());

        if (req.getEmergencyContact() != null) u.setEmergencyContact(req.getEmergencyContact());
        if (req.getPostalCode() != null)     u.setPostalCode(req.getPostalCode());
        if (req.getAddress() != null)        u.setAddress(req.getAddress());
        if (req.getDetailAddress() != null)  u.setDetailAddress(req.getDetailAddress());
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        AdminUser u = userRepo.findById(id).orElseThrow();
        u.setPassword(passwordEncoder.encode(newPassword));
        if ("LOCKED".equalsIgnoreCase(u.getStatus())) {
            u.setStatus("ACTIVE");
        }
        u.setFailedLoginCount(0);
        u.setAccountLockedUntil(null);
    }

    @Transactional
    public Long uploadProfile(Long id, MultipartFile file) throws IOException {
        AdminUser u = userRepo.findById(id).orElseThrow();
        AttachFile saved = storage.saveProfileImage(file);
        u.setProfileImage(saved);
        return saved.getId();
    }

    private StaffSummary toSummary(AdminUser u) {
        AttachFile f = u.getProfileImage();
        Long fileId = (f != null ? f.getId() : null);

        String relPath = null;
        if (f != null) {
            if (hasText(f.getRelativePath())) relPath = normalizeSlash(f.getRelativePath());
            else if (hasText(f.getDirectory()) && hasText(f.getSavedName()))
                relPath = normalizeSlash(f.getDirectory() + "/" + f.getSavedName());
        }
        String storedForUrl = null;
        if (f != null) {
            if (hasText(f.getAbsolutePath())) storedForUrl = f.getAbsolutePath();
            else if (hasText(relPath))        storedForUrl = relPath;
        }
        String publicUrl = (storedForUrl != null ? publicUrlHelper.toPublicUrl(storedForUrl) : null);

        return StaffSummary.builder()
                .id(u.getId())
                .userId(u.getUserId())
                .userName(u.getUserName())
                .birthdate(u.getBirthdate()) // ✅ [신규]
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .emergencyContact(u.getEmergencyContact())
                .roleCode(u.getRole())
                .workLocation(u.getWorkLocation())
                .status(u.getStatus())
                .profileImageId(fileId)
                .employeeType(u.getEmployeeType())
                .photoPath(relPath)
                .photoUrl(publicUrl)
                .postalCode(u.getPostalCode())
                .address(u.getAddress())
                .detailAddress(u.getDetailAddress())
                .build();
    }

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }
    private static String normalizeSlash(String path) { return path.replace('\\', '/'); }
}