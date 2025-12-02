// src/main/java/com/wino/academyapi/domain/auth/service/PasswordResetService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.member.entity.AdminUser;
import com.wino.academyapi.domain.member.repository.AdminUserRepository;
import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.auth.entity.PasswordResetCode;
import com.wino.academyapi.domain.auth.repository.PasswordResetCodeRepository;
import com.wino.academyapi.global.mail.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AdminUserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AppSettingService appSettingService;

    /** 인증코드 발급 & 메일 전송 */
    @Transactional
    public void requestCode(String userId) {
        AdminUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("등록된 이메일이 없습니다. 관리자에게 문의하세요.");
        }

        // 1. 코드 생성 (6자리 숫자)
        String code = randomDigits(6);
        String hash = sha256Hex(code);

        // 2. 만료 시간 (설정값 or 기본 10분)
        int ttl = appSettingService.getInt("password.reset.code-ttl-minutes", 10);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(ttl);

        // 3. DB 저장
        PasswordResetCode prc = PasswordResetCode.builder()
                .userId(userId)
                .codeHash(hash)
                .issuedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .used(false)
                .build();
        codeRepository.save(prc);

        // 4. 메일 발송
        String subject = "[WINO Academy] 비밀번호 재설정 인증코드";
        String body = "인증코드: " + code + "\n\n" + ttl + "분 내에 입력해주세요.";
        mailService.sendPlain(user.getEmail(), subject, body);
    }

    /** 비밀번호 재설정 실행 */
    @Transactional
    public void resetPassword(String userId, String code, String newRawPassword) {
        // 1. 최신 인증코드 조회
        PasswordResetCode prc = codeRepository.findTopByUserIdOrderByIssuedAtDesc(userId)
                .orElseThrow(() -> new IllegalArgumentException("재설정 요청 내역이 없습니다."));

        // 2. 유효성 검증
        if (prc.isExpired()) {
            throw new IllegalArgumentException("인증 코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (prc.isUsed()) {
            throw new IllegalArgumentException("이미 사용된 인증 코드입니다.");
        }
        if (!prc.getCodeHash().equals(sha256Hex(code))) {
            throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");
        }

        // 3. 사용자 조회 및 비밀번호 변경
        AdminUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (newRawPassword == null || newRawPassword.length() < 6) {
            throw new IllegalArgumentException("새 비밀번호는 6자리 이상이어야 합니다.");
        }

        user.setPassword(passwordEncoder.encode(newRawPassword));

        // 정책 적용: 잠금 해제, 실패 카운트 초기화
        user.applyPasswordResetPolicyPreserveTemporary();
        userRepository.save(user);

        // 4. 코드 사용 처리 (✅ 수정됨)
        prc.markUsed();
        codeRepository.save(prc);

        log.info("Password reset success for user: {}", userId);
    }

    // ---- 내부 유틸 ----
    private static String randomDigits(int digits) {
        int bound = (int) Math.pow(10, digits);
        int n = ThreadLocalRandom.current().nextInt(bound);
        return String.format("%0" + digits + "d", n);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Hash fail", e);
        }
    }
}