// src/main/java/com/wino/academyapi/domain/auth/service/PasswordResetService.java
package com.wino.academyapi.domain.auth.service;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
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

/**
 * 비밀번호 재설정 서비스 (압축본 엔티티/레포 시그니처와 1:1 매칭)
 * - issued_at NOT NULL: 코드 생성 시 issuedAt 반드시 세팅
 * - 비번재설정 성공시: 잠금 해제 + 실패횟수 초기화 (applyPasswordResetPolicyPreserveTemporary)
 * - ⛏ TTL(분)은 AppSetting(DB) → yml → 기본값(10) 순으로 적용: key = "password.reset.ttl.minutes"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AdminUserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AppSettingService settingService;

    /** 현재 비밀번호 재설정 코드 TTL(분)을 설정에서 조회 */
    private int ttlMinutes() {
        // 음수/0 같은 비정상값을 막기 위해 최소 1분 보장
        int v = settingService.getFirstIntProfileAware(new String[]{"password.reset.ttl.minutes"}, 10);
        return Math.max(1, v);
    }

    /** 인증코드 요청 */
    @Transactional
    public void requestCode(String userId) {
        // 1) 사용자 검증
        AdminUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 2) 6자리 숫자 코드 생성 + 해시화
        String code = randomDigits(6);
        String codeHash = sha256Hex(code);

        // 3) 기존 코드 제거(단순 정책)
        codeRepository.deleteByUserId(userId);

        // 4) 코드 저장 (issuedAt 필수, expiresAt = now + TTL)
        LocalDateTime now = LocalDateTime.now();
        int ttl = ttlMinutes();
        PasswordResetCode prc = PasswordResetCode.builder()
                .userId(userId)
                .codeHash(codeHash)
                .issuedAt(now)                      // NOT NULL
                .expiresAt(now.plusMinutes(ttl))    // 설정 기반 TTL
                .used(false)
                .build();

        codeRepository.save(prc);

        // 5) 메일 발송 (본문에 실제 TTL 반영)
        mailService.sendPlain(
                user.getEmail(),
                "[WINO] 비밀번호 재설정 코드",
                "인증 코드는 " + code + " 입니다. " + ttl + "분 내에 입력해 주세요."
        );
    }

    /** 코드 확인 + 비밀번호 변경 */
    @Transactional
    public void resetPassword(String userId, String code, String newRawPassword) {
        // 최신 코드
        PasswordResetCode prc = codeRepository.findTopByUserIdOrderByIssuedAtDesc(userId)
                .orElseThrow(() -> new IllegalArgumentException("재설정 요청이 없습니다."));

        if (prc.isExpired()) {
            throw new IllegalArgumentException("인증 코드가 만료되었습니다.");
        }
        if (prc.isUsed()) {
            throw new IllegalArgumentException("이미 사용된 인증 코드입니다.");
        }
        if (!prc.getCodeHash().equals(sha256Hex(code))) {
            throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");
        }

        AdminUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 비밀번호 변경
        user.setPassword(passwordEncoder.encode(newRawPassword));

        // 비번 재설정 정책 적용: 실패횟수/락 초기화, LOCKED → ACTIVE
        user.applyPasswordResetPolicyPreserveTemporary();
        userRepository.save(user);

        // 인증 코드 사용 처리
        prc.markUsed();
        codeRepository.save(prc); // 명시적 저장
    }

    // ---- 내부 유틸 ----
    private static String randomDigits(int digits) {
        int bound = (int) Math.pow(10, digits);
        int n = ThreadLocalRandom.current().nextInt(bound); // 0 ~ 10^digits-1
        return String.format("%0" + digits + "d", n);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("해시 계산 실패", e);
        }
    }
}
