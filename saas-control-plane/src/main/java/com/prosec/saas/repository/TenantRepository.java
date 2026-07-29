package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.Tenant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private final JdbcTemplate jdbc;

    public TenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Tenant tenant) {
        byte[] payload = tenant.toByteArray();
        String lifecycle = tenant.getLifecycle().name();
        int updated = jdbc.update(
                "UPDATE tenants SET lifecycle = ?, payload = ? WHERE id = ?",
                lifecycle, payload, tenant.getId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO tenants (id, lifecycle, payload) VALUES (?, ?, ?)",
                        tenant.getId(), lifecycle, payload);
            } catch (DuplicateKeyException raced) {
                jdbc.update(
                        "UPDATE tenants SET lifecycle = ?, payload = ? WHERE id = ?",
                        lifecycle, payload, tenant.getId());
            }
        }
    }

    public Optional<Tenant> findById(String tenantId) {
        return jdbc.query(
                        "SELECT payload FROM tenants WHERE id = ?",
                        (rs, rowNum) -> parse(rs.getBytes("payload")),
                        tenantId)
                .stream()
                .findFirst();
    }

    private static Tenant parse(byte[] payload) {
        try {
            return Tenant.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored tenant payload is not a valid protobuf message.", e);
        }
    }
}
