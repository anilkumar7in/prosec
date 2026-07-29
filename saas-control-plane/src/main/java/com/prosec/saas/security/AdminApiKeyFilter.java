package com.prosec.saas.security;

import com.prosec.saas.proto.AccessDecisionResponse;
import com.prosec.saas.proto.AccessEffect;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects operator endpoints with a static admin API key sent in the
 * X-Prosec-Api-Key header. Site-facing endpoints (heartbeat and inventory
 * upsert) are excluded here because they authenticate with per-site signed
 * credentials verified in their controllers.
 */
@Component
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Prosec-Api-Key";

    private final String adminApiKey;

    public AdminApiKeyFilter(@Value("${prosec.security.admin-api-key}") String adminApiKey) {
        if (adminApiKey == null || adminApiKey.isBlank()) {
            throw new IllegalStateException(
                    "prosec.security.admin-api-key must not be blank; set PROSEC_ADMIN_API_KEY.");
        }
        this.adminApiKey = adminApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/v1/")) {
            return true;
        }
        return path.equals("/v1/sites/heartbeat") || path.equals("/v1/containers/inventory:upsert");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(API_KEY_HEADER);
        if (provided == null || !constantTimeEquals(provided, adminApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/x-protobuf");
            AccessDecisionResponse body = AccessDecisionResponse.newBuilder()
                    .setEffect(AccessEffect.ACCESS_DENIED)
                    .setReason("Missing or invalid admin API key.")
                    .build();
            response.getOutputStream().write(body.toByteArray());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
