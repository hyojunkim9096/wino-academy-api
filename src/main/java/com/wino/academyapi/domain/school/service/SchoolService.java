// src/main/java/com/wino/academyapi/domain/school/service/SchoolService.java
package com.wino.academyapi.domain.school.service;

import com.wino.academyapi.domain.region.repository.RegionRepository;   // ✅ 지역명→접두어 유추용
import com.wino.academyapi.domain.school.dto.SchoolSummary;
import com.wino.academyapi.domain.school.dto.SchoolUpsertRequest;
import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.wino.academyapi.domain.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 학교 CRUD + 검색 비즈니스 로직
 *
 * 정책 요약
 * - search :
 *   · keyword는 Repository에서 name/address/detailAddress 모두 대상으로 LIKE 검색
 *   · ✅ (보강1) admPrefix가 10자리(예: 4111000000)로 들어오면 의미있는 접두(2/5/8/10자리)로 축약
 *   · ✅ (보강2) 접두가 5자리이고 마지막이 '0'(예: 41110)인 경우 1차 조회 0건이면 4자리(4111)로 폴백 재조회
 *   · ✅ (보강3) admPrefix가 비어 있고 keyword가 '지역명'일 가능성이 있으면 Region에서 한 건 찾아 접두(2/5/8/10)로 유추
 *         예) "수원시" → depth=2 → 5자리 "41110" 우선, 0건이면 4자리 "4111"로 폴백
 * - create : externalCode NOT NULL 제약을 만족시키기 위해 MANUAL-<UUID> 자동 부여
 * - patch  : null이 아닌 필드만 반영(문자열은 trim 후 빈문자열은 null로 저장)
 * - 좌표 스케일 : DECIMAL(10,7) 기준
 */
@Service
@RequiredArgsConstructor
public class SchoolService {

    /** 좌표 스케일(DECIMAL(10,7)) */
    private static final int GEO_SCALE = 7;

    private final SchoolRepository repo;
    private final RegionRepository regionRepo; // ✅ 지역명 키워드 → 접두 유추

    /** 목록 검색 (정렬은 Controller에서 Pageable Sort로 주입) */
    @Transactional(readOnly = true)
    public Page<SchoolSummary> search(String admPrefix,
                                      SchoolStage stage,
                                      Boolean active,
                                      String keyword,
                                      Pageable pageable) {

        // ── 0) 입력 정리 ─────────────────────────────────────────────
        final String kwRaw = trimToNull(keyword);
        String admRaw = trimToNull(admPrefix);

        // ── 1) admPrefix가 10자리 코드 형태면 "의미있는 접두(2/5/8/10)"로 축약 ──
        //     예) 4111000000(수원시) → 41110, 4100000000(경기도) → 41
        String adm = normalizeAdmPrefix(admRaw);

        // ── 2) 키워드만 들어온 경우(지역명으로 추정) → Region에서 한 건 찾아 접두 유추 ──
        //     depth=2면 5자리 우선 시도, 0건일 때 4자리 폴백을 위해 후보도 함께 계산
        String candidate5 = null; // 1차 시도(예: 41110)
        String candidate4 = null; // 폴백(예: 4111)
        if (adm == null && kwRaw != null) {
            var top = regionRepo.searchByNameLike(kwRaw, PageRequest.of(0, 1));
            if (!top.isEmpty()) {
                var r = top.getContent().get(0);
                String code = r.getCode();  // 항상 10자리
                byte depth = r.getDepth();

                if (depth == 1) {
                    adm = code.substring(0, 2);      // 시/도 → 2자리
                } else if (depth == 2) {
                    candidate5 = code.substring(0, 5); // 시/군/구 → 5자리(일반)
                    candidate4 = code.substring(0, 4); // 특례(수원시 등) 대비 폴백
                    adm = candidate5;
                } else if (depth == 3) {
                    adm = code.substring(0, 8);      // 읍/면/동 → 8자리
                } else {
                    adm = code.substring(0, 10);     // 리 → 10자리
                }
            }
        }

        // ── 3) admPrefix 자체가 5자리 & 마지막이 '0'이면 4자리 폴백 후보 준비 ──
        //     (예: 41110 → 1차 41110 조회가 0건이면 4111로 재조회)
        if (adm != null && adm.length() == 5 && adm.charAt(4) == '0') {
            candidate5 = adm;
            candidate4 = adm.substring(0, 4);
        }

        // ── 4) 1차 조회 ────────────────────────────────────────────
        Page<School> page = repo.search(adm, stage, active, kwRaw, pageable);

        // ── 5) 폴백: 5자리 시도로 0건이고 4자리 후보가 있으면 재조회 ─────────
        if (page.getTotalElements() == 0 && candidate4 != null) {
            page = repo.search(candidate4, stage, active, kwRaw, pageable);
        }

        // ── 6) DTO 매핑 ────────────────────────────────────────────
        return page.map(this::toSummary);
    }

