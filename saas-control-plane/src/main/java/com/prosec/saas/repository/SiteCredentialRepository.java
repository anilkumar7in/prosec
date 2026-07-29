package com.prosec.saas.repository;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SiteCredentialRepository {

    public record CredentialRow(String siteId, String tenantId, String tokenHash, long issuedAtEpochMs) {
    }

    private final JdbcTemplate jdbc;

    public SiteCredentialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(CredentialRow row) {
        int updated = jdbc.update(
                "UPDATE site_credentials SET tenant_id = ?, token_hash = ?, issued_at_epoch_ms = ? WHERE site_id = ?",
                row.tenantId(), row.tokenHash(), row.issuedAtEpochMs(), row.siteId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO site_credentials (site_id, tenant_id, token_hash, issued_at_epoch_ms) "
                                + "VALUES (?, ?, ?, ?)",
                        row.siteId(), row.tenantId(), row.tokenHash(), row.issuedAtEpochMs());
            } catch (DuplicateKeyException raced) {
                jdbc.update(
                        "UPDATE site_credentials SET tenant_id = ?, token_hash = ?, issued_at_epoch_ms = ? "
                                + "WHERE site_id = ?",
                        row.tenantId(), row.tokenHash(), row.issuedAtEpochMs(), row.siteId());
            }
        }
    }

    public Optional<CredentialRow> findBySiteId(String siteId) {
        return jdbc.query(
                        "SELECT site_id, tenant_id, token_hash, issued_at_epoch_ms FROM site_credentials "
                                + "WHERE site_id = ?",
                        (rs, rowNum) -> new CredentialRow(
                                rs.getString("site_id"),
                                rs.getString("tenant_id"),
                                rs.getString("token_hash"),
                                rs.getLong("issued_at_epoch_ms")),
                        siteId)
                .stream()
                .findFirst();
    }
}
