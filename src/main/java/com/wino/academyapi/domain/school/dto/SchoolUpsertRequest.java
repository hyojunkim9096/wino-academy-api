// src/main/java/com/wino/academyapi/domain/school/dto/SchoolUpsertRequest.java
package com.wino.academyapi.domain.school.dto;

import com.wino.academyapi.domain.school.entity.SchoolStage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 생성/수정(부분) 공용 DTO
 *
 * 설계 포인트
 * - 하나의 DTO를 POST(생성) / PATCH(부분수정) 모두에서 사용하기 위해 "검증 그룹"을 도입.
 *   · Create 그룹: name, stage 필수
 *   · Patch  그룹: 모두 선택(널이면 무시하고 서비스에서 병합)
 * - 엔티티/DDL 길이와 검증 길이를 일치(운영 시 400/500 방지)
 * - 문자열 공백/빈문자 → null 정규화를 위한 sanitized() 제공(서비스에서 호출 권장)
 */
@JsonIgnoreProperties(ignoreUnknown = true)          // 모르는 필드는 무시(클라이언트 확장에 안전)
@JsonInclude(JsonInclude.Include.NON_NULL)           // 응답으로도 쓰일 수 있으니 null 필드 제외
@JsonPropertyOrder({ "name","stage","eduOfficeCode","phone","postalCode","address","detailAddress","admCode","lat","lng","homepageUrl","active" })
public record SchoolUpsertRequest(

        /* ========================= 기본 정보 ========================= */

        @Schema(description = "학교명", example = "수원고등학교")
        @NotBlank(groups = Create.class, message = "name은 필수입니다.")
        @Size(max = 200, message = "name은 최대 200자입니다.")      // DDL: VARCHAR(200)
        String name,

        @Schema(description = "학부(E/M/H)", example = "H")
        @NotNull(groups = Create.class, message = "stage는 필수입니다.")
        SchoolStage stage,

        /* ========================= 행정/연계 ========================= */

        @Schema(description = "교육청 코드(선택, 예: B10). 환경에 따라 형식 검증은 선택.", example = "B10")
        @Size(max = 50, message = "eduOfficeCode는 최대 50자입니다.") // DDL: VARCHAR(50)
        // 필요 시 아래 패턴을 켜세요(교육청 코드 형식 고정 시)
        // @Pattern(regexp = "^[A-Z]\\d{2}$", message = "eduOfficeCode는 예: B10 형태여야 합니다.")
        String eduOfficeCode,

        @Schema(description = "전화번호", example = "031-123-4567")
        @Size(max = 50, message = "phone은 최대 50자입니다.")       // DDL: VARCHAR(50)
        @Pattern(regexp = "^[0-9\\-+()\\s]*$", message = "phone 형식이 올바르지 않습니다.") // 숫자/+, -, (), 공백만 허용
        String phone,

        /* ========================= 주소/지역/좌표 ========================= */

        @Schema(description = "우편번호(신주소)", example = "16459")
        @Pattern(regexp = "^\\d{5}$", message = "postalCode는 5자리 숫자여야 합니다.") // 한국 5자리
        String postalCode,                                    // DDL: VARCHAR(10) 이지만 5자리만 허용(정책)

        @Schema(description = "도로명 주소(신주소)", example = "경기도 수원시 권선구 권선로 123")
        @Size(max = 300, message = "address는 최대 300자입니다.")   // DDL: VARCHAR(300)
        String address,

        @Schema(description = "지번(구주소) — 지오코딩 시 규칙적으로 저장", example = "경기도 수원시 권선구 고색동 123-4")
        @Size(max = 300, message = "detailAddress는 최대 300자입니다.") // DDL: VARCHAR(300)
        String detailAddress,

        @Schema(description = "법정동 코드(2/5/8/10자리 숫자)", example = "4111500000")
        @Pattern(
                regexp = "^\\d{2}(\\d{3}(\\d{3}(\\d{2})?)?)?$",
                message = "admCode는 2/5/8/10자리 숫자여야 합니다."
        )
        String admCode,                                       // DDL: CHAR(10)

        @Schema(description = "위도", example = "37.2456789")
        @DecimalMin(value = "-90.0",  message = "lat는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0",   message = "lat는 90 이하이어야 합니다.")
        @Digits(integer = 3, fraction = 7, message = "lat는 소수 7자리까지 허용됩니다.") // 엔티티 DECIMAL(10,7)과 일치
        BigDecimal lat,                                       // DDL: DECIMAL(10,7)

        @Schema(description = "경도", example = "127.0123456")
        @DecimalMin(value = "-180.0", message = "lng는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0",  message = "lng는 180 이하이어야 합니다.")
        @Digits(integer = 3, fraction = 7, message = "lng는 소수 7자리까지 허용됩니다.") // 엔티티 DECIMAL(10,7)과 일치
        BigDecimal lng,                                       // DDL: DECIMAL(10,7)

        @Schema(description = "홈페이지 URL", example = "https://www.suwon.hs.kr")
        @Size(max = 300, message = "homepageUrl은 최대 300자입니다.") // DDL: VARCHAR(300)
        String homepageUrl,

        @Schema(description = "활성 여부(null이면 서비스 기본값 처리)", example = "true")
        Boolean active

) {
    /* ========================= 검증 그룹 ========================= */
    /** 생성 시 필수 필드 적용 그룹 */
    public interface Create {}
    /** PATCH(부분수정) 시 선택 필드 적용 그룹 — 모든 항목 선택 */
    public interface Patch {}

    /* ========================= 유틸리티 ========================= */

    /**
     * 공백/빈문자 → null 정규화된 복사본을 반환.
     * 서비스 레이어에서 저장 전 한 번 호출하면 DB/검색 일관성 유지에 유용.
     */
    public SchoolUpsertRequest sanitized() {
        return new SchoolUpsertRequest(
                t(name),
                stage,
                t(eduOfficeCode),
                t(phone),
                t(postalCode),
                t(address),
                t(detailAddress),
                t(admCode),
                lat,
                lng,
                t(homepageUrl),
                active
        );
    }

    /* 내부 유틸: trim → null */
    private static String t(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }
}
