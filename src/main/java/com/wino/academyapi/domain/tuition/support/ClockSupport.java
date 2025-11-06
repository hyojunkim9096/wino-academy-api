// src/main/java/com/wino/academyapi/domain/tuition/support/ClockSupport.java
package com.wino.academyapi.domain.tuition.support;

import java.time.LocalDateTime;

/** 시간 헬퍼 (필요 시 Zone/Clock 주입 구조로 확장 가능) */
public final class ClockSupport {
    private ClockSupport() {}
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}