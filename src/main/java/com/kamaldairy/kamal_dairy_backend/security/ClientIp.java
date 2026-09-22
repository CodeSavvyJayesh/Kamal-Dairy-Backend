package com.kamaldairy.kamal_dairy_backend.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's IP for rate limiting. Behind Render's proxy every request
 * arrives from the proxy, so the original client is taken from
 * X-Forwarded-For (first entry). That header can be forged, which is why IP
 * limits are only the outer layer: the per-account limits in AuthGuard do
 * not depend on the IP at all.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 64) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
