package com.wino.academyapi.domain.classs.service;

import com.wino.academyapi.domain.classs.dto.AssignDtos;
import com.wino.academyapi.domain.classs.entity.ClassSubject;
import com.wino.academyapi.domain.classs.entity.ClassTimeslot;
import com.wino.academyapi.domain.classs.repository.ClassMasterRepository;
import com.wino.academyapi.domain.classs.repository.ClassSubjectRepository;
import com.wino.academyapi.domain.classs.repository.ClassTimeslotRepository;

// ✅ 추가: 요청 컨텍스트(ThreadLocal) + 같은 트랜잭션/커넥션에 세션 변수 주입
import com.wino.academyapi.global.audit.AppUserContext;
import com.wino.academyapi.infra.db.DbSessionVars;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 담당/시간 일괄 저장 서비스 (Bulk)
 * -----------------------------------------------------------------------------
 * A) clearSlot=true
 *    → 해당 과목의 모든 슬롯 삭제(1개 보장 정책)
 *
 * B) dayOfWeek + startTime(+room[+roomId], +startTimeCode, +startTimeName)
 *    → 슬롯 업서트(종료 = 시작 + 50분)
 *    - class_time_code/label 스냅샷 저장 (프론트 startTimeCode/Name을 그대로 사용)
 *    - 없으면 "HH:mm"을 기본 code/name으로 사용
 *
 * C) teacherId / clearTeacher=true
 *    → 담당 교사 변경 또는 초기화
 *
 * 검증 정책:
 *  1) 반 내부(요청 업서트끼리) 시간 겹침 차단
 *  2) 요청 내부(같은 선생님+같은 요일) 시간대 겹침 차단(업서트/교사만변경 모두 포함)
 *  3) DB 기준 같은 선생님+같은 요일 시간대 겹침 차단(타 반 포함) → 상세 메시지
 *
 * 저장 순서: teacher patch → slot 반영(삭제/업서트)
 *
 * ⚠️ 매우 중요: 메서드 진입 직후, 같은 트랜잭션/같은 커넥션에서
 *    DbSessionVars#setAppVars(AppUserContext.getUserId(), AppUserContext.getNote()) 를 호출해야
 *    DB 트리거/히스토리(event_by, event_note)에 정확히 기록됩니다.
 * -----------------------------------------------------------------------------
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassAssignService {

    private final ClassMasterRepository classRepo;          // (현재 로직에서는 직접 사용하진 않지만 남겨둠)
    private final ClassSubjectRepository csRepo;
    private final ClassTimeslotRepository slotRepo;

    // ✅ 같은 트랜잭션/같은 커넥션에서 MySQL 세션 변수(@app_user_id, @event_note) 주입용
    private final DbSessionVars dbSessionVars;

    // ────────────────────────────────────────────────────────────────
    // 유틸
    // ────────────────────────────────────────────────────────────────

    /** "1630" 또는 "16:30" 모두 허용 (※ "9:30" 형태는 허용하지 않음; 프런트에서 HH:mm으로 정규화됨을 전제) */
    private static LocalTime parseHHmm(String s){
        if (s == null || s.isBlank()) return null;
        return LocalTime.parse(s.length()==4 ? s.substring(0,2)+":"+s.substring(2) : s);
    }

    /** 종료 = 시작 + 50분 */
    private static LocalTime plus50(LocalTime start){ return start.plusMinutes(50); }

    /** 시간대 겹침(동시간대 포함) : s1 < e2 && s2 < e1 */
    private static boolean overlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2){
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    private static String dayLabel(int d){
        return switch (d) {
            case 1 -> "월"; case 2 -> "화"; case 3 -> "수"; case 4 -> "목";
            case 5 -> "금"; case 6 -> "토"; case 7 -> "일"; default -> String.valueOf(d);
        };
    }

    private static IllegalArgumentException bad(String m){ return new IllegalArgumentException(m); }
    private static IllegalStateException conflict(String m){ return new IllegalStateException(m); }
    private static boolean hasText(String s){ return s != null && !s.isBlank(); }
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // ────────────────────────────────────────────────────────────────
    // 본문
    // ────────────────────────────────────────────────────────────────

    /**
     * 담당/시간 일괄 저장
     * - 컨트롤러: POST /api/admin/classes/{id}/assignments/bulk
     * - 요청: AssignDtos.SaveAssignmentsRequest
     */
    @Transactional
    public void saveBulk(Long classId, AssignDtos.SaveAssignmentsRequest req){

        // ✅ 같은 트랜잭션/같은 커넥션에 MySQL 세션 변수(@app_user_id, @event_note) 주입
        dbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote());

        // (0) 유효성: items 준비
        final List<AssignDtos.SaveAssignmentsRequest.Item> items =
                Optional.ofNullable(req.getItems()).orElse(Collections.emptyList());
        if (items.isEmpty()) return;

        // (0-0) 소속 검증: 모든 classSubjectId가 path의 {classId}에 속해야 함
        Set<Long> allowed = csRepo.findByClassIdOrderBySortOrderAsc(classId).stream()
                .map(ClassSubject::getId).collect(Collectors.toSet());

        Set<Long> csIds = new HashSet<>();
        for (AssignDtos.SaveAssignmentsRequest.Item it : items){
            if (it.getClassSubjectId()==null) throw bad("classSubjectId는 필수입니다.");
            if (!allowed.contains(it.getClassSubjectId()))
                throw bad("요청에 현재 반에 속하지 않는 과목이 포함되어 있습니다. csId="+it.getClassSubjectId());
            csIds.add(it.getClassSubjectId());
        }

        // (0-1) 대상 cs 엔티티/브리프 로딩
        Map<Long, ClassSubject> csMap = csRepo.findAllById(csIds).stream()
                .collect(Collectors.toMap(ClassSubject::getId, x->x));

        // 현재 항목(좌측)에 표시할 과목/반/지점 정보 미리 로딩 (DB 충돌 메시지에 사용)
        Map<Long, ClassSubjectRepository.CsBrief> briefByCsId = csRepo.findBriefsByIdIn(csIds).stream()
                .collect(Collectors.toMap(ClassSubjectRepository.CsBrief::getId, x -> x));

        // (0-2) 명령 전개: 한 item을 '명령'으로 변환 (검증 전 중간 모델)
        class Cmd {
            ClassSubject cs;          // 대상 class_subject
            boolean clearSlot;        // 슬롯 삭제?
            boolean teacherOnly;      // 시간 변경 없이 교사만 패치?
            boolean upsertSlot;       // 슬롯 업서트?
            Long teacherIdPatch;      // teacher 지정값(있으면 변경)
            boolean clearTeacher;     // true면 teacher null로 초기화
            Integer day; LocalTime s; LocalTime e; // 시간 정보(시작/종료)
            String room; Long roomId;              // ✅ NEW: roomId 포함
            String timeCode; String timeName;      // 공통코드 스냅샷 (class_time_code/label 로 저장)
        }
        List<Cmd> cmds = new ArrayList<>();

        for (AssignDtos.SaveAssignmentsRequest.Item it : items){
            ClassSubject cs = csMap.get(it.getClassSubjectId());
            if (cs==null) throw bad("class_subject가 존재하지 않습니다. id="+it.getClassSubjectId());

            boolean clearSlot = Boolean.TRUE.equals(it.getClearSlot());
            boolean teacherPatch = (it.getTeacherId()!=null) || Boolean.TRUE.equals(it.getClearTeacher());

            // 업서트 판단: 둘 중 하나라도 들어오면 업서트 후보
            boolean upsert = (it.getDayOfWeek()!=null) || (hasText(it.getStartTime()));

            Integer day = null; LocalTime s = null; LocalTime e = null; String room = null; Long roomId = null;
            String timeCode = null; String timeName = null;

            if (upsert){
                // 업서트는 dayOfWeek + startTime 둘 다 필수
                if (it.getDayOfWeek()==null || !hasText(it.getStartTime()))
                    throw bad("업서트 항목은 dayOfWeek와 startTime이 모두 필요합니다. csId="+cs.getId());

                day = it.getDayOfWeek();
                s   = parseHHmm(it.getStartTime());
                if (s==null) throw bad("startTime 형식이 올바르지 않습니다(HH:mm 또는 HHmm). csId="+cs.getId());
                e   = plus50(s);
                room= Optional.ofNullable(it.getRoom()).orElse("");
                roomId = it.getRoomId();                         // ✅ NEW

                // 스냅샷(프론트에서 온 코드/라벨 우선, 없으면 "HH:mm"으로 채움)
                String hhmm = s.format(HHMM);
                timeCode = hasText(it.getStartTimeCode()) ? it.getStartTimeCode() : hhmm;
                timeName = hasText(it.getStartTimeName()) ? it.getStartTimeName() : hhmm;
            }

            // clearSlot이 true면 업서트보다 삭제 우선
            if (clearSlot) { upsert = false; day=null; s=null; e=null; room=null; roomId=null; timeCode=null; timeName=null; }

            boolean teacherOnly = !clearSlot && !upsert && teacherPatch; // 오직 teacher만 바꾸는 케이스
            if (!clearSlot && !upsert && !teacherOnly) {
                // 무동작 항목은 스킵
                continue;
            }

            Cmd c = new Cmd();
            c.cs = cs;
            c.clearSlot = clearSlot;
            c.teacherOnly = teacherOnly;
            c.upsertSlot = upsert;
            c.teacherIdPatch = it.getTeacherId();
            c.clearTeacher = Boolean.TRUE.equals(it.getClearTeacher());
            c.day = day; c.s = s; c.e = e; c.room = room; c.roomId = roomId;
            c.timeCode = timeCode; c.timeName = timeName;
            cmds.add(c);
        }

        // (1) 반 내부 시간 겹침(요청 업서트끼리만 검사)
        class Tmp { int day; LocalTime s; LocalTime e; Tmp(int d, LocalTime s, LocalTime e){this.day=d;this.s=s;this.e=e;} }
        List<Tmp> reqSlots = cmds.stream().filter(c -> c.upsertSlot)
                .map(c -> new Tmp(c.day, c.s, c.e))
                .sorted(Comparator.comparingInt((Tmp a)->a.day).thenComparing(a->a.s))
                .collect(Collectors.toList());

        for (int i=0;i<reqSlots.size()-1;i++){
            Tmp A = reqSlots.get(i);
            Tmp B = reqSlots.get(i+1);
            if (A.day==B.day && overlap(A.s,A.e,B.s,B.e)){
                throw conflict("이미 해당 시간에 수업이 존재합니다. (반 내부 겹침)\n"
                        + dayLabel(A.day)+" "+A.s+"~"+A.e+" ↔ "+B.s+"~"+B.e);
            }
        }

        // (2) 요청 내부 교차수업(같은 선생님+같은 요일+시간 겹침)
        class U { Long csId; Long t; Integer d; LocalTime s; LocalTime e;
            U(Long csId, Long t, Integer d, LocalTime s, LocalTime e){this.csId=csId;this.t=t;this.d=d;this.s=s;this.e=e;} }
        List<U> intra = new ArrayList<>();
        for (Cmd c : cmds){
            // '적용될 담당자' 기준 (clearTeacher면 제외)
            Long effT = c.clearTeacher ? null : (c.teacherIdPatch!=null ? c.teacherIdPatch : c.cs.getTeacherId());
            if (effT==null) continue;

            if (c.upsertSlot){
                intra.add(new U(c.cs.getId(), effT, c.day, c.s, c.e));
            } else if (c.teacherOnly){
                // 교사만 변경인데 슬롯은 기존 1개 보장 정책 → DB에서 현 슬롯(첫 1개) 사용
                List<ClassTimeslot> slots = slotRepo.findByClassSubjectIdOrderByDayOfWeekAscStartTimeAsc(c.cs.getId());
                ClassTimeslot p = slots.isEmpty()? null : slots.get(0);
                if (p!=null) intra.add(new U(c.cs.getId(), effT, p.getDayOfWeek(), p.getStartTime(), p.getEndTime()));
            }
        }
        Map<String, List<U>> byTDay = new HashMap<>();
        for (U u : intra){
            String key = u.t + "|" + u.d;
            byTDay.computeIfAbsent(key, k->new ArrayList<>()).add(u);
        }
        for (Map.Entry<String, List<U>> entry : byTDay.entrySet()){
            List<U> list = entry.getValue().stream().sorted(Comparator.comparing(u->u.s)).collect(Collectors.toList());
            for (int i=0;i<list.size()-1;i++){
                U A = list.get(i);
                U B = list.get(i+1);
                if (overlap(A.s,A.e,B.s,B.e)){
                    throw conflict("해당 시간의 담당 선생님이 같은 날 동시간대 수업이 있어 저장할 수 없습니다.\n"
                            + dayLabel(A.d)+" "+A.s+"~"+A.e+" ↔ "+B.s+"~"+B.e);
                }
            }
        }

        // (3) DB와의 충돌(업서트/교사만변경) — 삭제는 제외
        for (Cmd c : cmds){
            if (c.clearSlot) continue;

            Long effT = c.clearTeacher ? null : (c.teacherIdPatch!=null ? c.teacherIdPatch : c.cs.getTeacherId());
            if (effT==null) continue;

            Integer day; LocalTime s; LocalTime e;
            if (c.upsertSlot){
                day = c.day; s = c.s; e = c.e;
            } else {
                List<ClassTimeslot> slots = slotRepo.findByClassSubjectIdOrderByDayOfWeekAscStartTimeAsc(c.cs.getId());
                ClassTimeslot p = slots.isEmpty()? null : slots.get(0);
                if (p==null) continue; // 시간 없으면 DB 충돌 자체가 불가
                day = p.getDayOfWeek(); s = p.getStartTime(); e = p.getEndTime();
            }

            List<ClassTimeslotRepository.BusyProjection> hits =
                    slotRepo.findConflictsForTeacher(effT, day, Time.valueOf(s), Time.valueOf(e), c.cs.getId());

            log.debug("[overlap-db] effTeacher={} day={} {}~{} excludeCs={} hits={}",
                    effT, day, s, e, c.cs.getId(), (hits==null?0:hits.size()));

            if (hits!=null && !hits.isEmpty()){
                // 상세 메시지 구성
                ClassSubjectRepository.CsBrief cur = briefByCsId.getOrDefault(
                        c.cs.getId(),
                        new ClassSubjectRepository.CsBrief() {
                            public Long getId(){ return c.cs.getId(); }
                            public Long getClassId(){ return c.cs.getClassId(); }
                            public String getClassName(){ return "Class#"+c.cs.getClassId(); }
                            public String getWorkLocationCode(){ return "-"; }
                            public Long getSubjectId(){ return c.cs.getSubjectId(); }
                            public String getSubjectName(){ return "과목#"+c.cs.getSubjectId(); }
                        }
                );

                String curSubj = Optional.ofNullable(cur.getSubjectName()).orElse("과목#"+cur.getSubjectId());
                String curClass= Optional.ofNullable(cur.getClassName()).orElse("Class#"+cur.getClassId());

                StringBuilder sb = new StringBuilder();
                sb.append("같은 선생님이 같은 요일에 동시간대 수업이 이미 존재합니다.\n")
                        .append("아래 항목을 확인하고 시간을 조정해 주세요.\n\n");

                for (ClassTimeslotRepository.BusyProjection b : hits){
                    String dbSubj = Optional.ofNullable(b.getSubjectName()).orElse("과목#"+b.getSubjectId());
                    String dbClass= Optional.ofNullable(b.getClassName()).orElse("Class#"+b.getClassId());
                    String dbLoc  = Optional.ofNullable(b.getWorkLocationCode()).orElse("-");
                    String bs     = b.getStartTime().toLocalTime().format(HHMM);
                    String be     = b.getEndTime().toLocalTime().format(HHMM);

                    sb.append("- ").append(dayLabel(day)).append(' ')
                            .append('[').append(curSubj).append("] '").append(curClass).append("' ")
                            .append(s.format(HHMM)).append('~').append(e.format(HHMM))
                            .append(" ↔ ")
                            .append('[').append(dbSubj).append("] '").append(dbClass).append("'(").append(dbLoc).append(") ")
                            .append(bs).append('~').append(be)
                            .append('\n');
                }
                throw conflict(sb.toString().trim());
            }
        }

        // (4) 저장(teacher → slot) : 모든 검증 통과 이후 반영
        for (Cmd c : cmds){
            // 4-1) 담당 교사 패치
            if (c.clearTeacher){
                c.cs.setTeacherId(null);
                csRepo.save(c.cs);
            } else if (c.teacherIdPatch != null){
                c.cs.setTeacherId(c.teacherIdPatch);
                csRepo.save(c.cs);
            }

            // 4-2) 슬롯 반영
            if (c.clearSlot){
                // 1개 보장 정책: 전체 삭제
                slotRepo.deleteByClassSubjectId(c.cs.getId());
                log.debug("[slot] deleted: csId={}", c.cs.getId());
            } else if (c.upsertSlot){
                // 1개 보장: 기존 전량 삭제 후 신규 1건 생성
                slotRepo.deleteByClassSubjectId(c.cs.getId());

                ClassTimeslot slot = ClassTimeslot.builder()
                        .classSubjectId(c.cs.getId())
                        .dayOfWeek(c.day)
                        .startTime(c.s)
                        .endTime(c.e)
                        .classTimeCode(c.timeCode)
                        .classTimeLabel(c.timeName)
                        .room(Optional.ofNullable(c.room).orElse(""))
                        .roomId(c.roomId)                         // ✅ NEW
                        .useYn(true)
                        .build();
                slotRepo.save(slot);

                log.debug("[slot] upserted: csId={} day={} {}~{} (code={}, name={}, roomId={})",
                        c.cs.getId(), c.day, c.s, c.e, c.timeCode, c.timeName, c.roomId);
            }
        }
    }
}