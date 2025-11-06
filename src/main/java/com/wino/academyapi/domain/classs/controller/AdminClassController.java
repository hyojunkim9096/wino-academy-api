// src/main/java/com/wino/academyapi/domain/classs/controller/AdminClassController.java
package com.wino.academyapi.domain.classs.controller;

import com.wino.academyapi.domain.classs.dto.ClassDtos;
import com.wino.academyapi.domain.classs.dto.AssignDtos;
import com.wino.academyapi.domain.classs.entity.ClassTimeslot;
import com.wino.academyapi.domain.classs.service.ClassAdminService;
import com.wino.academyapi.domain.classs.service.ClassAssignService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 반/과목/시간표 관리 컨트롤러
 * -----------------------------------------------------------------------------
 * - ✅ 반 생성/수정/목록에 공통코드(gradeCode/classCode) 반영
 * - 벌크 저장 시 startTimeCode/startTimeName(옵션)을 받아 스냅샷 저장(서비스 레이어에서 처리)
 * - 단건 슬롯 추가도 TimeslotReq에 코드/이름 필드가 추가됨
 * - DDL(class_timeslot)과의 스냅샷 필드 호환: code/label ⇄ startTimeCode/Name(JSON)
 * - 정렬 관련: class_master.sort_order 지원 (목록의 기본 정렬, 일괄 재정렬 엔드포인트)
 *
 * ⚠️ 주의:
 *   이 컨트롤러는 헤더(X-App-User-Id, X-Event-Note)를 직접 다루지 않는다.
 *   요청 헤더 → (WebMvc 인터셉터) AppUserContext(ThreadLocal) → (서비스) DbSessionVars.setAppVars(...)
 *   순으로 처리되어야 DB 트리거가 event_by / event_note를 기록한다.
 * -----------------------------------------------------------------------------
 */
@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
public class AdminClassController {

    private final ClassAdminService svc;
    private final ClassAssignService assignSvc;

    /** 반 목록 (지점/학부/사용여부=true)
     *  - 정렬: sort_order ASC, name ASC (리포지토리 메서드 기준)
     *  - 응답: sortOrder + ✅ gradeCode/classCode 포함
     */
    @GetMapping
    public List<ClassDtos.ClassRes> list(
            @RequestParam String workLocationCode,
            @RequestParam String schoolStage
    ){
        return svc.list(workLocationCode, schoolStage).stream().map(e ->
                new ClassDtos.ClassRes(
                        e.getId(), e.getWorkLocationCode(), e.getSchoolStage(), e.getSemesterId(),
                        e.getGradeCode(), e.getClassCode(),               // ✅ NEW
                        e.getCode(), e.getName(), e.getSortOrder(),
                        e.getHomeroomTeacherId(), e.getCapacity(), e.getStatus(), e.getMemo(), e.isUseYn()
                )
        ).toList();
    }

    /** 반 생성
     *  - 요청에 sortOrder가 주어지면 그대로 사용
     *  - 생략되면 같은 (지점×학부) 파티션의 마지막 다음 값으로 자동 배정
     *  - ✅ 응답에 gradeCode/classCode 포함
     */
    @PostMapping
    public ClassDtos.ClassRes create(@Validated @RequestBody ClassDtos.ClassUpsertReq r){
        var e = svc.upsert(null, r);
        return new ClassDtos.ClassRes(
                e.getId(), e.getWorkLocationCode(), e.getSchoolStage(), e.getSemesterId(),
                e.getGradeCode(), e.getClassCode(),                  // ✅ NEW
                e.getCode(), e.getName(), e.getSortOrder(),
                e.getHomeroomTeacherId(), e.getCapacity(), e.getStatus(), e.getMemo(), e.isUseYn()
        );
    }

