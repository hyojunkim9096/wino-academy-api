// src/main/java/com/wino/academyapi/domain/classs/service/ClassAdminService.java
package com.wino.academyapi.domain.classs.service;

import com.wino.academyapi.domain.classs.dto.ClassDtos;
import com.wino.academyapi.domain.classs.entity.ClassMaster;
import com.wino.academyapi.domain.classs.entity.ClassSubject;
import com.wino.academyapi.domain.classs.entity.ClassTimeslot;
import com.wino.academyapi.domain.classs.repository.ClassMasterRepository;
import com.wino.academyapi.domain.classs.repository.ClassSubjectRepository;
import com.wino.academyapi.domain.classs.repository.ClassTimeslotRepository;
import com.wino.academyapi.domain.semester.entity.Semester;
import com.wino.academyapi.domain.semester.entity.SemesterType;
import com.wino.academyapi.domain.semester.repository.SemesterRepository;

// ✅ 요청 스레드 컨텍스트(헤더에서 캡처) + 같은 트랜잭션/커넥션으로 DB 세션 변수 주입
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 반/과목/시간표 관리 서비스
 * -----------------------------------------------------------------------------
 * - 목록 정렬: sort_order ASC, name ASC
 * - upsert: 신규 생성 시 sortOrder 미지정이면 (지점×학부) 파티션의 마지막 다음 값으로 자동 부여
 * - 반 일괄 재정렬: 같은 (지점×학부) 파티션 내에서만 허용
 * - ✅ NEW: gradeCode/classCode 저장 및 간단 검증(학년코드 접두 vs 학부)
 *
 * ⚠️ 이벤트 기록 정책
 *   모든 "쓰기" 트랜잭션 진입 시
 *   DbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote())
 *   를 호출해 동일 커넥션의 MySQL 세션 변수(@app_user_id, @event_note)를 설정한다.
 *   그러면 DB 트리거가 class_*_hist 테이블에 event_by / event_note 를 기록한다.
 * -----------------------------------------------------------------------------
 */
@Service
@RequiredArgsConstructor
public class ClassAdminService {

    private final ClassMasterRepository classRepo;
    private final ClassSubjectRepository csRepo;
    private final ClassTimeslotRepository slotRepo;
    private final SemesterRepository semesterRepo;

    // ✅ 같은 트랜잭션/같은 커넥션에서 세션 변수 주입용
    private final DbSessionVars dbSessionVars;

    private SemesterType getSemesterTypeOrDefault(Long semesterId){
        if (semesterId == null) return SemesterType.REGULAR;
        Semester s = semesterRepo.findById(semesterId).orElse(null);
        return (s == null || s.getSemesterType() == null) ? SemesterType.REGULAR : s.getSemesterType();
    }

    /** 반 목록 조회 (사용중만) — 정렬: sort_order ASC, name ASC */
    @Transactional(readOnly = true)
    public List<ClassMaster> list(String workLocationCode, String schoolStage){
        return classRepo.findByWorkLocationCodeAndSchoolStageAndUseYnOrderBySortOrderAscNameAsc(
                workLocationCode, schoolStage, true
        );
    }

    /** 반 생성/수정
     *  - EXAM_PREP 학기: 초등(E) 금지
     *  - sortOrder:
     *      * r.sortOrder != null 이면 그대로 적용
     *      * 신규 & null이면 파티션 최대값+1 자동 배정
     *  - ✅ gradeCode: 학부 접두(E/M/H)와 1글자라도 불일치 시 예외
     */
    @Transactional
    public ClassMaster upsert(Long id, ClassDtos.ClassUpsertReq r){
        // ✅ 트랜잭션/커넥션에 @app_user_id, @event_note 주입(트리거 event_by/note 기록)
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        ClassMaster e = (id==null)? new ClassMaster() : classRepo.findById(id).orElseThrow();

        SemesterType semType = getSemesterTypeOrDefault(r.semesterId());
        String stage = r.schoolStage();
        if ("E".equalsIgnoreCase(stage) && semType == SemesterType.EXAM_PREP) {
            throw new IllegalStateException("초등부(E)에서는 시험대비(EXAM_PREP) 학기를 사용할 수 없습니다.");
        }

        // ✅ 간단 검증: gradeCode 접두(E/M/H) ↔ 학부(stage) 첫 글자 일치 여부
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

        // ✅ NEW: 공통코드 저장
        e.setGradeCode(r.gradeCode());
        e.setClassCode(r.classCode());

        e.setCode(r.code());
        e.setName(r.name());

        // 정렬 순서 처리
        if (r.sortOrder() != null) {
            e.setSortOrder(r.sortOrder());
        } else if (id == null) {
            // 신규인데 sortOrder 미제공 → 파티션 최대값+1
            var last = classRepo.findTopByWorkLocationCodeAndSchoolStageOrderBySortOrderDesc(
                    r.workLocationCode(), stage
            );
            int next = last.map(cm -> Optional.ofNullable(cm.getSortOrder()).orElse(0)).orElse( -1 ) + 1;
            e.setSortOrder(next);
        }
        // 수정인데 null이면 기존 값 유지

        e.setHomeroomTeacherId(r.homeroomTeacherId());
        e.setCapacity(r.capacity());
        if (r.status()!=null) e.setStatus(r.status());
        e.setMemo(r.memo());
        if (r.useYn()!=null) e.setUseYn(r.useYn());

        return classRepo.save(e);
    }

