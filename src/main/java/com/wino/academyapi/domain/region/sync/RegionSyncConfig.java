// src/main/java/com/wino/academyapi/domain/region/sync/RegionSyncConfig.java
package com.wino.academyapi.domain.region.sync;

import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 지역 동기화 HTTP 클라이언트 설정(호환성 버전)
 *
 * 핵심:
 * - Apache HttpClient5 + 커넥션 풀
 * - 연결/응답 타임아웃 설정
 * - 만료/유휴 커넥션 자동 정리(evict)로 connection reset 완화
 * - 재시도/TTL 설정은 버전 의존성이 있어 제외(원하면 주석 가이드 참고)
 */
@Configuration
@EnableConfigurationProperties(RegionSyncProperties.class)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.region.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RegionSyncConfig {

    private final RegionSyncProperties props;

    /** Region 전용 RestTemplate (bean name 고정) */
    @Bean(name = "regionRestTemplate")
    public RestTemplate regionRestTemplate() {
        // 풀 구성
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(props.getHttpMaxTotal());
        cm.setDefaultMaxPerRoute(props.getHttpMaxPerRoute());

        // 타임아웃
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(props.getHttpConnectTimeoutMs()))
                .setResponseTimeout(Timeout.ofMilliseconds(props.getHttpReadTimeoutMs()))
                .build();

        // HttpClient
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                // ✅ 만료/유휴 커넥션 정리(keep-alive 재사용 중 reset 완화)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                // 게이트웨이/압축 이슈 있으면 유지, 문제 없으면 주석 처리 가능
                .disableContentCompression()
                .build();

        HttpComponentsClientHttpRequestFactory f = new HttpComponentsClientHttpRequestFactory(client);
        return new RestTemplate(f);
    }

    /*
     * [옵션] 재시도/TTL을 쓰고 싶다면:
     *  - 재시도: httpclient5 버전에 따라 DefaultHttpRequestRetryStrategy 가 없을 수 있음.
     *           그 경우 Resilience4j Retry(외부)나 Spring Retry 템플릿으로 감싸는 방법 권장.
     *  - TTL: PoolingHttpClientConnectionManagerBuilder 를 사용하거나
     *         사용중인 버전에 맞는 팩토리 메서드로 설정해야 함.
     */
}