    /** 단건 조회 */
    @Transactional(readOnly = true)
    public School get(Long id) {
        return repo.findById(id).orElseThrow();
    }

    /** 생성 (수동 생성 시 externalCode 자동 부여) */
    @Transactional
    public Long create(SchoolUpsertRequest dto) {
        School s = new School();
        s.setExternalCode(genManualExternalCode()); // 업서트 키
        apply(s, dto);
        return repo.save(s).getId();
    }

    /** 부분 수정 */
    @Transactional
    public void patch(Long id, SchoolUpsertRequest dto) {
        School s = repo.findById(id).orElseThrow();
        apply(s, dto); // 변경감지로 반영
    }

    /** 삭제 */
    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    // ───────────────────────── 내부 유틸 ─────────────────────────

    /** 10자리 코드 → 의미있는 접두(2/5/8/10)로 축약 */
    private static String normalizeAdmPrefix(String adm) {
        if (adm == null) return null;
        String a = adm.trim();
        if (a.length() != 10) return a;

        // depth=1: 앞 2자리 + 00000000
        if (a.substring(2).equals("00000000")) return a.substring(0, 2);
        // depth=2: 앞 5자리 + 00000
        if (a.substring(5).equals("00000")) return a.substring(0, 5);
        // depth=3: 앞 8자리 + 00
        if (a.substring(8).equals("00")) return a.substring(0, 8);

        // 그 외(leaf) 그대로
        return a;
    }

    /** 엔티티 → 목록 요약 DTO */
    private SchoolSummary toSummary(School s) {
        return new SchoolSummary(
                s.getId(),
                s.getName(),
                s.getStage(),
                s.getHomepageUrl(),
                s.isActive(),
                s.getPostalCode(),
                s.getAddress(),
                s.getDetailAddress(), // ✅ detailAddress = 지번(옛 주소) 저장 규칙
                s.getAdmCode(),
                s.getLat(),
                s.getLng()
        );
    }

    /**
     * DTO → 엔티티 부분 반영
     * - name: 제공되면 공백은 무시, 비공백만 반영
     * - 문자열: trim, 빈문자열은 null 저장
     * - lat/lng: 소수점 7자리 스케일 고정
     * - externalCode: 변경하지 않음
     */
    private void apply(School s, SchoolUpsertRequest dto) {
        if (dto.name() != null) {
            String n = trim(dto.name());
            if (!n.isEmpty()) s.setName(n);
        }
        if (dto.stage() != null) s.setStage(dto.stage());
        if (dto.eduOfficeCode() != null) s.setEduOfficeCode(trimToNull(dto.eduOfficeCode()));
        if (dto.phone()        != null) s.setPhone(trimToNull(dto.phone()));
        if (dto.postalCode()   != null) s.setPostalCode(trimToNull(dto.postalCode()));
        if (dto.address()      != null) s.setAddress(trimToNull(dto.address()));
        if (dto.detailAddress()!= null) s.setDetailAddress(trimToNull(dto.detailAddress()));
        if (dto.admCode()      != null) s.setAdmCode(trimToNull(dto.admCode()));
        if (dto.lat() != null) s.setLat(scale(dto.lat()));
        if (dto.lng() != null) s.setLng(scale(dto.lng()));
        if (dto.homepageUrl() != null) s.setHomepageUrl(trimToNull(dto.homepageUrl()));
        if (dto.active() != null) s.setActive(dto.active());
    }

    /** 좌표 스케일 고정(HALF_UP) */
    private static BigDecimal scale(BigDecimal v) {
        return (v == null) ? null : v.setScale(GEO_SCALE, RoundingMode.HALF_UP);
    }

    /** 수동 생성 케이스용 externalCode 생성기 (UNIQUE 보장) */
    private static String genManualExternalCode() {
        return "MANUAL-" + UUID.randomUUID();
    }

    /** 공백 제거한 문자열 반환(null 안전) */
    private static String trim(String s) {
        return (s == null) ? "" : s.trim();
    }

    /** trim 후 빈 문자열이면 null */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
