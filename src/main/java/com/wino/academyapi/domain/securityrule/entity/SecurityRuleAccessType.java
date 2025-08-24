// src/main/java/com/wino/academyapi/domain/securityrule/entity/SecurityRuleAccessType.java
package com.wino.academyapi.domain.securityrule.entity;

/** 접근 유형 열거형 (로직 안정성 보장) */
public enum SecurityRuleAccessType {
    PERMIT_ALL, DENY_ALL, AUTHENTICATED, HAS_ANY_AUTHORITY
}
