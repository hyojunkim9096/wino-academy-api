package com.wino.academyapi.domain.region.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * 프런트/상위 레이어에 돌려줄 지역 정보 표준 DTO
 * - admCode: 법정동 10자리
 * - hCode:  행정동 8자리(필요시)
 * - lat/lng: 좌표(double)
 * - address/roadAddress: 원문 주소(옵션)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL) // ✅ null 필드 미포함
public class GeoRegionDto {

    @Schema(description = "법정동 코드(10자리)", example = "4113500000")
    private String admCode;

    @Schema(description = "행정동 코드(8자리, 선택)", example = "41135810")
    private String hCode;

    @Schema(description = "시/도", example = "경기도")
    private String sido;

    @Schema(description = "시/군/구", example = "수원시 영통구")
    private String sigungu;

    @Schema(description = "읍/면/동", example = "매탄동")
    private String eupmyondong;

    @Schema(description = "위도", example = "37.2598")
    private Double lat;

    @Schema(description = "경도", example = "127.0459")
    private Double lng;

    @Schema(description = "지번 주소(선택)", example = "경기도 수원시 영통구 매탄동 123-45")
    private String address;

    @Schema(description = "도로명 주소(선택)", example = "경기도 수원시 영통구 효원로 123")
    private String roadAddress;
}
