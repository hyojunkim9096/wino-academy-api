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

// ✅ [신규] 계정 연결 로직을 위한 import
import com.wino.academyapi.domain.enduser.entity.EndUser;
import com.wino.academyapi.domain.enduser.entity.EndUserGuardianMap;
import com.wino.academyapi.domain.enduser.repository.EndUserRepository;
import com.wino.academyapi.domain.enduser.repository.EndUserGuardianMapRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
// ✅ [오류 수정] 누락된 import
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class GuardianAdminService {

    private final GuardianRepository repo;
    private final DbSessionVars dbVars;

    // ✅ [신규] 계정 연결용
    private final EndUserRepository endUserRepo;
    private final EndUserGuardianMapRepository mapRepo;
    private final PasswordEncoder passwordEncoder;


    @Transactional(readOnly = true)
    public Page<GuardianSummary> list(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String kw = (keyword==null||keyword.isBlank())?null:keyword.trim();

        // ✅ [수정] N+1이 해결된 DTO 프로젝션 쿼리 사용
        return repo.searchWithSummary(kw, pageable);
    }

    @Transactional(readOnly = true)
    public GuardianSummary get(Long id) {
        // ✅ [수정] 상세 조회 시에도 toSummary를 통해 계정 정보 포함
        return repo.findById(id).map(this::toSummary).orElseThrow(
                () -> new ResponseStatusException(NOT_FOUND, "보호자 정보를 찾을 수 없습니다.")
        );
    }

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
        // ✅ 생성 시에는 계정 정보(endUserMap)가 없음 (null)
        return toSummary(repo.save(g));
    }

    @Transactional
    public void update(Long id, GuardianUpdateRequest p) {
        dbVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        Guardian g = repo.findById(id).orElseThrow(
                () -> new ResponseStatusException(NOT_FOUND, "보호자 정보를 찾을 수 없습니다.")
        );

        if (p.getName()!=null) g.setName(p.getName());
        if (p.getPhone()!=null) g.setPhone(p.getPhone());
        if (p.getEmail()!=null) g.setEmail(p.getEmail());
        if (p.getPreferSms()!=null) g.setPreferSms(p.getPreferSms());
        if (p.getPreferEmail()!=null) g.setPreferEmail(p.getPreferEmail());
        if (p.getPreferPush()!=null) g.setPreferPush(p.getPreferPush());
        if (p.getPushUserKey()!=null) g.setPushUserKey(p.getPushUserKey());
        if (p.getPostalCode()!=null) g.setPostalCode(p.getPostalCode());
        if (p.getAddress()!=null) g.setAddress(p.getAddress());
        if (p.getDetailAddress()!=null) g.setDetailAddress(p.getDetailAddress());
        if (p.getMemo()!=null) g.setMemo(p.getMemo());

        // ✅ [신규] 보호자 정보 수정 시, 연결된 end_user 계정 정보도 동기화 (이름, 연락처, 이메일)
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
        // ✅ [수정] 계정 연결(map)이 먼저 삭제되어야 함 (FK)
        mapRepo.findByGuardianId(id).ifPresent(mapRepo::delete);

        repo.deleteById(id);
    }

    // =====================================================================
    // ✅ [신규] 보호자 계정 연결/해제 로직
    // =====================================================================

    @Transactional
    public void linkAccount(Long guardianId, AccountLinkRequest req) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Link Guardian Account");

        Guardian g = repo.findById(guardianId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "보호자 프로필을 찾을 수 없습니다."));

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
                .userId(savedUser.getId()) // PK
                .user(savedUser)
                .guardian(g)
                .build();
        mapRepo.save(map);
    }

    @Transactional
    public void unlinkAccount(Long guardianId) {
        dbVars.setAppVars(AppUserContext.getUserId(), "Unlink Guardian Account");

        EndUserGuardianMap map = mapRepo.findByGuardianId(guardianId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정 연결 정보를 찾을 수 없습니다."));

        mapRepo.delete(map);
    }


    /**
     * 엔티티 -> DTO 변환
     * (GuardianSummary에 계정 정보를 채워 넣습니다)
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

        // ✅ [수정] N+1이 발생하지만, 단건 조회(get)에서는 허용
        // 엔티티 연관관계(g.getEndUserMap())를 통해 조회
        if (g.getEndUserMap() != null && g.getEndUserMap().getUser() != null) {
            EndUser user = g.getEndUserMap().getUser();
            summary.setUserId(user.getId());
            summary.setLoginId(user.getLoginId());
            summary.setUserStatus(user.getStatus());
        }

        return summary;
    }
}