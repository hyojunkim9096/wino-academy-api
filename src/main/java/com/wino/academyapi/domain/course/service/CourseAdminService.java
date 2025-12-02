package com.wino.academyapi.domain.course.service;

import com.wino.academyapi.domain.course.dto.CourseDtos;
import com.wino.academyapi.domain.course.entity.Course;
import com.wino.academyapi.domain.course.entity.CourseSubject;
import com.wino.academyapi.domain.course.entity.CourseTimeslot;
import com.wino.academyapi.domain.course.repository.CourseRepository;
import com.wino.academyapi.domain.course.repository.CourseSubjectRepository;
import com.wino.academyapi.domain.course.repository.CourseTimeslotRepository;
import com.wino.academyapi.domain.semester.entity.Semester;
import com.wino.academyapi.domain.semester.entity.SemesterType;
import com.wino.academyapi.domain.semester.repository.SemesterRepository;
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseAdminService {

    private final CourseRepository courseRepo;
    private final CourseSubjectRepository csRepo;
    private final CourseTimeslotRepository slotRepo;
    private final SemesterRepository semesterRepo;
    private final DbSessionVars dbSessionVars;

    private SemesterType getSemesterTypeOrDefault(Long semesterId){
        if (semesterId == null) return SemesterType.REGULAR;
        Semester s = semesterRepo.findById(semesterId).orElse(null);
        return (s == null || s.getSemesterType() == null) ? SemesterType.REGULAR : s.getSemesterType();
    }

    @Transactional(readOnly = true)
    public List<Course> list(String workLocationCode, String schoolStage){
        return courseRepo.findByWorkLocationCodeAndSchoolStageAndUseYnOrderBySortOrderAscNameAsc(
                workLocationCode, schoolStage, true
        );
    }

    @Transactional
    public Course upsert(Long id, CourseDtos.CourseUpsertReq r){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        Course e = (id==null)? new Course() : courseRepo.findById(id).orElseThrow();

        SemesterType semType = getSemesterTypeOrDefault(r.semesterId());
        String stage = r.schoolStage();
        if ("E".equalsIgnoreCase(stage) && semType == SemesterType.EXAM_PREP) {
            throw new IllegalStateException("초등부(E)에서는 시험대비(EXAM_PREP) 학기를 사용할 수 없습니다.");
        }

        if (r.gradeCode()!=null && !r.gradeCode().isBlank()) {
            char g0 = Character.toUpperCase(r.gradeCode().charAt(0));
            char s0 = Character.toUpperCase(stage.charAt(0));
            if (g0 != s0) {
                throw new IllegalArgumentException("학년 코드(" + r.gradeCode() + ")가 학부(" + stage + ")와 일치하지 않습니다.");
            }
        }

        e.setWorkLocationCode(r.workLocationCode());
        e.setSchoolStage(stage);
        e.setSemesterId(r.semesterId());
        e.setGradeCode(r.gradeCode());
        e.setClassCode(r.classCode());
        e.setCode(r.code());
        e.setName(r.name());

        if (r.sortOrder() != null) {
            e.setSortOrder(r.sortOrder());
        } else if (id == null) {
            var last = courseRepo.findTopByWorkLocationCodeAndSchoolStageOrderBySortOrderDesc(
                    r.workLocationCode(), stage
            );
            int next = last.map(cm -> Optional.ofNullable(cm.getSortOrder()).orElse(0)).orElse( -1 ) + 1;
            e.setSortOrder(next);
        }

        e.setHomeroomTeacherId(r.homeroomTeacherId());
        e.setCapacity(r.capacity());
        if (r.status()!=null) e.setStatus(r.status());
        e.setMemo(r.memo());
        if (r.useYn()!=null) e.setUseYn(r.useYn());

        return courseRepo.save(e);
    }

    @Transactional
    public void reorderCourses(String workLocationCode, String schoolStage, List<Long> idsInOrder){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        if (idsInOrder == null || idsInOrder.isEmpty()) return;

        LinkedHashSet<Long> unique = new LinkedHashSet<>(idsInOrder);
        if (unique.size() != idsInOrder.size()) {
            throw new IllegalArgumentException("중복된 ID가 포함되어 있습니다.");
        }

        List<Course> rows = courseRepo.findByWorkLocationCodeAndSchoolStageAndIdIn(
                workLocationCode, schoolStage, List.copyOf(unique)
        );
        if (rows.size() != unique.size()) {
            throw new IllegalArgumentException("요청한 ID 중 파티션에 속하지 않는 항목이 있습니다.");
        }

        Map<Long, Course> byId = new HashMap<>();
        for (Course cm : rows) byId.put(cm.getId(), cm);

        int i = 0;
        for (Long cid : unique) {
            Course cm = byId.get(cid);
            cm.setSortOrder(i++);
        }
        courseRepo.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<CourseDtos.CourseSubjectRes> listSubjectsWithName(Long courseId){
        var rows = csRepo.findRowsWithSubject(courseId);
        return rows.stream()
                .map(cs -> new CourseDtos.CourseSubjectRes(
                        cs.getId(), cs.getCourseId(), cs.getSubjectId(),
                        cs.getSubjectName(), cs.getTeacherId(),
                        cs.getSortOrder(), cs.getUseYn()
                ))
                .toList();
    }

    @Transactional
    public CourseSubject addSubject(Long courseId, Long subjectId, Long teacherId){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        Course cm = courseRepo.findById(courseId).orElseThrow();
        SemesterType type = getSemesterTypeOrDefault(cm.getSemesterId());

        int nextSort = csRepo.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .mapToInt(CourseSubject::getSortOrder).max().orElse(-1) + 1;

        Long teacherToUse = (type == SemesterType.EXAM_PREP) ? null : teacherId;

        return csRepo.save(CourseSubject.builder()
                .courseId(courseId)
                .subjectId(subjectId)
                .teacherId(teacherToUse)
                .sortOrder(nextSort)
                .build());
    }

    @Transactional
    public void removeSubject(Long courseId, Long courseSubjectId){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        CourseSubject e = csRepo.findById(courseSubjectId).orElseThrow();
        if (!e.getCourseId().equals(courseId)) throw new IllegalArgumentException("course mismatch");
        csRepo.delete(e);
    }

    @Transactional
    public void updateTeacher(Long courseSubjectId, Long teacherId){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        CourseSubject e = csRepo.findById(courseSubjectId).orElseThrow();
        Course cm = courseRepo.findById(e.getCourseId()).orElseThrow();
        SemesterType type = getSemesterTypeOrDefault(cm.getSemesterId());
        if (type == SemesterType.EXAM_PREP) {
            throw new IllegalStateException("시험대비(EXAM_PREP) 학기에서는 과목 담당 교사를 개별 지정할 수 없습니다.");
        }
        e.setTeacherId(teacherId);
        csRepo.save(e);
    }

    @Transactional
    public void reorderSubjects(Long courseId, List<Long> idsInOrder){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        List<CourseSubject> all = csRepo.findByCourseIdOrderBySortOrderAsc(courseId);
        Map<Long, CourseSubject> byId = new HashMap<>();
        for (var cs: all) byId.put(cs.getId(), cs);

        int i=0;
        for (Long csId : idsInOrder){
            CourseSubject cs = byId.get(csId);
            if (cs==null) throw new IllegalArgumentException("Invalid courseSubjectId: "+csId);
            cs.setSortOrder(i++);
        }
        csRepo.saveAll(all);
    }

    @Transactional(readOnly = true)
    public List<CourseTimeslot> listSlots(Long courseSubjectId){
        return slotRepo.findByCourseSubjectIdOrderByDayOfWeekAscStartTimeAsc(courseSubjectId);
    }

    @Transactional
    public CourseTimeslot addSlot(CourseDtos.TimeslotReq r){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        return slotRepo.save(CourseTimeslot.builder()
                .courseSubjectId(r.courseSubjectId())
                .dayOfWeek(r.dayOfWeek())
                .startTime(java.time.LocalTime.parse(r.startTime()))
                .endTime(java.time.LocalTime.parse(r.endTime()))
                .room(r.room())
                .roomId(r.roomId())
                .classTimeCode(r.startTimeCode())
                .classTimeLabel(r.startTimeName())
                .startDate(r.startDate()==null? null: java.time.LocalDate.parse(r.startDate()))
                .endDate(r.endDate()==null? null: java.time.LocalDate.parse(r.endDate()))
                .useYn(r.useYn()==null || r.useYn())
                .build());
    }

    @Transactional
    public void removeSlot(Long slotId){
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());
        slotRepo.deleteById(slotId);
    }
}