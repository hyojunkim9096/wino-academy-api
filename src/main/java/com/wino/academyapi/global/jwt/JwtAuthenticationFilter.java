// src/main/java/com/wino/academyapi/global/jwt/JwtAuthenticationFilter.java
package com.wino.academyapi.global.jwt;

import com.wino.academyapi.domain.auth.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final SessionService sessionService;

    private static final Set<String> PUBLIC_PREFIXES =
            new HashSet<>(Arrays.asList(
                    "/api/auth/login",
                    "/api/auth/password-reset",
                    "/api/admin/register",
                    "/api/admin/signUp"
            ));

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            final String uri = req.getRequestURI();

            if (isPublic(req.getMethod(), uri)) {
                chain.doFilter(req, res);
                return;
            }

            final String token = jwtProvider.resolveToken(req.getHeader(JwtProvider.AUTHORIZATION_HEADER));
            if (token != null && jwtProvider.validateToken(token)) {
                Claims claims = jwtProvider.getClaims(token);
                String sid = claims.get(JwtProvider.CLAIM_SID, String.class);

                if (sid != null) sessionService.requireActive(sid);

                String role = claims.get(JwtProvider.AUTHORIZATION_KEY, String.class);
                List<SimpleGrantedAuthority> authorities =
                        (role == null || role.trim().isEmpty())
                                ? Collections.emptyList()
                                : Collections.singletonList(new SimpleGrantedAuthority(role.trim().toUpperCase()));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            chain.doFilter(req, res);

        } catch (IllegalStateException ex) {
            writeJson(res, 423, "LOCKED_OR_INVALID_STATE", ex.getMessage());
        } catch (Exception ex) {
            writeJson(res, 401, "UNAUTHORIZED", "인증에 실패했습니다.");
        }
    }

    private boolean isPublic(String method, String uri) {
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        if (uri == null) return false;
        if (uri.startsWith("/api/auth/password-reset")) return true;
        for (String p : PUBLIC_PREFIXES) if (uri.startsWith(p)) return true;
        return false;
    }

    private void writeJson(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        String body = "{\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}";
        res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
