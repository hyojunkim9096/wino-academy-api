// src/main/java/com/wino/academyapi/domain/semester/controller/admin/AdminSemesterController.java
package com.wino.academyapi.domain.semester.controller.admin;

import com.wino.academyapi.domain.semester.dto.SemesterDtos;
import com.wino.academyapi.domain.semester.entity.Semester;
import com.wino.academyapi.domain.semester.service.SemesterService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 - 학기 관리 API
 * - 목록: useFilter(ACTIVE/INACTIVE/ALL) 지원, 기본 ACTIVE
 * - 상세: GET /{id}
 * - 업서트: POST (id param 유무로 create/update 구분)
 * - 삭제: DELETE /{id}  → 실제 삭제 대신 useYn=false 처리
 *
 * ✅ 변경점
 *  - schoolStage 파라미터를 선택으로 변경(required=false)
 *  - stage 미지정 시, "전체 학부"에서 필터/정렬 후 반환
 */
@RestController
@RequestMapping("/api/admin/semesters")
@RequiredArgsConstructor
public class AdminSemesterController {

    private final SemesterService svc;

    /** 학부별(선택) 학기 목록 (필터: ACTIVE/INACTIVE/ALL) */
    @GetMapping
    public List<SemesterDtos.SemesterRes> list(
            @RequestParam(required = false) String schoolStage,                // ⬅️ 선택값
            @RequestParam(required = false, defaultValue = "ACTIVE") String use
    ) {
        return svc.list(schoolStage, use).stream().map(this::toRes).toList();
    }

    /** 학기 상세 */
    @GetMapping("/{id}")
    public SemesterDtos.SemesterRes get(@PathVariable Long id) {
        return toRes(svc.get(id));
    }

    /** 학기 업서트 (id 없으면 생성, 있으면 수정) */
    @PostMapping
    public SemesterDtos.SemesterRes upsert(@RequestParam(required=false) Long id,
                                           @Validated @RequestBody SemesterDtos.SemesterUpsertReq r){
        Semester e = svc.upsert(id, r);
        return toRes(e);
    }

    /** 학기 삭제(비활성화) */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        svc.disable(id);
    }

    // ---------- mapper ----------

    private SemesterDtos.SemesterRes toRes(Semester e){
        return new SemesterDtos.SemesterRes(
                e.getId(), e.getSchoolStage(), e.getCode(), e.getName(),
                e.getSemesterType(), e.getStartDate(), e.getEndDate(),
                e.isUseYn(), e.getSortOrder()
        );
    }
}