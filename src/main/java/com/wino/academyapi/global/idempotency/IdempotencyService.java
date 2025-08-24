// src/main/java/com/wino/academyapi/global/idempotency/IdempotencyService.java
package com.wino.academyapi.global.idempotency;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 간단한 멱등성 서비스: X-Idempotency-Key 를 일정 시간 동안 재사용 금지
 * - 단일 인스턴스용 메모리 캐시 (스케일아웃 시 Redis 등으로 대체)
 */
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    /** 키가 처음이면 true, 이미 본 키면 false */
    public boolean checkAndMark(String key) {
        cleanup();
        if (key == null || key.isBlank()) return true; // 키 없으면 검사 스킵
        Instant now = Instant.now();
        Instant prev = seen.putIfAbsent(key, now);
        return prev == null;
    }

    /** 오래된 키 정리 */
    private void cleanup() {
        Instant cutoff = Instant.now().minus(TTL);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
