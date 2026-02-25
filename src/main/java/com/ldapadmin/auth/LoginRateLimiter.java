package com.ldapadmin.auth;

import com.ldapadmin.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter for the login endpoint.
 *
 * <p>Allows at most {@value #MAX_ATTEMPTS} login attempts per IP address
 * within a rolling {@value #WINDOW_MS}-millisecond window.  Intended to
 * slow brute-force attacks; not a substitute for account lock-out.</p>
 *
 * <p>Note: state is per-instance and not shared across multiple application
 * nodes.  For multi-node deployments, replace with a Redis-backed counter.</p>
 */
@Component
public class LoginRateLimiter {

    private static final int  MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS    = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public void check(HttpServletRequest request) {
        String ip  = resolveIp(request);
        long   now = System.currentTimeMillis();

        Deque<Long> times = attempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            // Drop timestamps outside the rolling window
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= MAX_ATTEMPTS) {
                throw new TooManyRequestsException(
                        "Too many login attempts from this IP, please try again later");
            }
            times.addLast(now);
        }
    }

    private String resolveIp(HttpServletRequest request) {
        // Do NOT trust X-Forwarded-For — it is caller-supplied and trivially spoofed,
        // which would allow an attacker to use a fresh IP bucket on every request.
        // If you deploy behind a reverse proxy, configure Spring Boot's
        // server.forward-headers-strategy=framework so the servlet container resolves
        // the real IP before this filter sees it.
        return request.getRemoteAddr();
    }
}
