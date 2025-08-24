// src/main/java/com/wino/academyapi/domain/dbconn/entity/DbConnectionProfile.java
package com.wino.academyapi.domain.dbconn.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;            // ✅ JSON 응답에서 숨김
import jakarta.persistence.*;
import lombok.*;

/**
 * DB 연결 프로필
 * - envCode: dev | prod
 * - isActive: NULL=비활성, TRUE(1)=활성  (UNIQUE(env_code, is_active)로 env당 1개 보장)
 *
 * 주의:
 * - 컨트롤러가 엔티티를 그대로 반환하므로, boolean "isXxx" 메서드는
 *   기본적으로 JSON 필드로 직렬화된다. (prod/dev 같은 불필요 필드 등장)
 *   → @JsonIgnore 로 숨겨서 응답을 깔끔하게 유지한다.
 */
@Entity
@Table(
        name = "db_connection_profile",
        indexes = {
                @Index(name = "ix_dbconn_profile_env_only", columnList = "env_code")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DbConnectionProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_name", nullable = false, length = 100)
    private String profileName;

    @Column(name = "env_code", nullable = false, length = 50)
    private String envCode;                                 // "dev" | "prod"

    @Column(name = "jdbc_url", nullable = false, length = 500)
    private String jdbcUrl;

    @Column(name = "username", nullable = false, length = 190)
    private String username;

    @Column(name = "enc_password", length = 1000)
    private String encPassword;

    @Column(name = "driver_class", nullable = false, length = 190)
    private String driverClass;

    @Column(name = "maximum_pool_size") private Integer maximumPoolSize;
    @Column(name = "minimum_idle")      private Integer minimumIdle;
    @Column(name = "idle_timeout_ms")   private Long    idleTimeoutMs;
    @Column(name = "max_lifetime_ms")   private Long    maxLifetimeMs;

    /** NULL=비활성, TRUE(1)=활성 */
    @Column(name = "is_active", nullable = true)
    private Boolean isActive;

    @Column(name = "remark", length = 500)
    private String remark;

    // ───────────────── 편의 메서드(응답에 노출되지 않도록 숨김) ─────────────────

    /** 현재 프로필이 dev 인가? */
    @Transient
    @JsonIgnore
    public boolean isDev()  {
        return "dev".equalsIgnoreCase(envCode);
    }

    /** 현재 프로필이 prod 인가? */
    @Transient
    @JsonIgnore
    public boolean isProd() {
        return "prod".equalsIgnoreCase(envCode);
    }
}
