package com.wino.academyapi.external.alimi;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 학교알리미(SchoolInfo) OpenAPI 설정
 * app.external.school.schoolinfo.* 에 바인딩
 */
@Data
@ConfigurationProperties(prefix = "app.external.school.schoolinfo")
public class SchoolInfoProperties {
    /** 사용 여부 */
    private boolean enabled = true;

    /** 절대 URL (예: https://www.schoolinfo.go.kr/openApi.do) */
    private String url;

    /** OpenAPI Key */
    private String apiKey;

    /** 한 번에 요청할 권장 페이지 크기(서비스 정책에 맞춰 조정) */
    private int pageSize = 1000;

    /** 호출 간 스로틀(ms) */
    private int throttleMs = 400;

    private Timeout timeout = new Timeout();
    private Http http = new Http();

    @Data
    public static class Timeout {
        private int connectMs = 5000;
        private int readMs = 30000;
    }

    @Data
    public static class Http {
        private int maxTotal = 50;
        private int maxPerRoute = 10;
    }
}
