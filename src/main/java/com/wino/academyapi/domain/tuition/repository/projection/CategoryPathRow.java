// src/main/java/com/wino/academyapi/domain/tuition/repository/projection/CategoryPathRow.java
package com.wino.academyapi.domain.tuition.repository.projection;

/**
 * 카테고리 경로 결과
 * - leafId: 입력한 카테고리(리프) id
 * - path  : "상위/하위/리프" 형태의 전체 경로
 */
public interface CategoryPathRow {
    Long getLeafId();
    String getPath();
}