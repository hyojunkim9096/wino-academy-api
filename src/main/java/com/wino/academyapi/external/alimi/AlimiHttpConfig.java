// src/main/java/com/wino/academyapi/external/alimi/AlimiHttpConfig.java
package com.wino.academyapi.external.alimi;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 학교알리미(SchoolInfo) HTTP 설정
 *
 * 핵심:
 * - 이제 클라이언트가 항상 '절대 URL'로 호출하므로 rootUri를 쓰지 않습니다.
 * - 커넥션 풀/타임아웃만 설정한 표준 RestTemplate 만 반환합니다.
 */
@Configuration
@EnableConfigurationProperties(SchoolInfoProperties.class)
@RequiredArgsConstructor
public class AlimiHttpConfig {

    private final SchoolInfoProperties props;

    @Bean(name = "schoolInfoRestTemplate")
    @ConditionalOnProperty(prefix = "app.external.school.schoolinfo", name = "enabled", havingValue = "true")
    public RestTemplate schoolInfoRestTemplate(RestTemplateBuilder builder) {
        // URL 검증: 절대 URL 필수(예: https://www.schoolinfo.go.kr/openApi.do)
        String url = props.getUrl();
        if (!StringUtils.hasText(url) || !url.startsWith("http")) {
            throw new IllegalStateException(
                    "학교알리미(SCHOOLINFO) url이 비어있거나 절대 URL이 아닙니다. (app.external.school.schoolinfo.url)"
            );
        }

        // 커넥션 풀
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(Math.max(1, props.getHttp().getMaxTotal()));
        cm.setDefaultMaxPerRoute(Math.max(1, props.getHttp().getMaxPerRoute()));

        // 타임아웃
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(Math.max(1, props.getTimeout().getConnectMs())))
                .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1, props.getTimeout().getReadMs())))
                .build();

        var httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                .evictExpiredConnections()
                .build();

        var factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        // ⚠ rootUri(베이스 URL)는 지정하지 않습니다!
        return builder
                .requestFactory(() -> factory)
                .build();
    }
}
