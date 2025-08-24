// api/src/main/java/com/wino/academyapi/global/mail/MailService.java
package com.wino.academyapi.global.mail;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 간단한 텍스트 메일 발송 서비스
 * - from 주소는 AppSetting(DB) → yml → 기본값 순으로 해석
 *   후보키: "mail.from", "app.mail.from"
 *   값이 비어있으면 JavaMailSender(username) 사용
 * - 예외는 상위로 던지지 않고 로깅만(비밀번호 재설정 흐름 차단 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final AppSettingService settingService;

    /** 설정에서 발신자 주소를 해석 (비어있으면 null 반환 → MailSender 기본값 사용) */
    private @Nullable String resolveFrom() {
        String from = settingService.getFirstProfileAware(
                new String[]{"mail.from", "app.mail.from"},
                "" // 빈 값이면 setFrom 호출하지 않아 MailSender username이 사용됨
        );
        return (from == null || from.isBlank()) ? null : from;
    }

    public void sendPlain(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            String from = resolveFrom();
            if (from != null) {
                msg.setFrom(from);
            }
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Mail sent to {}{}", to, from != null ? " (from=" + from + ")" : "");
        } catch (Exception e) {
            // 메일 실패는 치명적 흐름을 막지 않도록 로깅만
            log.warn("Mail send failed to {} : {}", to, e.toString());
        }
    }

    public void sendPlain(@Nullable String[] toList, String subject, String body) {
        if (toList == null || toList.length == 0) return;
        for (String to : toList) {
            sendPlain(to, subject, body);
        }
    }
}