    /** 반 수정 (기본 정보 & sortOrder 단건 수정 포함)
     *  - ✅ 응답에 gradeCode/classCode 포함
     */
    @PutMapping("/{id}")
    public ClassDtos.ClassRes update(@PathVariable Long id, @Validated @RequestBody ClassDtos.ClassUpsertReq r){
        var e = svc.upsert(id, r);
        return new ClassDtos.ClassRes(
                e.getId(), e.getWorkLocationCode(), e.getSchoolStage(), e.getSemesterId(),
                e.getGradeCode(), e.getClassCode(),                  // ✅ NEW
                e.getCode(), e.getName(), e.getSortOrder(),
                e.getHomeroomTeacherId(), e.getCapacity(), e.getStatus(), e.getMemo(), e.isUseYn()
        );
    }

    /** 반 목록 일괄 재정렬
     *  - 같은 (지점×학부) 파티션 내에서만 재정렬
     *  - 전달된 classIdsInOrder 순서대로 0..n을 부여
     */
    @PutMapping("/reorder")
    public void reorderClasses(
            @RequestParam String workLocationCode,
            @RequestParam String schoolStage,
            @RequestBody ClassDtos.ReorderClassReq r
    ){
        svc.reorderClasses(workLocationCode, schoolStage, r.classIdsInOrder());
    }

    /** 반-과목 목록(subjectName 포함) */
    @GetMapping("/{id}/subjects")
    public List<ClassDtos.ClassSubjectRes> listSubjects(@PathVariable Long id){
        return svc.listSubjectsWithName(id);
    }

    /** 반에 과목 추가 */
    @PostMapping("/{id}/subjects")
    public ClassDtos.ClassSubjectRes addSubject(@PathVariable Long id, @Validated @RequestBody ClassDtos.AddClassSubjectReq r){
        var cs = svc.addSubject(id, r.subjectId(), r.teacherId());
        return new ClassDtos.ClassSubjectRes(
                cs.getId(), cs.getClassId(), cs.getSubjectId(),
                null, // 단건 응답에는 subjectName 생략, 프론트는 목록 재조회로 동기화
                cs.getTeacherId(), cs.getSortOrder(), cs.isUseYn()
        );
    }

    /** 반-과목 삭제 */
    @DeleteMapping("/{id}/subjects/{csId}")
    public void remove(@PathVariable Long id, @PathVariable Long csId){
        svc.removeSubject(id, csId);
    }

    /** 반-과목 담당 교사 지정 */
    @PutMapping("/subjects/{csId}/teacher")
    public void setTeacher(@PathVariable Long csId, @RequestParam Long teacherId){
        svc.updateTeacher(csId, teacherId);
    }

    /** 반-과목 정렬 변경 */
    @PutMapping("/{id}/subjects/reorder")
    public void reorder(@PathVariable Long id, @RequestBody ClassDtos.ReorderReq r){
        svc.reorder(id, r.classSubjectIdsInOrder());
    }

    /** 시간표 슬롯 목록 */
    @GetMapping("/subjects/{csId}/slots")
    public List<ClassTimeslot> slots(@PathVariable("csId") Long classSubjectId){
        return svc.listSlots(classSubjectId);
    }

    /** 단건 슬롯 추가 */
    @PostMapping("/subjects/{csId}/slots")
    public ClassTimeslot addSlot(@PathVariable("csId") Long classSubjectId, @Validated @RequestBody ClassDtos.TimeslotReq r){
        if (!classSubjectId.equals(r.classSubjectId()))
            throw new IllegalArgumentException("classSubjectId mismatch");
        return svc.addSlot(r);
    }

    /** 슬롯 삭제 */
    @DeleteMapping("/slots/{slotId}")
    public void removeSlot(@PathVariable Long slotId){
        svc.removeSlot(slotId);
    }

    /** 담당/시간 일괄 저장(검증 포함) — 별도 서비스 */
    @PostMapping("/{id}/assignments/bulk")
    public void saveAssignmentsBulk(@PathVariable Long id, @Validated @RequestBody AssignDtos.SaveAssignmentsRequest req){
        assignSvc.saveBulk(id, req);
    }
}