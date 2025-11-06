// src/main/java/com/wino/academyapi/global/audit/AppUserContext.java
package com.wino.academyapi.global.audit;

/**
 * 요청 단위 사용자 컨텍스트(ThreadLocal)
 * - 인터셉터/필터에서 setUserId/setNote 호출
 * - @Transactional 서비스에서 DbSessionVars.setAppVars(AppUserContext.getUserId(), AppUserContext.getNote())로 사용
 * - 반드시 afterCompletion()에서 clear() 호출할 것
 */
public final class AppUserContext {
    private static final ThreadLocal<Long> USER_ID = ThreadLocal.withInitial(() -> 0L);
    private static final ThreadLocal<String> NOTE = new ThreadLocal<>();

    private AppUserContext() {}

    public static void setUserId(Long id) {
        USER_ID.set(id == null ? 0L : id);
    }

    public static Long getUserId() {
        Long v = USER_ID.get();
        return v == null ? 0L : v;
    }

    public static void setNote(String note) {
        NOTE.set(note);
    }

    /** null-safe */
    public static String getNote() {
        String v = NOTE.get();
        return v == null ? "" : v;
    }

    public static void clear() {
        USER_ID.remove();
        NOTE.remove();
    }
}