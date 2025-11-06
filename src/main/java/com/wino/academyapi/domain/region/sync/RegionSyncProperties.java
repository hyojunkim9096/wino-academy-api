// src/main/java/com/wino/academyapi/domain/region/sync/RegionSyncProperties.java
package com.wino.academyapi.domain.region.sync;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지역(법정동) 동기화 설정 바인딩 클래스.
 *
 * 매핑 prefix: app.region.sync
 *
 * 주요 사용처
 * - DataGoKrRegionApiProvider: apiUrl, apiKey
 * - RegionSyncConfig         : httpMaxTotal, httpMaxPerRoute, httpConnectTimeoutMs, httpReadTimeoutMs
 * - RegionSyncService        : enabled, batchSize, autoDisableMissing, replaceAllOnRefresh(선택)
 *
 * 주의
 * - application.yml의 케밥 표기(api-url 등)는 자동으로 카멜 표기(apiUrl)로 매핑됩니다.
 * - yml에 존재하지만 코드에 없는 키는 Spring이 기본적으로 무시하지만,
 *   경고를 줄이기 위해 source/schedule-*도 함께 정의해 둡니다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.region.sync")
public class RegionSyncProperties {

    /** 동기화 on/off (전체 기능 스위치) */
    private boolean enabled = true;

    // ───────────────── API 소스(DATA_GO_KR) ─────────────────

    /**
     * 사용 소스 구분(문자). 예: "DATA_GO_KR"
     * 현재 구현은 공공데이터포털 API만 사용하지만, 경고 방지를 위해 제공.
     */
    private String source = "DATA_GO_KR";

    /** 공공데이터포털 API URL (예: https://apis.data.go.kr/1741000/StanReginCd/getStanReginCdList) */
    private String apiUrl;

    /** 공공데이터포털 서비스키 */
    private String apiKey;

    /** 버튼 최신화 시 전체 교체 여부(삭제 후 재삽입) — 현재 사용처는 선택적 */
    private boolean replaceAllOnRefresh = true;

    /** 업서트 시, 소스에 없는 기존 코드를 자동 비활성화(use_yn=false) */
    private boolean autoDisableMissing = true;

    /** 저장 배치 크기 (replace/upsert 시 saveAll 버퍼 크기) */
    private int batchSize = 1000;

    // ───────────────── 네트워크/HTTP (RegionSyncConfig와 매칭) ─────────────────

    /** HTTP 커넥션 풀 전체 최대 */
    private int httpMaxTotal = 50;

    /** 라우트 당 최대 */
    private int httpMaxPerRoute = 10;

    /** 연결 타임아웃(ms) */
    private int httpConnectTimeoutMs = 5000;

    /** 응답(read) 타임아웃(ms) */
    private int httpReadTimeoutMs = 15000;

    // ───────────────── 스케줄 (선택) ─────────────────

    /** 스케줄러 사용 여부 (기본 off) */
    private boolean scheduleEnabled = false;

    /** 크론 표현식 (예: "0 30 3 * * *") */
    private String scheduleCron = "0 30 3 * * *";

    /** 스케줄 타임존 */
    private String scheduleZone = "Asia/Seoul";

    // ───────────────── 기타 ─────────────────

    /** 기본 모드 (스케줄 등에서 사용하려면) — upsert | replace */
    private String defaultMode = "upsert";
}
