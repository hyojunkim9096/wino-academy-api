// src/main/java/com/wino/academyapi/global/datasource/LazySwapDataSource.java
package com.wino.academyapi.global.datasource;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class LazySwapDataSource extends AbstractDataSource implements DisposableBean {

    // 유예(그레이스) 파라미터
    private static final long GRACE_CLOSE_TIMEOUT_MS  = 10_000L; // 최대 대기
    private static final long GRACE_POLL_INTERVAL_MS  =   150L;  // 폴링 간격

    private final AtomicReference<DataSource> delegateRef = new AtomicReference<>();

    public LazySwapDataSource(DataSource initial) {
        Objects.requireNonNull(initial, "initial DataSource must not be null");
        delegateRef.set(initial);
        log.info("[DS] LazySwapDataSource initialized with {}", infoOf(initial));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegateRef.get().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegateRef.get().getConnection(username, password);
    }

    /**
     * 새 DataSource(보통 Hikari)를 원자적으로 교체.
     * 교체 후 이전 풀은 '활성 커넥션이 떨어지거나' '유예시간 경과' 시점에 비동기로 close 한다.
     */
    public synchronized void swap(DataSource newDs, String reason) {
        Objects.requireNonNull(newDs, "new DataSource must not be null");
        DataSource old = delegateRef.getAndSet(newDs);
        log.info("[DS] Swapped datasource. reason='{}', new={}, old={}", reason, infoOf(newDs), infoOf(old));
        closeGracefully(old);
    }

    /** 현재 delegate의 사람이 읽기 쉬운 상태 정보 */
    public String currentInfo() {
        return infoOf(delegateRef.get());
    }

    private static String infoOf(DataSource ds) {
        if (ds instanceof HikariDataSource h) {
            try {
                HikariPoolMXBean mx = h.getHikariPoolMXBean();
                String metric = (mx != null)
                        ? String.format(", act=%d, idle=%d", mx.getActiveConnections(), mx.getIdleConnections())
                        : "";
                return "Hikari{pool=" + h.getPoolName() + ", jdbcUrl=" + safe(h.getJdbcUrl()) + metric + "}";
            } catch (Throwable ignore) {
                return "Hikari{pool=" + h.getPoolName() + ", jdbcUrl=" + safe(h.getJdbcUrl()) + "}";
            }
        }
        return ds == null ? "null" : ds.getClass().getSimpleName();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /** 이전 풀을 비동기/점진적으로 종료 — 커밋 중 커넥션을 즉시 닫지 않도록 보호 */
    private static void closeGracefully(DataSource ds) {
        if (ds == null) return;

        if (ds instanceof HikariDataSource h) {
            Thread t = new Thread(() -> {
                try {
                    long deadline = System.currentTimeMillis() + GRACE_CLOSE_TIMEOUT_MS;
                    try {
                        HikariPoolMXBean mx = h.getHikariPoolMXBean();
                        while (mx != null && System.currentTimeMillis() < deadline) {
                            if (mx.getActiveConnections() <= 0) break;
                            try { Thread.sleep(GRACE_POLL_INTERVAL_MS); }
                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        }
                    } catch (Throwable ignore) {
                        // MXBean이 없는 경우 등 — 바로 닫도록 진행
                    }
                } finally {
                    try { h.close(); } catch (Exception e) {
                        log.debug("[DS] old Hikari close ignored: {}", e.toString());
                    }
                }
            }, "hikari-close-" + h.getPoolName());
            t.setDaemon(true);
            t.start();
        } else if (ds instanceof AutoCloseable c) {
            Thread t = new Thread(() -> {
                try { c.close(); } catch (Exception e) {
                    log.debug("[DS] old DS close ignored: {}", e.toString());
                }
            }, "ds-close");
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    public void destroy() {
        // 종료 시에는 유예 없이 즉시 정리
        DataSource ds = delegateRef.get();
        try {
            if (ds instanceof HikariDataSource h) h.close();
            else if (ds instanceof AutoCloseable c) c.close();
        } catch (Exception ignore) {}
    }
}
