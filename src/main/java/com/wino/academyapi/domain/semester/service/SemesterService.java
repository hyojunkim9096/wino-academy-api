// src/main/java/com/wino/academyapi/domain/semester/service/SemesterService.java
package com.wino.academyapi.domain.semester.service;

import com.wino.academyapi.domain.semester.dto.SemesterDtos;
import com.wino.academyapi.domain.semester.entity.Semester;
import com.wino.academyapi.domain.semester.entity.SemesterType;
import com.wino.academyapi.domain.semester.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 학기 서비스
 * - list: 학부 선택(없으면 전체) + use 필터(ACTIVE/INACTIVE/ALL)
 * - upsert: 기간 유효성 검증, 초등부(E)는 EXAM_PREP 불가
 * - delete: 실제 삭제 대신 "미사용(useYn=false)" 처리
 *
 * ✅ 변경점
 *  - list(...) 추가: schoolStage == null/빈문자면 전체 학부 조회
 */
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository repo;

    @Transactional(readOnly = true)
    public List<Semester> list(String schoolStage, String useFilter) {
        final String stage = trimOrNull(schoolStage);
        final String uf = (useFilter == null ? "ACTIVE" : useFilter).toUpperCase();

        // ── 전체 학부 ──────────────────────────────────────────────────────
        if (stage == null) {
            return switch (uf) {
                case "ALL"      -> repo.findAllByOrderBySortOrderAsc();
                case "INACTIVE" -> repo.findByUseYnOrderBySortOrderAsc(false);
                default         -> repo.findByUseYnOrderBySortOrderAsc(true); // ACTIVE
            };
        }

        // ── 특정 학부 ──────────────────────────────────────────────────────
        return switch (uf) {
            case "ALL"      -> repo.findBySchoolStageOrderBySortOrderAsc(stage);
            case "INACTIVE" -> repo.findBySchoolStageAndUseYnOrderBySortOrderAsc(stage, false);
            default         -> repo.findBySchoolStageAndUseYnOrderBySortOrderAsc(stage, true); // ACTIVE
        };
    }

    @Transactional(readOnly = true)
    public Semester get(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("학기를 찾을 수 없습니다. id="+id));
    }

    @Transactional
    public Semester upsert(Long id, SemesterDtos.SemesterUpsertReq r) {
        validate(r);

        Semester e = (id==null) ? new Semester() : repo.findById(id).orElseThrow();
        e.setSchoolStage(r.schoolStage());
        e.setCode(r.code());
        e.setName(r.name());
        e.setSemesterType(r.semesterType());
        e.setStartDate(r.startDate());
        e.setEndDate(r.endDate());
        e.setSortOrder(r.sortOrder()==null ? 0 : r.sortOrder());
        if (r.useYn()!=null) e.setUseYn(r.useYn());

        try {
            return repo.save(e);
        } catch (DataIntegrityViolationException dup) {
            // UNIQUE (school_stage, code) 충돌 시 사용자 친화 메시지
            throw new IllegalArgumentException("동일 학부에 동일 코드가 존재합니다. (schoolStage="+r.schoolStage()+", code="+r.code()+")");
        }
    }

    @Transactional
    public void disable(Long id) {
        Semester e = repo.findById(id).orElseThrow();
        e.setUseYn(false); // 실제 삭제 대신 비활성화
        repo.save(e);
    }

    // ---------- 내부 유효성 검사 ----------

    private void validate(SemesterDtos.SemesterUpsertReq r) {
        if (r.startDate().isAfter(r.endDate())) {
            throw new IllegalArgumentException("startDate는 endDate보다 이후일 수 없습니다.");
        }
        // 초등부(E)는 시험대비 학기 금지
        if ("E".equalsIgnoreCase(r.schoolStage()) && r.semesterType() == SemesterType.EXAM_PREP) {
            throw new IllegalArgumentException("초등부(E)는 시험대비(EXAM_PREP) 학기를 등록할 수 없습니다.");
        }
    }

    private static String trimOrNull(String s){
        if (s == null) return null;
        final String t = s.trim();
        return t.isEmpty()? null : t;
    }
}