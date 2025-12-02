package com.wino.academyapi.domain.course.controller.admin;

import com.wino.academyapi.domain.course.dto.CourseDtos;
import com.wino.academyapi.domain.course.dto.AssignDtos;
import com.wino.academyapi.domain.course.entity.CourseTimeslot;
import com.wino.academyapi.domain.course.service.CourseAdminService;
import com.wino.academyapi.domain.course.service.CourseAssignService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/courses") // ✅ URL 변경: /classes -> /courses
@RequiredArgsConstructor
public class CourseAdminController {

    private final CourseAdminService svc;
    private final CourseAssignService assignSvc;

    /** 반 목록 */
    @GetMapping
    public List<CourseDtos.CourseRes> list(
            @RequestParam String workLocationCode,
            @RequestParam String schoolStage
    ){
        return svc.list(workLocationCode, schoolStage).stream().map(this::toRes).toList();
    }

    /** 반 생성 */
    @PostMapping
    public CourseDtos.CourseRes create(@Validated @RequestBody CourseDtos.CourseUpsertReq r){
        var e = svc.upsert(null, r);
        return toRes(e);
    }

    /** 반 수정 */
    @PutMapping("/{id}")
    public CourseDtos.CourseRes update(@PathVariable Long id, @Validated @RequestBody CourseDtos.CourseUpsertReq r){
        var e = svc.upsert(id, r);
        return toRes(e);
    }

    /** 반 정렬 */
    @PutMapping("/reorder")
    public void reorder(
            @RequestParam String workLocationCode,
            @RequestParam String schoolStage,
            @RequestBody CourseDtos.ReorderCourseReq r
    ){
        svc.reorderCourses(workLocationCode, schoolStage, r.courseIdsInOrder());
    }

    /** 반-과목 목록 */
    @GetMapping("/{id}/subjects")
    public List<CourseDtos.CourseSubjectRes> listSubjects(@PathVariable Long id){
        return svc.listSubjectsWithName(id);
    }

    /** 반에 과목 추가 */
    @PostMapping("/{id}/subjects")
    public CourseDtos.CourseSubjectRes addSubject(@PathVariable Long id, @Validated @RequestBody CourseDtos.AddCourseSubjectReq r){
        var cs = svc.addSubject(id, r.subjectId(), r.teacherId());
        return new CourseDtos.CourseSubjectRes(
                cs.getId(), cs.getCourseId(), cs.getSubjectId(),
                null,
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
    public void reorder(@PathVariable Long id, @RequestBody CourseDtos.ReorderReq r){
        svc.reorderSubjects(id, r.courseSubjectIdsInOrder());
    }

    /** 시간표 슬롯 목록 */
    @GetMapping("/subjects/{csId}/slots")
    public List<CourseTimeslot> slots(@PathVariable("csId") Long courseSubjectId){
        return svc.listSlots(courseSubjectId);
    }

    /** 단건 슬롯 추가 */
    @PostMapping("/subjects/{csId}/slots")
    public CourseTimeslot addSlot(@PathVariable("csId") Long courseSubjectId, @Validated @RequestBody CourseDtos.TimeslotReq r){
        if (!courseSubjectId.equals(r.courseSubjectId()))
            throw new IllegalArgumentException("courseSubjectId mismatch");
        return svc.addSlot(r);
    }

    /** 슬롯 삭제 */
    @DeleteMapping("/slots/{slotId}")
    public void removeSlot(@PathVariable Long slotId){
        svc.removeSlot(slotId);
    }

    /** 담당/시간 일괄 저장 */
    @PostMapping("/{id}/assignments/bulk")
    public void saveAssignmentsBulk(@PathVariable Long id, @Validated @RequestBody AssignDtos.SaveAssignmentsRequest req){
        assignSvc.saveBulk(id, req);
    }

    private CourseDtos.CourseRes toRes(com.wino.academyapi.domain.course.entity.Course e) {
        return new CourseDtos.CourseRes(
                e.getId(), e.getWorkLocationCode(), e.getSchoolStage(), e.getSemesterId(),
                e.getGradeCode(), e.getClassCode(),
                e.getCode(), e.getName(), e.getSortOrder(),
                e.getHomeroomTeacherId(), e.getCapacity(), e.getStatus(), e.getMemo(), e.isUseYn()
        );
    }
}