// src/main/java/com/wino/academyapi/domain/tuition/support/TuitionCategoryUsageChecker.java
package com.wino.academyapi.domain.tuition.support;

import java.util.Optional;

/**
 * 외부 도메인에서 tuition_category를 참조 중인지 판단하는 확장 포인트.
 * - Optional<String> : 사용 중이면 "어디서 왜"를 한 줄 메시지로 리턴.
 * - 예: "enrollment_plan에서 3건 참조"
 */
public interface TuitionCategoryUsageChecker {
    Optional<String> findFirstUsageReason(Long categoryId);
}