    /** 반 일괄 재정렬 */
    @Transactional
    public void reorderClasses(String workLocationCode, String schoolStage, List<Long> idsInOrder){
        // ✅ 히스토리 기록용 세션 변수 주입
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        if (idsInOrder == null || idsInOrder.isEmpty()) return;

        // 중복 체크
        LinkedHashSet<Long> unique = new LinkedHashSet<>(idsInOrder);
        if (unique.size() != idsInOrder.size()) {
            throw new IllegalArgumentException("중복된 classId가 포함되어 있습니다.");
        }

        // 파티션 내 해당 ID들만 조회
        List<ClassMaster> rows = classRepo.findByWorkLocationCodeAndSchoolStageAndIdIn(
                workLocationCode, schoolStage, List.copyOf(unique)
        );
        if (rows.size() != unique.size()) {
            throw new IllegalArgumentException("요청한 ID 중 파티션에 속하지 않는 항목이 있습니다.");
        }

        // id -> entity 매핑
        Map<Long, ClassMaster> byId = new HashMap<>();
        for (ClassMaster cm : rows) byId.put(cm.getId(), cm);

        // 순서 부여
        int i = 0;
        for (Long cid : unique) {
            ClassMaster cm = byId.get(cid);
            cm.setSortOrder(i++);
        }
        classRepo.saveAll(rows);
    }

    // ======= 과목/시간표 기존 로직 =======

    /** 과목명 포함 응답 */
    @Transactional(readOnly = true)
    public List<ClassDtos.ClassSubjectRes> listSubjectsWithName(Long classId){
        var rows = csRepo.findRowsWithSubject(classId);
        return rows.stream()
                .map(cs -> new ClassDtos.ClassSubjectRes(
                        cs.getId(), cs.getClassId(), cs.getSubjectId(),
                        cs.getSubjectName(), cs.getTeacherId(),
                        cs.getSortOrder(), cs.getUseYn()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClassSubject> listSubjects(Long classId){
        return csRepo.findByClassIdOrderBySortOrderAsc(classId);
    }

    /** 반에 과목 추가 */
    @Transactional
    public ClassSubject addSubject(Long classId, Long subjectId, Long teacherId){
        // ✅ 세션 변수 주입(과목 히스토리에도 by/note 남길 때)
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        ClassMaster cm = classRepo.findById(classId).orElseThrow();
        SemesterType type = getSemesterTypeOrDefault(cm.getSemesterId());

        int nextSort = csRepo.findByClassIdOrderBySortOrderAsc(classId).stream()
                .mapToInt(ClassSubject::getSortOrder).max().orElse(-1) + 1;

        Long teacherToUse = (type == SemesterType.EXAM_PREP) ? null : teacherId;

        return csRepo.save(ClassSubject.builder()
                .classId(classId)
                .subjectId(subjectId)
                .teacherId(teacherToUse)
                .sortOrder(nextSort)
                .build());
    }

    /** 반-과목 삭제 */
    @Transactional
    public void removeSubject(Long classId, Long classSubjectId){
        // ✅ 세션 변수 주입
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        ClassSubject e = csRepo.findById(classSubjectId).orElseThrow();
        if (!e.getClassId().equals(classId)) throw new IllegalArgumentException("class mismatch");
        csRepo.delete(e);
    }

    /** 과목 담당 교사 지정 */
    @Transactional
    public void updateTeacher(Long classSubjectId, Long teacherId){
        // ✅ 세션 변수 주입
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        ClassSubject e = csRepo.findById(classSubjectId).orElseThrow();
        ClassMaster cm = classRepo.findById(e.getClassId()).orElseThrow();
        SemesterType type = getSemesterTypeOrDefault(cm.getSemesterId());
        if (type == SemesterType.EXAM_PREP) {
            throw new IllegalStateException("시험대비(EXAM_PREP) 학기에서는 과목 담당 교사를 개별 지정할 수 없습니다.");
        }
        e.setTeacherId(teacherId);
        csRepo.save(e);
    }

    /** 반-과목 정렬 변경 */
    @Transactional
    public void reorder(Long classId, List<Long> idsInOrder){
        // ✅ 세션 변수 주입
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        List<ClassSubject> all = csRepo.findByClassIdOrderBySortOrderAsc(classId);
        Map<Long, ClassSubject> byId = new HashMap<>();
        for (var cs: all) byId.put(cs.getId(), cs);

        int i=0;
        for (Long csId : idsInOrder){
            ClassSubject cs = byId.get(csId);
            if (cs==null) throw new IllegalArgumentException("Invalid classSubjectId: "+csId);
            cs.setSortOrder(i++);
        }
        csRepo.saveAll(all);
    }

    /** 시간표 슬롯 목록 */
    @Transactional(readOnly = true)
    public List<ClassTimeslot> listSlots(Long classSubjectId){
        return slotRepo.findByClassSubjectIdOrderByDayOfWeekAscStartTimeAsc(classSubjectId);
    }

    /**
     * 단건 슬롯 추가(관리자 화면에서 직접 추가하는 엔드포인트용)
     * - Dto에 추가된 startTimeCode/startTimeName 도 함께 저장
     * - roomId(FK) 저장 (DB: class_timeslot.room_id)
     */
    @Transactional
    public ClassTimeslot addSlot(ClassDtos.TimeslotReq r){
        // ✅ 세션 변수 주입(시간표 히스토리에도 by/note 남김)
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        return slotRepo.save(ClassTimeslot.builder()
                .classSubjectId(r.classSubjectId())
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

    /** 슬롯 삭제 */
    @Transactional
    public void removeSlot(Long slotId){
        // ✅ 세션 변수 주입
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        slotRepo.deleteById(slotId);
    }
}