// src/main/java/com/wino/academyapi/domain/cors/entity/CorsSetting.java
package com.wino.academyapi.domain.cors.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="cors_setting")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CorsSetting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String allowedOrigins;
    private String allowedMethods;
    private String allowedHeaders;
    private String exposedHeaders;
    private Integer maxAge;
    private Boolean allowCredentials;
}
