package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.GlobalObject;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GlobalObjectVersionRepository {

    private final JdbcTemplate jdbc;

    public GlobalObjectVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Appends one version row. Version history is immutable: a duplicate
     * (object_id, version) means a concurrent update raced this one, and the
     * DuplicateKeyException propagates so the caller's transaction rolls back
     * instead of silently overwriting history.
     */
    public void saveVersion(GlobalObject object) {
        jdbc.update(
                "INSERT INTO global_object_versions (object_id, version, payload) VALUES (?, ?, ?)",
                object.getId(), object.getVersion(), object.toByteArray());
    }

    public List<GlobalObject> listVersions(String objectId) {
        return jdbc.query(
                "SELECT payload FROM global_object_versions WHERE object_id = ? ORDER BY version DESC",
                (rs, rowNum) -> parse(rs.getBytes("payload")),
                objectId);
    }

    private static GlobalObject parse(byte[] payload) {
        try {
            return GlobalObject.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored object version payload is not a valid protobuf message.", e);
        }
    }
}
