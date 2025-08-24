// api/src/main/java/com/wino/academyapi/global/jwt/JwtProvider.java
package com.wino.academyapi.global.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 생성/검증 유틸
 * - sid(세션ID) 클레임을 포함하여 DB 세션과 연동
 * - subject = username
 * - AUTHORIZATION_KEY 클레임에 roleName
 */
@Component
public class JwtProvider {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization"; // ★ 요게 필요
    public static final String AUTHORIZATION_KEY = "auth";
    public static final String CLAIM_SID = "sid";
    public static final String CLAIM_UID = "uid";

    @Value("${jwt.secret:${app.jwt.secret-key:${JWT_SECRET_KEY:}}}")
    private String secret;

    @Value("${jwt.expiration-ms:${app.jwt.expiration-ms:3600000}}")
    private long expirationMs;

    private Key key;
    private final SignatureAlgorithm alg = SignatureAlgorithm.HS256;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 시크릿이 설정되지 않았습니다. (jwt.secret 또는 app.jwt.secret-key 또는 JWT_SECRET_KEY)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 토큰 생성 (username + roleName + sid 포함) */
    public String createToken(String username, String roleName, String sessionId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_UID, username);
        claims.put(AUTHORIZATION_KEY, roleName);
        claims.put(CLAIM_SID, sessionId);

        String jwt = Jwts.builder()
                .setSubject(username)
                .addClaims(claims)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, alg)
                .compact();

        return BEARER_PREFIX + jwt;
    }

    /** (레거시) sid 없이 */
    public String createToken(String username, String roleName) {
        return createToken(username, roleName, "no-sid");
    }

    /** "Bearer xxx" 헤더에서 순수 jwt 문자열 추출 */
    public String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String h = authorizationHeader.trim();
        if ((h.startsWith("\"") && h.endsWith("\"")) || (h.startsWith("'") && h.endsWith("'"))) {
            h = h.substring(1, h.length() - 1).trim();
        }
        if (!h.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return h.substring(BEARER_PREFIX.length()).trim();
    }

    /** 서명/만료 검증 */
    public boolean validateToken(String token) {
        try {
            getParser().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Claims 추출 */
    public Claims getClaims(String token) {
        return getParser().parseClaimsJws(token).getBody();
    }

    /** sid 추출(없으면 null) */
    public String getSid(String token) {
        try { return getClaims(token).get(CLAIM_SID, String.class); }
        catch (Exception e) { return null; }
    }

    private JwtParser getParser() {
        return Jwts.parserBuilder().setSigningKey(key).build();
    }
}
