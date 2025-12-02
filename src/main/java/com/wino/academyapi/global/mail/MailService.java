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
 * - 발신자 주소/이름은 AppSetting(DB) -> yml -> 기본값 순으로 해석
 * (DB Key: "mail.sender.address", "mail.sender.name")
 * - 예외는 상위로 던지지 않고 로깅만 수행(비즈니스 로직 중단 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final AppSettingService appSettingService;

    /**
     * ✅ DB (AppSetting)에서 발신자 주소를 가져옴
     * - Key: "mail.sender.address" (프로필별 오버라이드 지원)
     */
    private @Nullable String resolveFromAddress() {
        return appSettingService.getProfileAware("mail.sender.address", null);
    }

    /**
     * ✅ DB (AppSetting)에서 발신자 이름을 가져옴
     * - Key: "mail.sender.name"
     */
    private @Nullable String resolveFromName() {
        return appSettingService.getProfileAware("mail.sender.name", null);
    }

    public void sendPlain(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();

            String fromAddress = resolveFromAddress();
            String fromName = resolveFromName();

            // 1. 발신 주소 설정 (DB 설정값 우선 사용)
            if (fromAddress != null && !fromAddress.isBlank()) {
                // 발신자 이름이 설정되어 있다면 "이름 <주소>" 형태로 구성
                if (fromName != null && !fromName.isBlank()) {
                    msg.setFrom(String.format("%s <%s>", fromName, fromAddress));
                } else {
                    msg.setFrom(fromAddress);
                }
            }
            // (DB 설정이 없으면 JavaMailSender의 기본값인 spring.mail.username 사용)

            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);

            log.info("Mail sent to {}{}", to, fromAddress != null ? " (from=" + fromAddress + ")" : "");
        } catch (Exception e) {
            // 메일 발송 실패는 비즈니스 로직의 치명적 오류가 아니므로 로그만 남김
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