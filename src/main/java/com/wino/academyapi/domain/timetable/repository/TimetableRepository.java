// src/main/java/com/wino/academyapi/domain/timetable/repository/TimetableRepository.java
package com.wino.academyapi.domain.timetable.repository;

import com.wino.academyapi.domain.timetable.dto.TimetableDtos.EventRes;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * 시간표 조회 전용 Repository (Native SQL, JDBC Template)
 *
 * 테이블:
 *  - class_master(cm): id, name, work_location_code, semester_id, ...
 *  - class_subject(cs): id, class_id, subject_id, teacher_id, ...
 *  - class_timeslot(ct): id, class_subject_id, day_of_week(1~7), start_time, end_time, room, use_yn
 *  - subject(subj): id, name
 *  - admin_user_info(aui): id, user_name, ...
 *
 * ★ 포인트: 학기/관 모두 "선택"이므로 WHERE 절에
 *     AND (? IS NULL OR cm.semester_id = ?)
 *     AND (? IS NULL OR cm.work_location_code = ?)
 *   형태로 조건을 둔다.
 */
@Repository
@RequiredArgsConstructor
public class TimetableRepository {

    private final JdbcTemplate jdbc;

    // ===== RowMappers ==================================================

    /** 단일 교사 모드 */
    private static final RowMapper<EventRes> TEACHER_MAPPER = new RowMapper<>() {
        @Override public EventRes mapRow(ResultSet rs, int rowNum) throws SQLException {
            final String subjectName = rs.getString("subject_name");
            final String room        = rs.getString("room");
            String subtitle = Objects.requireNonNullElse(subjectName, "-");
            if (room != null && !room.isBlank()) subtitle += " / " + room;

            return EventRes.builder()
                    .dayOfWeek(rs.getInt("day_of_week"))
                    .start(rs.getString("start_hhmm"))
                    .end(rs.getString("end_hhmm"))
                    .title(rs.getString("class_name"))
                    .subtitle(subtitle)
                    .teacherName(null)
                    .workLocationCode(rs.getString("work_location_code"))
                    .build();
        }
    };

    /** 반 모드 */
    private static final RowMapper<EventRes> CLASS_MAPPER = new RowMapper<>() {
        @Override public EventRes mapRow(ResultSet rs, int rowNum) throws SQLException {
            final String teacherLabel = rs.getString("teacher_label");
            final String room         = rs.getString("room");
            String sub = teacherLabel != null ? teacherLabel : "-";
            if (room != null && !room.isBlank()) sub += " / " + room;

            return EventRes.builder()
                    .dayOfWeek(rs.getInt("day_of_week"))
                    .start(rs.getString("start_hhmm"))
                    .end(rs.getString("end_hhmm"))
                    .title(rs.getString("subject_name"))
                    .subtitle(sub)
                    .teacherName(null)
                    .workLocationCode(rs.getString("work_location_code"))
                    .build();
        }
    };

    /** 전체 교사 모드 */
    private static final RowMapper<EventRes> ALL_TEACHERS_MAPPER = new RowMapper<>() {
        @Override public EventRes mapRow(ResultSet rs, int rowNum) throws SQLException {
            final String subjectName = rs.getString("subject_name");
            final String room        = rs.getString("room");
            String subtitle = Objects.requireNonNullElse(subjectName, "-");
            if (room != null && !room.isBlank()) subtitle += " / " + room;

            return EventRes.builder()
                    .dayOfWeek(rs.getInt("day_of_week"))
                    .start(rs.getString("start_hhmm"))
                    .end(rs.getString("end_hhmm"))
                    .title(rs.getString("class_name"))
                    .subtitle(subtitle)
                    .teacherName(rs.getString("teacher_name"))
                    .workLocationCode(rs.getString("work_location_code"))
                    .build();
        }
    };

    // ===== Queries =====================================================

