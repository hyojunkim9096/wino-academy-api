// src/main/java/com/wino/academyapi/domain/appsetting/audit/AppSettingAuditAspect.java
package com.wino.academyapi.domain.appsetting.audit;

import com.wino.academyapi.domain.appsetting.entity.AppSetting;
import com.wino.academyapi.domain.appsetting.entity.AppSettingAudit;
import com.wino.academyapi.domain.appsetting.repository.AppSettingAuditRepository;
import com.wino.academyapi.domain.appsetting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AppSettingService의 create/update/deleteAndArchive 호출을 가로채
 * app_setting_audit 테이블에 CREATE/UPDATE/DELETE 기록을 남긴다.
 *
 * - 서비스 로직을 손대지 않아도 동작(안전)
 * - 트랜잭션은 REQUIRES_NEW로 잡아, 원 트랜잭션 롤백과 무관하게 로그 남김
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AppSettingAuditAspect {

    private final AppSettingRepository appSettingRepository;
    private final AppSettingAuditRepository auditRepository;

    /** delete 전, 기존 값을 읽어두기 위해 보관 */
    private final ThreadLocal<AppSetting> preDeleteHolder = new ThreadLocal<>();

    /* -------- CREATE -------- */
    @AfterReturning(
            pointcut = "execution(* com.wino.academyapi.domain.appsetting.service.AppSettingService.create(..))",
            returning = "ret")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterCreate(JoinPoint jp, Object ret) {
        if (!(ret instanceof AppSetting saved)) return;
        // 메서드 파라미터: (AppSetting req, String createdBy)
        Object[] args = jp.getArgs();
        String actor = args.length >= 2 ? String.valueOf(args[1]) : "system";
        saveAudit("CREATE", null, saved.getValue(), saved.getKey(), actor, saved.getRemark());
    }

    /* -------- UPDATE -------- */
    @AfterReturning(
            pointcut = "execution(* com.wino.academyapi.domain.appsetting.service.AppSettingService.update(..))",
            returning = "ret")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterUpdate(JoinPoint jp, Object ret) {
        if (!(ret instanceof AppSetting saved)) return;
        Object[] args = jp.getArgs(); // (Long id, AppSetting req, String modifiedBy)
        String actor = args.length >= 3 ? String.valueOf(args[2]) : "system";

        Long id = (Long) args[0];
        AppSetting before = appSettingRepository.findById(id).orElse(null);
        String beforeVal = (before != null) ? before.getValue() : null; // 업데이트 시점엔 이미 저장되었을 수 있음

        saveAudit("UPDATE", beforeVal, saved.getValue(), saved.getKey(), actor, saved.getRemark());
    }

    /* -------- DELETE -------- */
    @Before("execution(* com.wino.academyapi.domain.appsetting.service.AppSettingService.deleteAndArchive(..))")
    public void beforeDelete(JoinPoint jp) {
        Object[] args = jp.getArgs(); // (Long id, String deletedBy)
        Long id = (Long) args[0];
        AppSetting s = appSettingRepository.findById(id).orElse(null);
        preDeleteHolder.set(s);
    }

    @After("execution(* com.wino.academyapi.domain.appsetting.service.AppSettingService.deleteAndArchive(..))")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterDelete(JoinPoint jp) {
        Object[] args = jp.getArgs();
        String actor = args.length >= 2 ? String.valueOf(args[1]) : "system";
        AppSetting s = preDeleteHolder.get();
        if (s != null) {
            saveAudit("DELETE", s.getValue(), null, s.getKey(), actor, s.getRemark());
        }
        preDeleteHolder.remove();
    }

    /* -------- 공통 저장 -------- */
    private void saveAudit(String type, String before, String after, String key, String actor, String remark) {
        try {
            AppSettingAudit log = new AppSettingAudit();
            log.setChangeType(type);
            log.setKey(key);
            log.setValueBefore(before);
            log.setValueAfter(after);
            log.setChangedBy(actor);
            log.setRemark(remark);
            log.setChangedAt(LocalDateTime.now());
            auditRepository.save(log);
        } catch (Exception e) {
            log.warn("[AppSettingAudit] save failed: {}", e.getMessage(), e);
        }
    }
}
