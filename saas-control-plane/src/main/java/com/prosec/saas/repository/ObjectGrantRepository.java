package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.ObjectGrant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ObjectGrantRepository {

    private final JdbcTemplate jdbc;

    public ObjectGrantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ObjectGrant grant) {
        byte[] payload = grant.toByteArray();
        int updated = jdbc.update(
                "UPDATE object_grants SET payload = ? WHERE tenant_id = ? AND object_id = ?",
                payload, grant.getTenantId(), grant.getObjectId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO object_grants (tenant_id, object_id, payload) VALUES (?, ?, ?)",
                        grant.getTenantId(), grant.getObjectId(), payload);
            } catch (DuplicateKeyException raced) {
                jdbc.update(
                        "UPDATE object_grants SET payload = ? WHERE tenant_id = ? AND object_id = ?",
                        payload, grant.getTenantId(), grant.getObjectId());
            }
        }
    }

    public List<ObjectGrant> listByTenant(String tenantId) {
        return jdbc.query(
                "SELECT payload FROM object_grants WHERE tenant_id = ? ORDER BY object_id",
                (rs, rowNum) -> parse(rs.getBytes("payload")),
                tenantId);
    }

    public boolean delete(String tenantId, String objectId) {
        return jdbc.update(
                "DELETE FROM object_grants WHERE tenant_id = ? AND object_id = ?",
                tenantId, objectId) > 0;
    }

    public Optional<ObjectGrant> find(String tenantId, String objectId) {
        return jdbc.query(
                        "SELECT payload FROM object_grants WHERE tenant_id = ? AND object_id = ?",
                        (rs, rowNum) -> parse(rs.getBytes("payload")),
                        tenantId, objectId)
                .stream()
                .findFirst();
    }

    private static ObjectGrant parse(byte[] payload) {
        try {
            return ObjectGrant.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored object grant payload is not a valid protobuf message.", e);
        }
    }
}
