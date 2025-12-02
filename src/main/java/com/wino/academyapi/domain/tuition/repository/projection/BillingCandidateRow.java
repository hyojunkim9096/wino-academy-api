// src/main/java/com/wino/academyapi/domain/tuition/repository/projection/BillingCandidateRow.java
package com.wino.academyapi.domain.tuition.repository.projection;

import java.math.BigDecimal;

/**
 * 미리보기 후보 행(네이티브 쿼리 alias와 동일한 getter 이름 필수)
 */
public interface BillingCandidateRow {
    Long getTuitionId();
    Long getStudentId();
    String getStudentName();
    String getSchoolStage();
    String getWorkLocationCode();

    Long getTuitionPriceId();
    String getUnit();
    BigDecimal getPrice();
    Long getCategoryId();
    String getCategoryName();

    String getBillMonth();
    Boolean getAlreadyBilled();
}