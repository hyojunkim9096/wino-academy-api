// src/main/java/com/wino/academyapi/domain/tuition/support/NoopTuitionCategoryUsageChecker.java
package com.wino.academyapi.domain.tuition.support;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** 기본 구현: 외부 참조 없음으로 간주 */
@Component
public class NoopTuitionCategoryUsageChecker implements TuitionCategoryUsageChecker {
    @Override
    public Optional<String> findFirstUsageReason(Long categoryId) {
        return Optional.empty();
    }
}