    /**
     * 단일 교사 기준 시간표
     * - 필수: teacherId
     * - 선택: semesterId, workLocation
     */
    public List<EventRes> findTeacherEvents(Long teacherId, Long semesterId, String workLocation){
        String sql =
                "SELECT " +
                        "  ct.day_of_week, " +
                        "  DATE_FORMAT(ct.start_time, '%H:%i') AS start_hhmm, " +
                        "  DATE_FORMAT(ct.end_time,   '%H:%i') AS end_hhmm, " +
                        "  cm.name AS class_name, " +
                        "  subj.name AS subject_name, " +
                        "  ct.room AS room, " +
                        "  cm.work_location_code AS work_location_code " +
                        "FROM class_timeslot ct " +
                        "JOIN class_subject cs ON cs.id = ct.class_subject_id " +
                        "JOIN class_master cm  ON cm.id = cs.class_id " +
                        "LEFT JOIN subject subj ON subj.id = cs.subject_id " +
                        "WHERE ct.use_yn = 1 " +
                        "  AND cs.teacher_id = ? " +
                        "  AND (? IS NULL OR cm.semester_id = ?) " +          // ★ 학기 선택적
                        "  AND (? IS NULL OR cm.work_location_code = ?) " +   // ★ 관 선택적
                        "ORDER BY ct.day_of_week, ct.start_time";

        // 파라미터 순서 주의: teacherId, semesterId, semesterId, workLocation, workLocation
        return jdbc.query(sql, TEACHER_MAPPER, teacherId, semesterId, semesterId, workLocation, workLocation);
    }

    /**
     * 반 기준 시간표 (관/반 필수)
     */
    public List<EventRes> findClassEvents(String workLocation, Long classId){
        String sql =
                "SELECT " +
                        "  ct.day_of_week, " +
                        "  DATE_FORMAT(ct.start_time, '%H:%i') AS start_hhmm, " +
                        "  DATE_FORMAT(ct.end_time,   '%H:%i') AS end_hhmm, " +
                        "  subj.name AS subject_name, " +
                        "  cm.work_location_code AS work_location_code, " +
                        "  COALESCE(aui.user_name, CONCAT('T#', cs.teacher_id)) AS teacher_label, " +
                        "  ct.room AS room " +
                        "FROM class_timeslot ct " +
                        "JOIN class_subject cs ON cs.id = ct.class_subject_id " +
                        "JOIN class_master cm  ON cm.id = cs.class_id " +
                        "LEFT JOIN subject subj ON subj.id = cs.subject_id " +
                        "LEFT JOIN admin_user_info aui ON aui.id = cs.teacher_id " +
                        "WHERE ct.use_yn = 1 " +
                        "  AND cm.id = ? " +
                        "  AND cm.work_location_code = ? " +
                        "ORDER BY ct.day_of_week, ct.start_time";

        return jdbc.query(sql, CLASS_MAPPER, classId, workLocation);
    }

    /**
     * 전체 교사 시간표
     * - 선택: semesterId, workLocation
     */
    public List<EventRes> findAllTeacherEvents(Long semesterId, String workLocation){
        String sql =
                "SELECT " +
                        "  ct.day_of_week, " +
                        "  DATE_FORMAT(ct.start_time, '%H:%i') AS start_hhmm, " +
                        "  DATE_FORMAT(ct.end_time,   '%H:%i') AS end_hhmm, " +
                        "  cm.name AS class_name, " +
                        "  subj.name AS subject_name, " +
                        "  ct.room AS room, " +
                        "  cm.work_location_code AS work_location_code, " +
                        "  COALESCE(aui.user_name, CONCAT('T#', cs.teacher_id)) AS teacher_name " +
                        "FROM class_timeslot ct " +
                        "JOIN class_subject cs ON cs.id = ct.class_subject_id " +
                        "JOIN class_master cm  ON cm.id = cs.class_id " +
                        "LEFT JOIN subject subj ON subj.id = cs.subject_id " +
                        "LEFT JOIN admin_user_info aui ON aui.id = cs.teacher_id " +
                        "WHERE ct.use_yn = 1 " +
                        "  AND (? IS NULL OR cm.semester_id = ?) " +          // ★ 학기 선택적
                        "  AND (? IS NULL OR cm.work_location_code = ?) " +   // ★ 관 선택적
                        "ORDER BY ct.day_of_week, ct.start_time";

        // 파라미터: semesterId, semesterId, workLocation, workLocation
        return jdbc.query(sql, ALL_TEACHERS_MAPPER, semesterId, semesterId, workLocation, workLocation);
    }

    /** 반-관 유효성 검증 */
    public boolean existsClassInWork(Long classId, String workLocation){
        String sql = "SELECT COUNT(*) FROM class_master WHERE id = ? AND work_location_code = ?";
        Integer n = jdbc.queryForObject(sql, Integer.class, classId, workLocation);
        return n != null && n > 0;
    }
}