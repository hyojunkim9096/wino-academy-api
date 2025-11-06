package com.wino.academyapi.infra.db;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 트랜잭션/같은 커넥션에서 MySQL 세션 변수(@app_user_id, @event_note) 설정
 * - 반드시 @Transactional 메서드 안에서 호출되어야 함 (MANDATORY)
 * - JdbcTemplate로 실행해야 이후 JPA/JdbcTemplate가 동일 커넥션을 공유
 */
@Component
@RequiredArgsConstructor
public class DbSessionVars {

    private final JdbcTemplate jdbc;

    /** 사용자 ID만 세팅 (기존 호환) */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setAppUserId(Long appUserId) {
        Long v = (appUserId == null ? 0L : appUserId);
        try {
            jdbc.update("SET @app_user_id := ?", v);
        } catch (Exception e) {
            // 드라이버 호환성 폴백 (리터럴 주입: Long이라 안전)
            jdbc.execute("SET @app_user_id := " + v);
        }
    }


    /** 사용자 ID + 이벤트 메모 동시 세팅 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setAppVars(Long appUserId, String note) {
        Long v = (appUserId == null ? 0L : appUserId);
        try {
            jdbc.update("SET @app_user_id := ?, @event_note := ?", v, note);
        } catch (Exception e) {
            String safeNote = (note == null) ? "NULL" : ("'" + note.replace("'", "''") + "'");
            jdbc.execute("SET @app_user_id := " + v + ", @event_note := " + safeNote);
        }
    }
}