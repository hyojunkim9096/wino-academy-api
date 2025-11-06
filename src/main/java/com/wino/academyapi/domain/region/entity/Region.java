// src/main/java/com/wino/academyapi/domain/region/entity/Region.java
package com.wino.academyapi.domain.region.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.DynamicInsert;

/**
 * 법정동 지역 마스터 (BOOLEAN useYn 스키마)
 *
 * DB 스키마 가정
 *  - code        : CHAR(10)      PK
 *  - parent_code : CHAR(10)      (depth=1은 null)
 *  - depth       : TINYINT       (1~4)
 *  - name        : VARCHAR(100)  NOT NULL
 *  - path_name   : VARCHAR(300)  NULL
 *  - use_yn      : TINYINT(1)    NOT NULL DEFAULT 1  ← boolean
 *
 * 정규화 정책
 *  - code, parent_code, name, path_name 모두 trim()
 *  - parent_code, path_name 는 trim 후 빈 문자열이면 null
 *  - name 은 NOT NULL + 빈 문자열 금지
 *
 * 성능
 *  - 인덱스: parent_code, (depth,use_yn)
 */
@Entity
@Table(
        name = "region",
        indexes = {
                @Index(name = "idx_region_parent", columnList = "parent_code"),
                @Index(name = "idx_region_depth_use", columnList = "depth,use_yn")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "code")     // PK 기반 동등성
@ToString(exclude = "pathName")
@DynamicInsert                      // INSERT 시 DB의 DEFAULT (use_yn) 활용
public class Region {

    @Id
    @Column(name = "code", columnDefinition = "CHAR(10)", nullable = false)
    @Comment("법정동 코드(10자리)")
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    @Comment("명칭(리프명)")
    private String name;

    @Column(name = "depth", nullable = false, columnDefinition = "TINYINT")
    @Comment("깊이(1~4)")
    private Byte depth; // 1~4

    @Column(name = "parent_code", columnDefinition = "CHAR(10)")
    @Comment("상위 법정동 코드(1뎁스는 null)")
    private String parentCode;

    @Column(name = "path_name", length = 300)
    @Comment("전체 경로명(예: 경기도 수원시 권선구 권선2동)")
    private String pathName;

    @Builder.Default
    @Column(name = "use_yn", nullable = false, columnDefinition = "TINYINT(1)")
    @Comment("사용 여부(true/false)")
    private boolean useYn = true;

    /** 공백/패딩 정규화 */
    @PrePersist
    @PreUpdate
    private void normalize() {
        code       = trim(code);               // PK는 공백 제거만
        parentCode = trimToNull(parentCode);   // "" → null
        pathName   = trimToNull(pathName);     // "" → null

        if (name != null) name = name.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("Region.name must not be blank");
        }

        // depth: 공급자 단계에서 1~4로 들어오도록 운용. 방어적으로 null이면 0.
        if (depth == null) depth = 0;
    }

    // 편의 메서드
    public boolean isUsable() { return useYn; }

    // ---- 내부 유틸 ----
    private static String trim(String s) {
        return (s == null) ? null : s.trim();
    }
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
