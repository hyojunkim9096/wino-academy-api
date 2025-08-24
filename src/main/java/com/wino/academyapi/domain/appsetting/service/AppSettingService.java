package com.wino.academyapi.domain.appsetting.service;

import com.wino.academyapi.domain.appsetting.entity.AppSetting;
import com.wino.academyapi.domain.appsetting.entity.AppSettingDeleted;
import com.wino.academyapi.domain.appsetting.repository.AppSettingDeletedRepository;
import com.wino.academyapi.domain.appsetting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시스템 설정 조회/변경 서비스
 *
 * 조회 우선순위:
 *   DB(key@activeProfile) → DB(key) → yml(Environment) → null
 *
 * 기능:
 *   - 캐시: key 기준
 *   - CRUD(create, update, delete+archive)
 *   - 숫자/불리언 변환 유틸
 *   - getProfileAware / getFirst...(keys...)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository repo;
    private final AppSettingDeletedRepository deletedRepo;
    private final Environment env;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /* ================= 프로필 유틸 ================= */

    /** 활성 프로필 탐색 (없으면 dev). local/development는 dev로 간주 */
    private String activeProfile() {
        try {
            String p = null;
            String[] act = env.getActiveProfiles();
            if (act != null && act.length > 0) {
                p = act[0];
            } else {
                p = env.getProperty("spring.profiles.active");
            }
            if (StringUtils.hasText(p)) {
                String v = p.trim().toLowerCase();
                if ("local".equals(v) || "development".equals(v)) return "dev"; // ✅ 보정
                return p;
            }
        } catch (Exception ignored) {}
        return "dev";
    }

    /** 캐시 무효화 (key와 key@profile 둘 다 제거) */
    public void refresh(String key) {
        cache.remove(key);
        cache.remove(key + "@" + activeProfile());
    }

    /* ================= 조회 ================= */

    /** 문자열 조회 (우선순위: DB@profile → DB → yml) */
    @Transactional(readOnly = true)
    public String get(String key) {
        if (!StringUtils.hasText(key)) return null;

        String hit = cache.get(key);
        if (hit != null) return hit;

        String profile = activeProfile();
        String profiledKey = key + "@" + profile;

        // 1) DB: key@profile
        Optional<AppSetting> s = repo.findByKey(profiledKey);
        if (s.isPresent()) {
            String v = s.get().getValue();
            cache.put(key, v);
            return v;
        }

        // 2) DB: key
        s = repo.findByKey(key);
        if (s.isPresent()) {
            String v = s.get().getValue();
            cache.put(key, v);
            return v;
        }

        // 3) yml
        String v = env.getProperty(key);
        if (v != null) {
            cache.put(key, v);
            return v;
        }
        return null;
    }

    /** 프로필 인지 + 기본값 지원 */
    @Transactional(readOnly = true)
    public String getProfileAware(String key, String defaultValue) {
        String v = get(key);
        if (!StringUtils.hasText(v)) return defaultValue;
        return v;
    }

    /* ================= 숫자/불리언 유틸 ================= */

    public Integer getInt(String key, Integer defaultValue) {
        String v = get(key);
        if (!StringUtils.hasText(v)) return defaultValue;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) {
            log.warn("Invalid int for key={} value='{}' → default={}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public Long getLong(String key, Long defaultValue) {
        String v = get(key);
        if (!StringUtils.hasText(v)) return defaultValue;
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException e) {
            log.warn("Invalid long for key={} value='{}' → default={}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        String v = get(key);
        if (!StringUtils.hasText(v)) return defaultValue;
        String t = v.trim().toLowerCase();
        if ("true".equals(t) || "1".equals(t) || "y".equals(t) || "yes".equals(t)) return true;
        if ("false".equals(t) || "0".equals(t) || "n".equals(t) || "no".equals(t)) return false;
        log.warn("Invalid boolean for key={} value='{}' → default={}", key, v, defaultValue);
        return defaultValue;
    }

    /* ===== 여러 후보 키 중 '첫 매칭' 반환 ===== */

    @Transactional(readOnly = true)
    public String getFirstProfileAware(String[] keys, String defaultValue) {
        if (keys == null || keys.length == 0) return defaultValue;
        for (String k : keys) {
            String v = get(k);
            if (StringUtils.hasText(v)) return v;
        }
        return defaultValue;
    }

    @Transactional(readOnly = true)
    public Integer getFirstIntProfileAware(String[] keys, Integer defaultValue) {
        if (keys == null || keys.length == 0) return defaultValue;
        for (String k : keys) {
            Integer v = getInt(k, null);
            if (v != null) return v;
        }
        return defaultValue;
    }

    @Transactional(readOnly = true)
    public Long getFirstLongProfileAware(String[] keys, Long defaultValue) {
        if (keys == null || keys.length == 0) return defaultValue;
        for (String k : keys) {
            Long v = getLong(k, null);
            if (v != null) return v;
        }
        return defaultValue;
    }

    @Transactional(readOnly = true)
    public Boolean getFirstBooleanProfileAware(String[] keys, Boolean defaultValue) {
        if (keys == null || keys.length == 0) return defaultValue;
        for (String k : keys) {
            Boolean v = getBoolean(k, null);
            if (v != null) return v;
        }
        return defaultValue;
    }

    /* ================= 목록/CRUD ================= */

    @Transactional(readOnly = true)
    public List<AppSetting> list() { return repo.findAll(); }

    /** 생성 (키 중복 방지) */
    @Transactional
    public AppSetting create(AppSetting req, String createdBy) {
        String key = safeKey(req.getKey());
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("key는 필수입니다.");
        repo.findByKey(key).ifPresent(x -> { throw new IllegalArgumentException("이미 존재하는 key 입니다: " + key); });

        AppSetting saved = repo.save(
                AppSetting.builder()
                        .key(key)
                        .value(req.getValue())
                        .remark(req.getRemark())
                        .build()
        );

        log.info("[AppSetting][CREATE] key={} by={}", key, createdBy);

        refresh(stripProfile(key));
        return saved;
    }

    /** 수정 (키 변경 시 중복 방지) */
    @Transactional
    public AppSetting update(Long id, AppSetting req, String modifiedBy) {
        AppSetting s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        String beforeKey = s.getKey();
        String newKey = safeKey(req.getKey());
        if (!StringUtils.hasText(newKey)) newKey = beforeKey;

        if (!newKey.equals(beforeKey) && repo.findByKey(newKey).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 key 입니다: " + newKey);
        }

        s.setKey(newKey);
        s.setValue(req.getValue());
        s.setRemark(req.getRemark());
        AppSetting saved = repo.save(s);

        log.info("[AppSetting][UPDATE] {} -> {} by={}", beforeKey, newKey, modifiedBy);

        refresh(stripProfile(beforeKey));
        refresh(stripProfile(newKey));
        return saved;
    }

    /** 삭제(+삭제테이블 아카이브: 업서트) */
    @Transactional
    public void deleteAndArchive(Long id, String deletedBy) {
        AppSetting s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("not found: " + id));

        deletedRepo.findTopByKeyOrderByDeletedAtDesc(s.getKey())
                .ifPresentOrElse(existing -> {
                    existing.setSettingId(s.getId());
                    existing.setValue(s.getValue());
                    existing.setRemark(s.getRemark());
                    existing.setDeletedBy(deletedBy);
                    existing.setDeletedAt(LocalDateTime.now());
                    deletedRepo.save(existing);
                }, () -> {
                    AppSettingDeleted d = AppSettingDeleted.builder()
                            .settingId(s.getId())
                            .key(s.getKey())
                            .value(s.getValue())
                            .remark(s.getRemark())
                            .deletedBy(deletedBy)
                            .deletedAt(LocalDateTime.now())
                            .build();
                    deletedRepo.save(d);
                });

        repo.delete(s);

        refresh(stripProfile(s.getKey()));
        log.info("[AppSetting][DELETE] key={} by={}", s.getKey(), deletedBy);
    }

    @Transactional(readOnly = true)
    public List<AppSettingDeleted> listDeleted() {
        return deletedRepo.findAllByOrderByDeletedAtDesc();
    }

    /* ================= 내부 유틸 ================= */

    private String safeKey(String key) {
        return key == null ? null : key.trim();
    }

    /** "abc@dev" → "abc" */
    private String stripProfile(String key) {
        if (!StringUtils.hasText(key)) return key;
        int at = key.indexOf('@');
        return at > -1 ? key.substring(0, at) : key;
    }
}
