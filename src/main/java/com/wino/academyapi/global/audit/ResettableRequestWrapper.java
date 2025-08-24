package com.wino.academyapi.global.audit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 요청 본문을 여러 번 읽을 수 있도록 캐싱하는 래퍼 */
public class ResettableRequestWrapper extends HttpServletRequestWrapper {

    private final InputStream cachedInput;

    public ResettableRequestWrapper(HttpServletRequest request, InputStream cachedInput) {
        super(request);
        this.cachedInput = cachedInput;
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream bais = (cachedInput instanceof ByteArrayInputStream)
                ? (ByteArrayInputStream) cachedInput
                : new ByteArrayInputStream(readAll(cachedInput));
        return new ServletInputStream() {
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener readListener) {}
            @Override public int read() { return bais.read(); }
        };
    }

    private byte[] readAll(InputStream is) {
        try { return is.readAllBytes(); }
        catch (IOException e) { return new byte[0]; }
    }
}
