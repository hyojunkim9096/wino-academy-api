// src/main/java/com/wino/academyapi/domain/guardian/service/GuardianAdminService.java
package com.wino.academyapi.domain.guardian.service;

import com.wino.academyapi.domain.guardian.dto.GuardianDtos.*;
import com.wino.academyapi.domain.guardian.entity.Guardian;
import com.wino.academyapi.domain.guardian.repository.GuardianRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wino.academyapi.domain.enduser.entity.EndUser;
import com.wino.academyapi.domain.enduser.entity.EndUserGuardianMap;
import com.wino.academyapi.domain.enduser.repository.EndUserRepository;
import com.wino.academyapi.domain.enduser.repository.EndUserGuardianMapRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 보호자 Admin 서비스
 * - 프로필 CRUD
 * - EndUser 계정 생성/연결/해제
 */
@Service
@RequiredArgsConstructor
public class GuardianAdminService {

    private final GuardianRepository repo;
    private final DbSessionVars dbVars;

    // 계정 연결용
    private final EndUserRepository endUserRepo;
    private final EndUserGuardianMapRepository mapRepo;
    private final PasswordEncoder passwordEncoder;

    /* ===== 목록 / 상세 ===== */

    @Transactional(readOnly = true)
    public Page<GuardianSummary> list(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        // N+1 이 해결된 DTO 프로젝션 쿼리 사용
        return repo.searchWithSummary(kw, pageable);
    }

    @Transactional(readOnly = true)
    public GuardianSummary get(Long id) {
        // 엔티티 → Summary 변환 (계정 정보 포함)
        return repo.findById(id)
                .map(this::toSummary)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "보호자 정보를 찾을 수 없습니다.")
                );
    }

    /* ===== 생성 / 수정 / 삭제 ===== */

    @Transactional
    public GuardianSummary create(GuardianCreateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        Guardian g = Guardian.builder()
                .name(p.getName())
                .phone(p.getPhone())
                .email(p.getEmail())
                .preferSms(p.isPreferSms())
                .preferEmail(p.isPreferEmail())
                .preferPush(p.isPreferPush())
                .pushUserKey(p.getPushUserKey())
                .postalCode(p.getPostalCode())
                .address(p.getAddress())
                .detailAddress(p.getDetailAddress())
                .memo(p.getMemo())
                .build();

        Guardian saved = repo.save(g);
        // 생성 시에는 계정 연결 없음
        return toSummary(saved);
    }

    @Transactional
    public void update(Long id, GuardianUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        Guardian g = repo.findById(id).orElseThrow(
                () -> new ResponseStatusException(NOT_FOUND, "보호자 정보를 찾을 수 없습니다.")
        );

        // 부분 수정 (null 체크 후 반영)
        if (p.getName() != null) g.setName(p.getName());
        if (p.getPhone() != null) g.setPhone(p.getPhone());
        if (p.getEmail() != null) g.setEmail(p.getEmail());
        if (p.getPreferSms() != null) g.setPreferSms(p.getPreferSms());
        if (p.getPreferEmail() != null) g.setPreferEmail(p.getPreferEmail());
        if (p.getPreferPush() != null) g.setPreferPush(p.getPreferPush());
        if (p.getPushUserKey() != null) g.setPushUserKey(p.getPushUserKey());
        if (p.getPostalCode() != null) g.setPostalCode(p.getPostalCode());
        if (p.getAddress() != null) g.setAddress(p.getAddress());
        if (p.getDetailAddress() != null) g.setDetailAddress(p.getDetailAddress());
        if (p.getMemo() != null) g.setMemo(p.getMemo());

        // 연결된 EndUser 계정이 있다면 이름/연락처/이메일 동기화
        if (g.getEndUserMap() != null && g.getEndUserMap().getUser() != null) {
            EndUser user = g.getEndUserMap().getUser();
            if (p.getName() != null) user.setName(p.getName());
            if (p.getPhone() != null) user.setPhone(p.getPhone());
            if (p.getEmail() != null) user.setEmail(p.getEmail());
        }
    }

    @Transactional
    public void delete(Long id) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        // FK 제약 때문에, EndUserGuardianMap 을 먼저 제거
        mapRepo.findByGuardianId(id).ifPresent(mapRepo::delete);

        repo.deleteById(id);
    }

    /* ===== 계정 연결 / 해제 ===== */

    @Transactional
    public void linkAccount(Long guardianId, AccountLinkRequest req) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Link Guardian Account");

        Guardian g = repo.findById(guardianId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "보호자 프로필을 찾을 수 없습니다.")
                );

        // 이미 연결된 계정이 있는지 체크
        if (g.getEndUserMap() != null || mapRepo.findByGuardianId(guardianId).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "이미 계정에 연결된 보호자 프로필입니다.");
        }

        String loginId = req.getLoginId().trim();
        if (endUserRepo.findByLoginIdIgnoreCase(loginId).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }

        EndUser user = EndUser.builder()
                .userType("GUARDIAN")
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(req.getPassword().trim()))
                .passwordAlgo("bcrypt")
                .name(g.getName())
                .phone(g.getPhone())
                .email(g.getEmail())
                .status("ACTIVE")
                .marketingOptIn(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        EndUser savedUser = endUserRepo.save(user);

        EndUserGuardianMap map = EndUserGuardianMap.builder()
                .userId(savedUser.getId()) // PK = FK(end_user.id)
                .user(savedUser)
                .guardian(g)
                .build();
        mapRepo.save(map);
    }

    @Transactional
    public void unlinkAccount(Long guardianId) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Unlink Guardian Account");

        EndUserGuardianMap map = mapRepo.findByGuardianId(guardianId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "계정 연결 정보를 찾을 수 없습니다.")
                );

        mapRepo.delete(map);
    }

    /* ===== 엔티티 → DTO 변환 ===== */

    /**
     * Guardian 엔티티를 GuardianSummary DTO로 변환
     * - EndUser 계정 정보까지 채워서 반환
     */
    private GuardianSummary toSummary(Guardian g) {
        GuardianSummary summary = GuardianSummary.builder()
                .id(g.getId())
                .name(g.getName())
                .phone(g.getPhone())
                .email(g.getEmail())
                .preferSms(g.isPreferSms())
                .preferEmail(g.isPreferEmail())
                .preferPush(g.isPreferPush())
                .pushUserKey(g.getPushUserKey())
                .postalCode(g.getPostalCode())
                .address(g.getAddress())
                .detailAddress(g.getDetailAddress())
                .memo(g.getMemo())
                .build();

        // 단건 조회에서는 엔티티 연관관계를 통해 계정 정보 조회 (N+1 허용)
        if (g.getEndUserMap() != null && g.getEndUserMap().getUser() != null) {
            EndUser user = g.getEndUserMap().getUser();
            summary.setUserId(user.getId());
            summary.setLoginId(user.getLoginId());
            summary.setUserStatus(user.getStatus());
        }

        return summary;
    }
}
