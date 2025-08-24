// src/main/java/com/wino/academyapi/domain/syslog/service/AdminSystemLogService.java
package com.wino.academyapi.domain.syslog.service;

import com.wino.academyapi.domain.syslog.entity.AdminSystemLog;
import com.wino.academyapi.domain.syslog.repository.AdminSystemLogRepository;
import com.wino.academyapi.global.audit.SyslogScreenResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 시스템 로그 저장 서비스 — DDL(현재 스키마) 정합 보장 버전
 *
 * 흐름
 *  1) 필터(AdminActionLoggingFilter)가 AdminSystemLog를 생성해 save(row) 호출
 *  2) admin_menu 매핑으로 screenCode/screenName 보강(비어 있을 때만)
 *  3) 모든 문자열 컬럼을 DDL 길이에 맞춰 trim + truncate
 *  4) created_at 누락 시 now()로 보정 (DB DEFAULT에 의존하지 않도록)
 *  5) 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSystemLogService {

    private final AdminSystemLogRepository repo;
    private final SyslogScreenResolver screenResolver;

    // DDL 길이 상수(엔티티/DDL과 반드시 동기화)
    private static final int LEN_ACTOR_ID     = 120;
    private static final int LEN_METHOD       = 10;
    private static final int LEN_PATH         = 500;
    private static final int LEN_SCREEN_CODE  = 120;
    private static final int LEN_SCREEN_NAME  = 200;
    private static final int LEN_RESOURCE_TYP = 60;
    private static final int LEN_RESOURCE_ID  = 80;
    private static final int LEN_ACTION       = 80;
    private static final int LEN_IP           = 64;
    private static final int LEN_UA           = 512;
    private static final int LEN_ERROR_MSG    = 1000;

    /**
     * 시스템 로그 저장 엔트리포인트.
     * - 화면코드/화면명 자동 보강 → 필드 길이 정규화 → createdAt 보정 → 저장
     */
    @Transactional
    public void save(AdminSystemLog row) {
        if (row == null) return;

        // ── 0) created_at 보정(필터에서 누락되었더라도 NOT NULL 충족)
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDateTime.now());
        }

        // ── 1) admin_menu 기반 화면 코드/이름 보강 ─────────────────────────────
        try {
            if (isBlank(row.getScreenCode()) || isBlank(row.getScreenName())) {
                screenResolver.resolveFromApiPath(row.getPath()).ifPresent(r -> {
                    if (isBlank(row.getScreenCode())) row.setScreenCode(r.code());
                    if (isBlank(row.getScreenName())) row.setScreenName(r.name());
                    if (log.isDebugEnabled()) {
                        log.debug("[SysLog] screen resolved: code='{}', name='{}', path='{}'",
                                r.code(), r.name(), row.getPath());
                    }
                });
            }
        } catch (Exception e) {
            // 매핑 실패는 저장을 막지 않음
            log.debug("[SysLog] screen resolve skipped: {}", e.toString());
        }

        // ── 2) 문자열 컬럼 길이 정규화(트림 + 트렁케이션) ──────────────────────
        try {
            row.setActorUserId(  trimAndCut(row.getActorUserId(),  LEN_ACTOR_ID));
            row.setMethod(       upperAndCut(required(row.getMethod(), "method"), LEN_METHOD)); // NOT NULL
            row.setPath(         trimAndCut(required(row.getPath(), "path"),      LEN_PATH));   // NOT NULL
            row.setScreenCode(   trimAndCut(row.getScreenCode(),    LEN_SCREEN_CODE));
            row.setScreenName(   trimAndCut(row.getScreenName(),    LEN_SCREEN_NAME));
            row.setResourceType( trimAndCut(row.getResourceType(),  LEN_RESOURCE_TYP));
            row.setResourceId(   trimAndCut(row.getResourceId(),    LEN_RESOURCE_ID));
            row.setAction(       trimAndCut(row.getAction(),        LEN_ACTION));
            row.setIpAddress(    trimAndCut(row.getIpAddress(),     LEN_IP));
            row.setUserAgent(    trimAndCut(row.getUserAgent(),     LEN_UA));
            row.setErrorMessage( trimAndCut(row.getErrorMessage(),  LEN_ERROR_MSG));
            // requestBody / extraJson 은 LONGTEXT — 필터에서 캡처 크기 제어/마스킹
        } catch (Exception e) {
            log.debug("[SysLog] normalize failed: {}", e.toString());
        }

        // ── 3) 저장 ───────────────────────────────────────────────────────────
        repo.save(row);
    }

    // ───────────────────── 내부 유틸 ─────────────────────

    /** null 또는 공백문자만 있으면 true */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** null/blank 금지 필드 보정: 빈 값이면 IllegalArgumentException 발생 대신 안전 기본 처리 */
    private static String required(String s, String field) {
        if (isBlank(s)) {
            // NOT NULL 제약 위반을 방지하기 위해 안전 기본값 사용
            // method → "GET", path → "/"
            return "method".equals(field) ? "GET" : "/";
        }
        return s;
    }

    /** 공통: trim 후 최대 길이로 자르기(null 안전) */
    private static String trimAndCut(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (max <= 0 || t.length() <= max) return t;
        return t.substring(0, max);
    }

    /** HTTP 메서드용: trim → upper → 길이 컷 */
    private static String upperAndCut(String s, int max) {
        if (s == null) return null;
        String t = s.trim().toUpperCase();
        if (max <= 0 || t.length() <= max) return t;
        return t.substring(0, max);
    }
}
