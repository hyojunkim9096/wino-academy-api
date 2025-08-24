// src/main/java/com/wino/academyapi/domain/appsetting/entity/AppSettingAudit.java
package com.wino.academyapi.domain.appsetting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AppSetting 변경 감사 로그 (CREATE/UPDATE/DELETE)
 * - 변경 전/후 값과 변경자, 시각 기록
 */
@Entity
@Table(name = "app_setting_audit",
        indexes = {
                @Index(name="idx_app_setting_audit_key", columnList="`key`"),
                @Index(name="idx_app_setting_audit_changed_at", columnList="changed_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSettingAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** app_setting.id (삭제 후에도 추적 가능) */
    @Column(name="setting_id")
    private Long settingId;

    /** 키 (key 또는 key@profile) */
    @Column(name="`key`", nullable=false, length=190)
    private String key;

    /** 변경 유형: CREATE / UPDATE / DELETE */
    @Column(name="change_type", nullable=false, length=16)
    private String changeType;

    /** 변경 전 값 (CREATE면 null 가능) */
    @Column(name="value_before", columnDefinition="text")
    private String valueBefore;

    /** 변경 후 값 (DELETE면 null 가능) */
    @Column(name="value_after", columnDefinition="text")
    private String valueAfter;

    /** 비고(설명) */
    @Column(name="remark")
    private String remark;

    /** 변경자 (로그인 ID) */
    @Column(name="changed_by", length=100)
    private String changedBy;

    /** 변경 시각 */
    @Column(name="changed_at", nullable=false)
    private LocalDateTime changedAt;
}
