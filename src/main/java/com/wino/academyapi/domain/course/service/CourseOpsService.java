package com.wino.academyapi.domain.course.service;

import com.wino.academyapi.domain.course.dto.CourseOpsDtos; // ✅ DTO 이름 변경 반영 (또는 ClassOpsDtos)
import com.wino.academyapi.infra.db.DbSessionVars;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 반(Course) 운영 작업 서비스 (스냅샷/마감/복원/미리보기)
 * -----------------------------------------------------------------------------
 * - 중요 이슈: class_master_hist 테이블의 code_full 컬럼이 NOT NULL인데
 * 기존 INSERT가 값을 공급하지 않아 MySQL 1364 에러가 발생했음.
 * => 본 파일에서는 모든 "cm 히스토리 SNAP" INSERT에 code_full 값을 추가한다.
 *
 * - 스냅샷 시(cm/cs/ct):
 * * class_master_hist  : grade_code / class_code / **code_full(계산식)** 포함
 * * class_subject_hist : (기존과 동일) subject_name_snapshot 포함
 * * class_timeslot_hist: class_time_code/label, room_id 포함
 *
 * - 복원 시:
 * * 복원 직전 pre-SNAPSHOT(옵션 true)에서도 위와 동일하게 cm에 code_full 공급
 * * 복원 데이터 임시 테이블을 경유하여 1442 문제 회피
 *
 * - 모든 "쓰기" 트랜잭션 시작 시 dbVars.setAppVars(userId, note) 호출로
 * 같은 커넥션의 MySQL 세션 변수(@app_user_id, @event_note)를 설정하여
 * 트리거/히스토리 event_by / event_note가 올바르게 기록되도록 한다.
 * -----------------------------------------------------------------------------
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOpsService { // ✅ 클래스명 변경: ClassOpsService -> CourseOpsService

    private final NamedParameterJdbcTemplate jdbc;
    private final DbSessionVars dbVars;

    // 드라이버별 Timestamp 변환 보조
    private static Timestamp asTimestamp(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts)       return ts;
        if (v instanceof LocalDateTime ldt)  return Timestamp.valueOf(ldt);
        if (v instanceof OffsetDateTime odt) return Timestamp.from(odt.toInstant());
        if (v instanceof java.util.Date d)   return new Timestamp(d.getTime());
        throw new IllegalArgumentException("Unsupported time type: " + v.getClass());
    }

    // ────────────────────────────────────────────────────────────────
    // PREVIEW
    // ────────────────────────────────────────────────────────────────
    public CourseOpsDtos.PreviewRes preview(String work, String stage, Long semId){ // ✅ DTO 변경
        var p = params(work, stage, semId);

        long classCnt = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM class_master cm
             WHERE cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND cm.semester_id       = :semId
        """, p, Long.class);

        long subjectCnt = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM class_subject cs
              JOIN class_master cm ON cm.id = cs.class_id
             WHERE cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND cm.semester_id       = :semId
        """, p, Long.class);

        long slotCnt = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM class_timeslot ct
              JOIN class_subject  cs ON cs.id = ct.class_subject_id
              JOIN class_master   cm ON cm.id = cs.class_id
             WHERE cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND cm.semester_id       = :semId
        """, p, Long.class);

        Object lastSnapObj = jdbc.queryForObject("""
            SELECT MAX(h.event_at)
              FROM class_master_hist h
              JOIN class_master cm ON cm.id = h.ref_id
             WHERE cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND h.event_type         = 'SNAP'
               AND h.semester_id        = :semId
        """, p, Object.class);
        Timestamp lastSnap = asTimestamp(lastSnapObj);

        String lastSnapshotAt = (lastSnap == null) ? null : lastSnap.toString().substring(0, 19);
        return new CourseOpsDtos.PreviewRes(classCnt, subjectCnt, slotCnt, lastSnapshotAt);
    }

    // ────────────────────────────────────────────────────────────────
    // CLOSE (SNAP + 초기화)
    // ────────────────────────────────────────────────────────────────
    @Transactional
    public CourseOpsDtos.SimpleAck snapshotAndClose(Long appUserId, CourseOpsDtos.CloseReq req){
        var p = params(req.getWorkLocationCode(), req.getSchoolStage(), req.getSemesterId());
        String note = (req.getNote() == null || req.getNote().isBlank()) ? "학기 마감(ops)" : req.getNote().trim();
        p.addValue("note", note);

        // DB 세션 변수 주입(@app_user_id, @event_note) → 트리거에서 event_by/note 기록
        dbVars.setAppVars(appUserId, note);

        Timestamp snapAt = new Timestamp(System.currentTimeMillis());
        p.addValue("snapAt", snapAt);

        long beforeClasses = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM class_master cm
             WHERE cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND cm.semester_id       = :semId
        """, p, Long.class);

        // ⚠️ FIXED: class_master_hist SNAP 시 code_full 컬럼에 값 공급(계산식)
        int cmSnap = jdbc.update("""
            INSERT INTO class_master_hist
            (ref_id, version, event_type, event_at, event_by, event_note,
             work_location_code, school_stage, grade_code, class_code, code_full,
             semester_id, code, name, sort_order, homeroom_teacher_id,
             capacity, status, memo, use_yn, created_at, updated_at)
            SELECT
                cm.id,
                COALESCE((SELECT MAX(h.version) FROM class_master_hist h WHERE h.ref_id = cm.id), 0) + 1,
                'SNAP', :snapAt, @app_user_id, :note,
                cm.work_location_code, cm.school_stage, cm.grade_code, cm.class_code,
                /* code_full 계산식: grade/class 둘 다 없으면 code만, 있으면 "grade-class-code" */
                CASE
                  WHEN (cm.grade_code IS NULL OR cm.grade_code = '')
                   AND (cm.class_code IS NULL OR cm.class_code = '')
                    THEN cm.code
                  ELSE CONCAT_WS('-', cm.grade_code, cm.class_code, cm.code)
                END AS code_full,
                cm.semester_id, cm.code, cm.name, cm.sort_order, cm.homeroom_teacher_id,
                cm.capacity, cm.status, cm.memo, cm.use_yn, cm.created_at, cm.updated_at
            FROM class_master cm
            WHERE cm.work_location_code = :work
              AND cm.school_stage      = :stage
              AND cm.semester_id       = :semId
        """, p);

        // class_subject SNAP (변경 없음)
        int csSnap = jdbc.update("""
            INSERT INTO class_subject_hist
            (ref_id, version, event_type, event_at, event_by, event_note,
             class_id, subject_id, teacher_id, sort_order, use_yn, created_at, updated_at,
             subject_name_snapshot, teacher_name_snapshot)
            SELECT
                cs.id,
                COALESCE((SELECT MAX(h.version) FROM class_subject_hist h WHERE h.ref_id = cs.id), 0) + 1,
                'SNAP', :snapAt, @app_user_id, :note,
                cs.class_id, cs.subject_id, cs.teacher_id, cs.sort_order, cs.use_yn, cs.created_at, cs.updated_at,
                s.name, NULL
            FROM class_subject cs
            JOIN class_master cm ON cm.id = cs.class_id
            LEFT JOIN subject s  ON s.id = cs.subject_id
            WHERE cm.work_location_code = :work
              AND cm.school_stage      = :stage
              AND cm.semester_id       = :semId
        """, p);

        // class_timeslot SNAP (code/label/room_id 포함)
        int ctSnap = jdbc.update("""
            INSERT INTO class_timeslot_hist
            (ref_id, version, event_type, event_at, event_by, event_note,
             class_subject_id, day_of_week, start_time, end_time, class_time_code, class_time_label,
             room, room_id, start_date, end_date, use_yn, created_at, updated_at)
            SELECT
                ct.id,
                COALESCE((SELECT MAX(h.version) FROM class_timeslot_hist h WHERE h.ref_id = ct.id), 0) + 1,
                'SNAP', :snapAt, @app_user_id, :note,
                ct.class_subject_id, ct.day_of_week, ct.start_time, ct.end_time, ct.class_time_code, ct.class_time_label,
                ct.room, ct.room_id, ct.start_date, ct.end_date, ct.use_yn, ct.created_at, ct.updated_at
            FROM class_timeslot ct
            JOIN class_subject  cs ON cs.id = ct.class_subject_id
            JOIN class_master   cm ON cm.id = cs.class_id
            WHERE cm.work_location_code = :work
              AND cm.school_stage      = :stage
              AND cm.semester_id       = :semId
        """, p);

        // 초기화 (과목 담당 NULL, 슬롯 삭제, 학기/담임 NULL)
        int csClr = jdbc.update("""
            UPDATE class_subject cs
            JOIN class_master cm ON cm.id = cs.class_id
               AND cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND cm.semester_id       = :semId
            SET cs.teacher_id = NULL,
                cs.updated_at = NOW()
        """, p);

        int ctDel = jdbc.update("""
            DELETE ct FROM class_timeslot ct
            JOIN class_subject cs ON cs.id = ct.class_subject_id
            JOIN class_master  cm ON cm.id = cs.class_id
            WHERE cm.work_location_code = :work
              AND cm.school_stage      = :stage
              AND cm.semester_id       = :semId
        """, p);

        int cmClr = jdbc.update("""
            UPDATE class_master cm
               SET cm.semester_id = NULL,
                   cm.homeroom_teacher_id = NULL,
                   cm.updated_at = NOW()
             WHERE cm.work_location_code = :work
               AND cm.school_stage      = :stage
               AND cm.semester_id       = :semId
        """, p);

        log.info("[Ops CLOSE] SNAP cm/cs/ct = {}/{}/{}, CLEAR cm/cs/ctDel = {}/{}/{}",
                cmSnap, csSnap, ctSnap, cmClr, csClr, ctDel);

        return new CourseOpsDtos.SimpleAck("snapshot+close done",
                beforeClasses, csClr, ctDel);
    }

    // ────────────────────────────────────────────────────────────────
    // RESTORE (선스냅샷 옵션 + 복원)
    // ────────────────────────────────────────────────────────────────
    @Transactional
    public CourseOpsDtos.SimpleAck restoreFromSnapshot(Long appUserId, CourseOpsDtos.RestoreReq req){
        var base = params(req.getWorkLocationCode(), req.getSchoolStage(), req.getSemesterId());

        String note = (req.getNote() == null || req.getNote().isBlank()) ? "스냅샷 복원(ops)" : req.getNote().trim();
        dbVars.setAppVars(appUserId, note); // 세션 변수 주입

        boolean restoreClass    = req.getTargets().isClazz();
        boolean restoreSubject  = req.getTargets().isSubject();
        boolean restoreTimeslot = req.getTargets().isTimeslot();
        if (!restoreClass && !restoreSubject && !restoreTimeslot) {
            throw new IllegalArgumentException("복원할 항목이 없습니다.");
        }

        // 1) 반별 마지막 master SNAP 시각(해당 학기 기준)
        Map<Long, Timestamp> lastByClass = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT h.ref_id AS class_id, MAX(h.event_at) AS last_at
              FROM class_master_hist h
              JOIN class_master cm ON cm.id = h.ref_id
             WHERE h.event_type   = 'SNAP'
               AND h.semester_id  = :semId
               AND cm.work_location_code = :work
               AND cm.school_stage      = :stage
             GROUP BY h.ref_id
        """, base);
        for (Map<String, Object> row : rows) {
            Long classId = ((Number) row.get("class_id")).longValue();
            Timestamp t  = asTimestamp(row.get("last_at"));
            lastByClass.put(classId, t);
        }
        if (lastByClass.isEmpty()){
            throw new IllegalStateException("선택한 범위에서 복원할 스냅샷을 찾을 수 없습니다.");
        }

        // 2) 임시 테이블 준비(복원용 버퍼)
        jdbc.getJdbcOperations().execute("CREATE TEMPORARY TABLE IF NOT EXISTS tmp_restore_class (class_id BIGINT PRIMARY KEY)");
        jdbc.getJdbcOperations().execute("""
            CREATE TEMPORARY TABLE IF NOT EXISTS tmp_restore_master (
                class_id BIGINT PRIMARY KEY,
                semester_id BIGINT NULL,
                homeroom_teacher_id BIGINT NULL,
                grade_code VARCHAR(32) NULL,
                class_code VARCHAR(32) NULL
            )
        """);
        jdbc.getJdbcOperations().execute("""
            CREATE TEMPORARY TABLE IF NOT EXISTS tmp_restore_subject (
                class_subject_id BIGINT PRIMARY KEY,
                teacher_id BIGINT NULL
            )
        """);
        jdbc.getJdbcOperations().execute("""
            CREATE TEMPORARY TABLE IF NOT EXISTS tmp_restore_timeslot (
                class_subject_id BIGINT,
                day_of_week INT,
                start_time TIME,
                end_time TIME,
                class_time_code VARCHAR(64),
                class_time_label VARCHAR(64),
                room VARCHAR(80),
                room_id BIGINT NULL,
                start_date DATE,
                end_date DATE,
                use_yn TINYINT(1)
            )
        """);
        jdbc.getJdbcOperations().execute("DELETE FROM tmp_restore_class");
        jdbc.getJdbcOperations().execute("DELETE FROM tmp_restore_master");
        jdbc.getJdbcOperations().execute("DELETE FROM tmp_restore_subject");
        jdbc.getJdbcOperations().execute("DELETE FROM tmp_restore_timeslot");

        // 3) 대상 반 적재 + 테이블별 SNAP 기준 시각 보정
        for (var e : lastByClass.entrySet()) {
            Long classId = e.getKey();
            Timestamp masterSnapAt = e.getValue();

            jdbc.update("INSERT IGNORE INTO tmp_restore_class(class_id) VALUES(:classId)",
                    new MapSqlParameterSource().addValue("classId", classId));

            // 과목/시간표는 master SNAP 시각보다 늦지 않은 마지막 SNAP을 기준으로 맞춤
            Timestamp subjSnapAt = jdbc.query("""
                SELECT MAX(h.event_at)
                  FROM class_subject_hist h
                  JOIN class_subject cs ON cs.id = h.ref_id
                 WHERE h.event_type = 'SNAP'
                   AND cs.class_id   = :classId
                   AND h.event_at   <= :masterAt
            """, new MapSqlParameterSource().addValue("classId", classId).addValue("masterAt", masterSnapAt),
                    (rs) -> rs.next() ? rs.getTimestamp(1) : null);

            Timestamp timeSnapAt = jdbc.query("""
                SELECT MAX(h.event_at)
                  FROM class_timeslot_hist h
                  JOIN class_subject cs ON cs.id = h.class_subject_id
                 WHERE h.event_type = 'SNAP'
                   AND cs.class_id   = :classId
                   AND h.event_at   <= :masterAt
            """, new MapSqlParameterSource().addValue("classId", classId).addValue("masterAt", masterSnapAt),
                    (rs) -> rs.next() ? rs.getTimestamp(1) : null);

            if (restoreClass) {
                jdbc.update("""
                    INSERT INTO tmp_restore_master (class_id, semester_id, homeroom_teacher_id, grade_code, class_code)
                    SELECT h.ref_id, h.semester_id, h.homeroom_teacher_id, h.grade_code, h.class_code
                      FROM class_master_hist h
                     WHERE h.event_type = 'SNAP'
                       AND h.ref_id     = :classId
                       AND h.event_at   = :t
                """, new MapSqlParameterSource().addValue("classId", classId).addValue("t", masterSnapAt));
            }

            if (restoreSubject && subjSnapAt != null) {
                jdbc.update("""
                    INSERT INTO tmp_restore_subject (class_subject_id, teacher_id)
                    SELECT h.ref_id, h.teacher_id
                      FROM class_subject_hist h
                      JOIN class_subject cs ON cs.id = h.ref_id
                     WHERE h.event_type = 'SNAP'
                       AND h.event_at   = :t
                       AND cs.class_id  = :classId
                """, new MapSqlParameterSource().addValue("classId", classId).addValue("t", subjSnapAt));
            }

            if (restoreTimeslot && timeSnapAt != null) {
                jdbc.update("""
                    INSERT INTO tmp_restore_timeslot
                      (class_subject_id, day_of_week, start_time, end_time,
                       class_time_code, class_time_label, room, room_id,
                       start_date, end_date, use_yn)
                    SELECT
                      h.class_subject_id, h.day_of_week, h.start_time, h.end_time,
                      h.class_time_code, h.class_time_label, h.room, h.room_id,
                      h.start_date, h.end_date, h.use_yn
                      FROM class_timeslot_hist h
                      JOIN class_subject cs ON cs.id = h.class_subject_id
                     WHERE h.event_type = 'SNAP'
                       AND h.event_at   = :t
                       AND cs.class_id  = :classId
                """, new MapSqlParameterSource().addValue("classId", classId).addValue("t", timeSnapAt));
            }
        }

        // 3.5) (옵션) 되돌리기 직전 '현재 상태'를 SNAP 으로 저장(권장: true)
        if (req.isPreSnapshot()) {
            var p = new MapSqlParameterSource()
                    .addValue("note", (note + " (pre-restore SNAP)").trim())
                    .addValue("snapAt", new Timestamp(System.currentTimeMillis()));

            // ⚠️ FIXED: 여기서도 code_full을 동일 계산식으로 공급
            int preCmSnap = jdbc.update("""
                INSERT INTO class_master_hist
                (ref_id, version, event_type, event_at, event_by, event_note,
                 work_location_code, school_stage, grade_code, class_code, code_full,
                 semester_id, code, name, sort_order, homeroom_teacher_id,
                 capacity, status, memo, use_yn, created_at, updated_at)
                SELECT
                    cm.id,
                    COALESCE((SELECT MAX(h.version) FROM class_master_hist h WHERE h.ref_id = cm.id), 0) + 1,
                    'SNAP', :snapAt, @app_user_id, :note,
                    cm.work_location_code, cm.school_stage, cm.grade_code, cm.class_code,
                    CASE
                      WHEN (cm.grade_code IS NULL OR cm.grade_code = '')
                       AND (cm.class_code IS NULL OR cm.class_code = '')
                        THEN cm.code
                      ELSE CONCAT_WS('-', cm.grade_code, cm.class_code, cm.code)
                    END AS code_full,
                    cm.semester_id, cm.code, cm.name, cm.sort_order, cm.homeroom_teacher_id,
                    cm.capacity, cm.status, cm.memo, cm.use_yn, cm.created_at, cm.updated_at
                FROM class_master cm
                JOIN tmp_restore_class t ON t.class_id = cm.id
            """, p);

            int preCsSnap = jdbc.update("""
                INSERT INTO class_subject_hist
                (ref_id, version, event_type, event_at, event_by, event_note,
                 class_id, subject_id, teacher_id, sort_order, use_yn, created_at, updated_at,
                 subject_name_snapshot, teacher_name_snapshot)
                SELECT
                    cs.id,
                    COALESCE((SELECT MAX(h.version) FROM class_subject_hist h WHERE h.ref_id = cs.id), 0) + 1,
                    'SNAP', :snapAt, @app_user_id, :note,
                    cs.class_id, cs.subject_id, cs.teacher_id, cs.sort_order, cs.use_yn, cs.created_at, cs.updated_at,
                    s.name, NULL
                FROM class_subject cs
                JOIN class_master cm ON cm.id = cs.class_id
                JOIN tmp_restore_class t ON t.class_id = cm.id
                LEFT JOIN subject s  ON s.id = cs.subject_id
            """, p);

            int preCtSnap = jdbc.update("""
                INSERT INTO class_timeslot_hist
                (ref_id, version, event_type, event_at, event_by, event_note,
                 class_subject_id, day_of_week, start_time, end_time, class_time_code, class_time_label,
                 room, room_id, start_date, end_date, use_yn, created_at, updated_at)
                SELECT
                    ct.id,
                    COALESCE((SELECT MAX(h.version) FROM class_timeslot_hist h WHERE h.ref_id = ct.id), 0) + 1,
                    'SNAP', :snapAt, @app_user_id, :note,
                    ct.class_subject_id, ct.day_of_week, ct.start_time, ct.end_time, ct.class_time_code, ct.class_time_label,
                    ct.room, ct.room_id, ct.start_date, ct.end_date, ct.use_yn, ct.created_at, ct.updated_at
                FROM class_timeslot ct
                JOIN class_subject  cs ON cs.id = ct.class_subject_id
                JOIN class_master   cm ON cm.id = cs.class_id
                JOIN tmp_restore_class t ON t.class_id = cm.id
            """, p);

            log.info("[Ops RESTORE] pre-SNAP cm/cs/ct = {}/{}/{}", preCmSnap, preCsSnap, preCtSnap);
        }

        // 4) 실제 복원
        long affectedC = 0, affectedS = 0, affectedTDel = 0, affectedTIns = 0;

        if (restoreClass) {
            affectedC = jdbc.update("""
                UPDATE class_master cm
                JOIN tmp_restore_master t ON t.class_id = cm.id
                   SET cm.semester_id        = t.semester_id,
                       cm.homeroom_teacher_id = t.homeroom_teacher_id,
                       cm.grade_code          = t.grade_code,
                       cm.class_code          = t.class_code,
                       cm.updated_at          = NOW()
            """, new MapSqlParameterSource());
        }

        if (restoreSubject) {
            affectedS = jdbc.update("""
                UPDATE class_subject cs
                JOIN tmp_restore_subject t ON t.class_subject_id = cs.id
                   SET cs.teacher_id = t.teacher_id,
                       cs.updated_at = NOW()
            """, new MapSqlParameterSource());
        }

        if (restoreTimeslot) {
            affectedTDel = jdbc.update("""
                DELETE ct FROM class_timeslot ct
                JOIN class_subject cs ON cs.id = ct.class_subject_id
                JOIN tmp_restore_class tc ON tc.class_id = cs.class_id
            """, new MapSqlParameterSource());

            affectedTIns = jdbc.update("""
                INSERT INTO class_timeslot
                  (class_subject_id, day_of_week, start_time, end_time,
                   class_time_code, class_time_label, room, room_id,
                   start_date, end_date, use_yn, created_at, updated_at)
                SELECT
                  class_subject_id, day_of_week, start_time, end_time,
                  class_time_code, class_time_label, room, room_id,
                  start_date, end_date, use_yn, NOW(), NOW()
                  FROM tmp_restore_timeslot
            """, new MapSqlParameterSource());
        }

        log.info("[Ops RESTORE] class={}, subject={}, timeslot del/ins = {}/{}",
                affectedC, affectedS, affectedTDel, affectedTIns);

        return new CourseOpsDtos.SimpleAck("restore done", affectedC, affectedS, affectedTDel + affectedTIns);
    }

    // 파라미터 공통 바인딩
    private MapSqlParameterSource params(String work, String stage, Long semId){
        return new MapSqlParameterSource()
                .addValue("work", work)
                .addValue("stage", stage)
                .addValue("semId", semId);
    }
}