// src/main/java/com/wino/academyapi/domain/appsetting/entity/AppSetting.java
package com.wino.academyapi.domain.appsetting.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_setting")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppSetting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 190)
    private String key;

    @Column(name = "setting_val", nullable = false, length = 2000)
    private String value;

    @Column(name = "remark", length = 500)
    private String remark;
}
