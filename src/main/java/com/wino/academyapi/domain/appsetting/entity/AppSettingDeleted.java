// src/main/java/com/wino/academyapi/domain/appsetting/entity/AppSettingDeleted.java
package com.wino.academyapi.domain.appsetting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity @Table(name="app_setting_deleted")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSettingDeleted {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long settingId;
    @Column(name="`key`") private String key;
    @Column(name="`value`") private String value;
    private String remark;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
