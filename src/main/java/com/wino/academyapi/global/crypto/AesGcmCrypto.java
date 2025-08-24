// src/main/java/com/wino/academyapi/global/crypto/AesGcmCrypto.java
package com.wino.academyapi.global.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES/GCM 암복호화 유틸
 * - 키 소스: 환경변수/설정 (AES_SECRET_KEY 우선, 없으면 app.crypto.key)
 * - 키 길이: 16바이트(128-bit) 또는 32바이트(256-bit) 허용
 * - 포맷: Base64(iv) + ":" + Base64(ciphertext)
 */
@Component
@RequiredArgsConstructor
public class AesGcmCrypto {

    private final Environment env;
    private static final String KEY_PROP_1 = "AES_SECRET_KEY";
    private static final String KEY_PROP_2 = "app.crypto.key";
    private static final int GCM_TAG_BITS = 128;

    private SecretKey key() {
        String k = env.getProperty(KEY_PROP_1);
        if (k == null || k.isBlank()) k = env.getProperty(KEY_PROP_2);
        if (k == null || k.isBlank())
            throw new IllegalStateException("AES 키가 없습니다. AES_SECRET_KEY 또는 app.crypto.key 를 설정하세요.");

        byte[] kb = k.getBytes(StandardCharsets.UTF_8);
        // 16 또는 32 바이트만 허용 (그 외 길이는 명확히 거부)
        byte[] keyBytes;
        if (kb.length >= 32) {
            keyBytes = new byte[32];
            System.arraycopy(kb, 0, keyBytes, 0, 32);
        } else if (kb.length >= 16) {
            keyBytes = new byte[16];
            System.arraycopy(kb, 0, keyBytes, 0, 16);
        } else {
            throw new IllegalStateException("AES 키는 최소 16바이트(권장: 32바이트)여야 합니다. 현재: " + kb.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), spec);

            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM 암호화 실패", e);
        }
    }

    public String decrypt(String combined) {
        try {
            String[] parts = combined.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("암호문 포맷이 올바르지 않습니다.");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipher = Base64.getDecoder().decode(parts[1]);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), spec);

            byte[] dec = c.doFinal(cipher);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM 복호화 실패", e);
        }
    }
}
