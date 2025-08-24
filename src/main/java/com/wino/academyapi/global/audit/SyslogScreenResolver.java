// src/main/java/com/wino/academyapi/global/audit/SyslogScreenResolver.java
package com.wino.academyapi.global.audit;

import com.wino.academyapi.domain.menu.entity.MenuItem;
import com.wino.academyapi.domain.menu.entity.MenuItem.Audience;   // ADMIN / USER
import com.wino.academyapi.domain.menu.entity.MenuItem.MenuType;  // FOLDER / SCREEN
import com.wino.academyapi.domain.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 시스템 로그의 request path 를 admin_menu 와 매칭하여
 * 화면 코드(screenCode) / 화면명(screenName)을 해석하는 컴포넌트.
 *
 * 매칭 전략 (안정성 + 성능):
 *  1) 1차: DB에서 "해당 경로로 시작하는 메뉴" 상위 1건을 바로 조회
 *      - MenuItemRepository.findAdminScreenTop1ForPath(ADMIN, SCREEN, appPath, PageRequest.of(0,1))
 *      - 길이 내림차순 정렬이므로 최장 prefix 1건을 곧바로 획득
 *  2) 2차: DB 결과가 없으면 메모리 캐시(ADMIN+SCREEN+enabled+path!=null)에서
 *      앱단 prefix 리스트로 수동 매칭 (30초 캐시)
 *
 * 정규화 규칙:
 *  - /api(/vN)? 접두 제거 → '/admin/**' 로 환산
 *  - 트레일링 슬래시 제거 ("/admin/codes/" → "/admin/codes")
 *
 * 결과 규칙:
 *  - screenCode = component_key(있으면) / 없으면 path
 *  - screenName = name
 *
 * 캐시:
 *  - 메뉴 전체 후보: 30초 캐시
 *  - 경로별 해석 결과: 30초 캐시(부정 캐시 포함, 동일 경로 반복 비용 축소)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyslogScreenResolver {

    private final MenuItemRepository menuRepo;

    /** 캐시 TTL(ms) — 메뉴 목록 및 경로 해석 결과에 동일 TTL 적용 */
    private static final long CACHE_TTL_MS = 30_000L;

    /** 메뉴 전체 후보 캐시(ADMIN+SCREEN+enabled+path!=null) */
    private final AtomicReference<MenuListCached> menuListCache =
            new AtomicReference<>(new MenuListCached(List.of(), 0L));

    /** 경로별 해석 결과 캐시: appPath → Resolved (부정 캐시 포함) */
    private final Map<String, PathCached> pathCache = new ConcurrentHashMap<>();

    /** 외부 제공 DTO(해석 결과) */
    public record Resolved(String code, String name) {}

    /** 내부 캐시 컨테이너들 */
    private record MenuListCached(List<MenuItem> items, long loadedAt) {}
    private record PathCached(Resolved value, long loadedAt) {}

    /**
     * 원본 URI(예: "/api/admin/codes/123?x=1")를 받아
     * 화면코드/화면명을 해석해 Optional로 반환.
     */
    @Transactional(readOnly = true)
    public Optional<Resolved> resolveFromApiPath(String requestPath) {
        // 0) 입력값 검증
        if (requestPath == null || requestPath.isBlank()) return Optional.empty();

        // 1) 쿼리스트링 제거 ("/api/admin/codes?x=1" → "/api/admin/codes")
        String noQuery = requestPath.split("\\?", 2)[0];

        // 2) '/api' 또는 '/api/vN' 접두 제거 → 앱 내부 경로로 변환 ("/admin/**")
        String appPath = normalizeToAppPath(noQuery);
        if (appPath == null || appPath.isBlank()) return Optional.empty();

        // 3) 경로 캐시 조회(부정 캐시 포함)
        Resolved cached = getFromPathCache(appPath);
        if (cached != null) return Optional.ofNullable(cached);

        // 4) 1차: DB에서 "최장 prefix 상위 1건" 바로 조회
        try {
            var top1 = menuRepo.findAdminScreenTop1ForPath(
                    Audience.ADMIN, MenuType.SCREEN, appPath, PageRequest.of(0, 1)
            );
            if (!top1.isEmpty()) {
                Resolved r = toResolved(top1.get(0));
                putToPathCache(appPath, r);
                return Optional.of(r);
            }
        } catch (Exception e) {
            // DB 조회 실패는 매핑 실패로 간주하지 않고, 2차(메모리)로 폴백
            log.debug("[SyslogScreenResolver] DB top1 prefix query failed: {}", e.toString());
        }

        // 5) 2차: 메모리 캐시(전체 후보) + 앱단 prefix 리스트로 수동 매칭
        try {
            List<String> prefixes = buildPrefixes(appPath); // '/admin/a/b' → ['/admin/a/b','/admin/a','/admin']
            MenuItem matched = findMatchFromCachedList(prefixes);
            if (matched != null) {
                Resolved r = toResolved(matched);
                putToPathCache(appPath, r);
                return Optional.of(r);
            }
        } catch (Exception e) {
            log.debug("[SyslogScreenResolver] in-memory prefix matching failed: {}", e.toString());
        }

        // 6) 매칭 실패 → 부정 캐싱(30초간 재시도 억제)
        putToPathCache(appPath, null);
        return Optional.empty();
    }

    // ───────────────────────── 내부 유틸/캐시 ─────────────────────────

    /** 원본 요청 URI를 '/admin/..' 형태의 앱 경로로 정규화 */
    private static String normalizeToAppPath(String requestUri) {
        if (requestUri == null) return null;

        // '/api' 또는 '/api/vN' 접두 제거
        // 예) /api/admin/codes → /admin/codes
        // 예) /api/v1/admin/users → /admin/users
        String appPath = requestUri.replaceFirst("^/api(?:/v\\d+)?", "");

        // 앞쪽 슬래시 보정
        if (appPath.isBlank()) return null;
        if (!appPath.startsWith("/")) appPath = "/" + appPath;

        // 트레일링 슬래시 제거
        return trimTrailingSlash(appPath);
    }

    /** 메뉴 엔티티 → 화면 해석 결과 (component_key 우선, 없으면 path) */
    private static Resolved toResolved(MenuItem m) {
        String code = (m.getComponentKey() != null && !m.getComponentKey().isBlank())
                ? m.getComponentKey()
                : m.getPath();
        String name = m.getName();
        return new Resolved(code, name);
    }

    /** 경로 캐시에서 조회(30초 TTL). null 저장된 경우도 부정 캐시로 인정하여 그대로 반환(null 가능). */
    private Resolved getFromPathCache(String appPath) {
        PathCached pc = pathCache.get(appPath);
        if (pc == null) return null;
        long now = System.currentTimeMillis();
        if (now - pc.loadedAt > CACHE_TTL_MS) {
            // TTL 초과 → 캐시 제거
            pathCache.remove(appPath);
            return null;
        }
        return pc.value; // null 가능(부정 캐시)
    }

    /** 경로 캐시에 저장(30초 TTL). value == null 이면 부정 캐시로 기록 */
    private void putToPathCache(String appPath, Resolved value) {
        pathCache.put(appPath, new PathCached(value, System.currentTimeMillis()));
    }

    /** ADMIN + SCREEN + enabled + path!=null 목록(30초 캐시) */
    private List<MenuItem> cachedMenus() {
        long now = Instant.now().toEpochMilli();
        MenuListCached cur = menuListCache.get();
        if (now - cur.loadedAt < CACHE_TTL_MS && !cur.items.isEmpty()) {
            return cur.items;
        }
        List<MenuItem> list = menuRepo.findAdminScreenWithPathEnabled(Audience.ADMIN, MenuType.SCREEN);
        menuListCache.set(new MenuListCached(list, now));
        return list;
    }

    /** 수동 매칭: 후보 prefix 목록에서 일치 항목을 찾는다 (가장 긴 경로 먼저 검사). */
    private MenuItem findMatchFromCachedList(List<String> candidates) {
        List<MenuItem> menus = cachedMenus();
        for (String c : candidates) {
            for (MenuItem m : menus) {
                String p = trimTrailingSlash(Objects.toString(m.getPath(), ""));
                if (!p.isEmpty() && c.equalsIgnoreCase(p)) {
                    return m;
                }
            }
        }
        return null;
    }

    /** '/admin/a/b/c' → ['/admin/a/b/c','/admin/a/b','/admin/a','/admin'] */
    private static List<String> buildPrefixes(String path) {
        String p = trimTrailingSlash(path);
        String[] parts = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append("/").append(part);
        }
        String cur = sb.toString();

        List<String> out = new ArrayList<>();
        while (cur.length() > 0) {
            out.add(cur);
            int idx = cur.lastIndexOf('/');
            if (idx <= 0) break;
            cur = cur.substring(0, idx);
        }
        if (!out.contains("/admin")) out.add("/admin");
        return out;
    }

    /** 트레일링 슬래시 제거 (루트 "/"는 유지) */
    private static String trimTrailingSlash(String s) {
        if (s == null) return null;
        if (s.length() > 1 && s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }

    // ───────────────────────── (옵션) 운영 중 수동 캐시 비우기 지원 ─────────────────────────
    /** 외부에서 캐시를 비우고 싶을 때 호출 (예: 메뉴가 대거 변경된 직후) */
    public void evictCaches() {
        menuListCache.set(new MenuListCached(List.of(), 0L));
        pathCache.clear();
        log.debug("[SyslogScreenResolver] caches evicted");
    }
}
