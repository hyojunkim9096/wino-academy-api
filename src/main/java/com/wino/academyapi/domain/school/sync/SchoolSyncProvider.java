// src/main/java/com/wino/academyapi/domain/school/sync/SchoolSyncProvider.java
package com.wino.academyapi.domain.school.sync;

import com.wino.academyapi.domain.school.entity.SchoolStage;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface SchoolSyncProvider {

    /** 이 Provider가 동작 가능한지 (설정/키 유무 등) */
    boolean isEnabled();

    /** 식별용 이름 (예: "alimi") */
    String name();

    /**
     * 범위(scopes) 및 학부(stages) 제한으로 원격 레코드를 가져오고, 레코드 단위로 sink에 전달
     * - scopes: 교육청/지역 등 원격 API의 범위 개념(없거나 비면 '전체')
     * - stages: E/M/H 제한(없거나 비면 '전체')
     */
    FetchReport fetch(Set<String> scopes, Set<SchoolStage> stages, Consumer<RemoteSchool> sink) throws Exception;

    /* ====== 전송 모델 ====== */
    @Builder @Getter
    class RemoteSchool {
        private final String externalCode;        // 업서트 키 (필수)
        private final String name;
        private final SchoolStage stage;
        private final Boolean active;

        private final String eduOfficeCode;
        private final String postalCode;
        private final String address;             // 도로명
        private final String detailAddress;       // 지번(옛 주소)
        private final String admCode;
        private final BigDecimal lat;
        private final BigDecimal lng;

        private final String homepageUrl;
        private final String phone;

        /** 레코드가 소속된 scope 힌트(집계/로그용, 선택) */
        @Singular("scope")
        private final List<String> scopes;
    }

    @Getter @Setter
    class FetchReport {
        private int rows;                              // 전달한 총 레코드 수
        private Set<String> triedScopes;               // 실제 시도한 범위(정규화)
        private Map<String,String> errors;             // scope → 에러코드/메시지
    }
}
