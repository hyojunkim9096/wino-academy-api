package com.wino.academyapi.domain.enroll.service;

import com.wino.academyapi.domain.enroll.dto.TimeslotSuggestion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * 교차수업 후보 검색 서비스
 *
 * 설계 포인트
 * - class_timeslot 기준 후보를 뽑되, excludeStudentId의 기존 수강 타임슬롯과
 *   (요일 동일) + (시간 겹침)이 있는 슬롯은 제외(NOT EXISTS)
 * - 기간 겹침: (ts.start_date ≤ to) AND (from ≤ ts.end_date) 형태
 * - 과목 표시: subject LEFT JOIN → subject_name/subject_code 반환
 * - limit 는 JPA Query#setMaxResults 로 안전 제한(벤더별 LIMIT 문법 차이 회피)
 *
 * 주의
 * - gradeCode 컬럼 위치(반/과목 단위)는 프로젝트 스키마에 따라 다를 수 있습니다.
 *   안전을 위해 현재 쿼리에서는 gradeCode 필터를 적용하지 않았습니다(파라미터만 보유).
 *   필요 시 실제 컬럼명에 맞게 where 절을 한 줄 추가해 주세요.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentSuggestService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<TimeslotSuggestion> suggest(
            String locCode,
            String stage,
            String gradeCode,     // TODO: 실제 스키마 확인 후 조건 추가 필요
            List<Integer> days,   // null 또는 비어있으면 전체
            LocalDate fromDate,   // null 허용
            LocalDate toDate,     // null 허용
            Long excludeStudentId,// null 허용(충돌제거 skip)
            int limit             // 1~200 권장
    ){
        final int max = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        final boolean hasDays = days != null && !days.isEmpty();

        // 기간 기본값 (무제한) — MySQL DATE 유효 범위에 맞춤
        final LocalDate from = (fromDate == null ? LocalDate.of(1000,1,1) : fromDate);
        final LocalDate to   = (toDate   == null ? LocalDate.of(9999,12,31) : toDate);

        StringBuilder sql = new StringBuilder("""
            SELECT
              cm.id               AS class_id,
              cm.code             AS class_code,      -- ✅ 프로젝트 스키마: class_master.code
              cm.name             AS class_name,
              ts.id               AS timeslot_id,
              ts.day_of_week      AS day_of_week,
              ts.start_time       AS start_time,
              ts.end_time         AS end_time,
              ts.class_time_code  AS class_time_code,
              ts.class_time_label AS class_time_label,
              s.name              AS subject_name,    -- ✅ 과목명
              s.code              AS subject_code     -- ✅ 과목코드(없으면 NULL)
            FROM class_timeslot ts
            JOIN class_subject  cs ON cs.id = ts.class_subject_id
            JOIN class_master   cm ON cm.id = cs.class_id
            LEFT JOIN subject   s  ON s.id  = cs.subject_id
            WHERE COALESCE(cm.use_yn,1) = 1
              AND COALESCE(ts.use_yn,1) = 1
              AND COALESCE(ts.start_date, DATE '1000-01-01') <= :toDate
              AND COALESCE(ts.end_date,   DATE '9999-12-31') >= :fromDate
        """);

        Map<String, Object> params = new HashMap<>();
        params.put("fromDate", java.sql.Date.valueOf(from));
        params.put("toDate",   java.sql.Date.valueOf(to));

        if (locCode != null && !locCode.isBlank()) {
            sql.append(" AND cm.work_location_code = :loc ");
            params.put("loc", locCode);
        }
        if (stage != null && !stage.isBlank()) {
            sql.append(" AND cm.school_stage = :stage ");
            params.put("stage", stage);
        }
        // gradeCode 필터는 스키마 확인 후 아래 주석을 해제해 실제 컬럼명으로 사용하세요.
        // if (gradeCode != null && !gradeCode.isBlank()) {
        //     sql.append(" AND cs.grade_code = :grade "); // 또는 cm.grade_code
        //     params.put("grade", gradeCode);
        // }

        if (hasDays) {
            sql.append(" AND ts.day_of_week IN (:days) ");
            params.put("days", days);
        }

        // 충돌 제거(NOT EXISTS): 제외 학생의 ACTIVE 배정 타임슬롯과 요일/시간 겹침 제거
        if (excludeStudentId != null) {
            sql.append("""
                AND NOT EXISTS (
                  SELECT 1
                    FROM student_class_enrollment e
                    JOIN student_enroll_timeslot x ON x.enroll_id = e.id
                    JOIN class_timeslot ets ON ets.id = x.timeslot_id
                   WHERE e.student_id = :exSid
                     AND UPPER(COALESCE(e.status,'ACTIVE')) = 'ACTIVE'
                     AND (COALESCE(e.left_at, DATE '9999-12-31') >= :fromDate AND e.enrolled_at <= :toDate)
                     AND COALESCE(ets.use_yn,1) = 1
                     AND ets.day_of_week = ts.day_of_week
                     AND (ets.start_time < ts.end_time AND ts.start_time < ets.end_time)
                )
            """);
            params.put("exSid", excludeStudentId);
        }

        sql.append(" ORDER BY ts.day_of_week, ts.start_time, cm.code ");

        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        q.setMaxResults(max);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        List<TimeslotSuggestion> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            // 인덱스 맵핑: 0..10
            TimeslotSuggestion s = TimeslotSuggestion.builder()
                    .classId        (((Number) r[0]).longValue())
                    .classCode      ((String) r[1])
                    .className      ((String) r[2])
                    .timeslotId     (((Number) r[3]).longValue())
                    .dayOfWeek      (((Number) r[4]).intValue())
                    .startTime      (toLocalTime(r[5]))   // TIME 안전 변환
                    .endTime        (toLocalTime(r[6]))   // TIME 안전 변환
                    .classTimeCode  ((String) r[7])
                    .classTimeLabel ((String) r[8])
                    .subjectName    ((String) r[9])       // ✅ 과목명 매핑
                    .subjectCode    ((String) r[10])      // ✅ 과목코드 매핑
                    .build();
            out.add(s);
        }
        return out;
    }

    /** TIME 값 안전 변환 헬퍼(LocalTime / java.sql.Time / String 대응) */
    private static LocalTime toLocalTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalTime lt) return lt;
        if (v instanceof java.sql.Time t) return t.toLocalTime();
        if (v instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) return null;
            if (str.length() == 5) str = str + ":00"; // "HH:mm" → "HH:mm:ss"
            try { return LocalTime.parse(str); } catch (Exception ignore) { return null; }
        }
        return null;
    }
}