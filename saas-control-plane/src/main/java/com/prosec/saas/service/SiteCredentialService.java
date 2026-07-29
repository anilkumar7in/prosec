package com.prosec.saas.service;

import com.prosec.saas.proto.SiteCredential;
import com.prosec.saas.repository.SiteCredentialRepository;
import com.prosec.saas.repository.SiteCredentialRepository.CredentialRow;
import com.prosec.saas.security.SiteIdentity;
import com.prosec.saas.security.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies per-site credentials. The token has the form
 * "{siteId}.{secret}". Only the SHA-256 hash of the secret is stored, so a
 * database leak does not disclose usable credentials. Tokens are returned to
 * the caller exactly once, at connect or rotation time.
 */
@Service
public class SiteCredentialService {

    public static final String SITE_TOKEN_HEADER = "X-Prosec-Site-Token";

    private final SiteCredentialRepository siteCredentialRepository;
    private final ClockService clockService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SiteCredentialService(SiteCredentialRepository siteCredentialRepository, ClockService clockService) {
        this.siteCredentialRepository = siteCredentialRepository;
        this.clockService = clockService;
    }

    public SiteCredential issue(String tenantId, String siteId) {
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long issuedAt = clockService.nowEpochMs();
        siteCredentialRepository.save(new CredentialRow(siteId, tenantId, sha256Hex(secret), issuedAt));
        return SiteCredential.newBuilder()
                .setSiteId(siteId)
                .setToken(siteId + "." + secret)
                .setIssuedAtEpochMs(issuedAt)
                .build();
    }

    public SiteIdentity authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing site credential token.");
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new UnauthorizedException("Malformed site credential token.");
        }
        String siteId = token.substring(0, separator);
        String secret = token.substring(separator + 1);
        CredentialRow row = siteCredentialRepository.findBySiteId(siteId)
                .orElseThrow(() -> new UnauthorizedException("Unknown site credential."));
        if (!constantTimeEquals(sha256Hex(secret), row.tokenHash())) {
            throw new UnauthorizedException("Invalid site credential.");
        }
        return new SiteIdentity(row.siteId(), row.tenantId());
    }

    public void requireMatch(SiteIdentity identity, String tenantId, String siteId) {
        if (!identity.siteId().equals(siteId) || !identity.tenantId().equals(tenantId)) {
            throw new UnauthorizedException("Site credential does not match the requested tenant or site.");
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable.", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
