// src/main/java/com/wino/academyapi/global/security/AuthUtils.java
package com.wino.academyapi.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtils {
    private AuthUtils() {}
    public static String currentUserIdOr(String fallback) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return fallback;
        Object principal = a.getPrincipal();
        String name = null;
        if (principal instanceof String s) name = s;
        if (name == null || name.isBlank()) name = a.getName();
        return (name==null || name.isBlank()) ? fallback : name;
    }
}
