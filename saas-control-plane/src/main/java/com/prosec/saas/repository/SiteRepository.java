package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.Site;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SiteRepository {

    private final JdbcTemplate jdbc;

    public SiteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Site site) {
        byte[] payload = site.toByteArray();
        int updated = jdbc.update(
                "UPDATE sites SET tenant_id = ?, payload = ? WHERE id = ?",
                site.getTenantId(), payload, site.getId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO sites (id, tenant_id, payload) VALUES (?, ?, ?)",
                        site.getId(), site.getTenantId(), payload);
            } catch (DuplicateKeyException raced) {
                jdbc.update(
                        "UPDATE sites SET tenant_id = ?, payload = ? WHERE id = ?",
                        site.getTenantId(), payload, site.getId());
            }
        }
    }

    public Optional<Site> findByTenantAndId(String tenantId, String siteId) {
        return jdbc.query(
                        "SELECT payload FROM sites WHERE id = ? AND tenant_id = ?",
                        (rs, rowNum) -> parse(rs.getBytes("payload")),
                        siteId, tenantId)
                .stream()
                .findFirst();
    }

    private static Site parse(byte[] payload) {
        try {
            return Site.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored site payload is not a valid protobuf message.", e);
        }
    }
}
