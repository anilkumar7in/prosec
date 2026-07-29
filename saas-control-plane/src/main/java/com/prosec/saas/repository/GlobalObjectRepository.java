package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.GlobalObject;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GlobalObjectRepository {

    private final JdbcTemplate jdbc;

    public GlobalObjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(GlobalObject object) {
        byte[] payload = object.toByteArray();
        int updated = jdbc.update(
                "UPDATE global_objects SET payload = ? WHERE id = ?",
                payload, object.getId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO global_objects (id, payload) VALUES (?, ?)",
                        object.getId(), payload);
            } catch (DuplicateKeyException raced) {
                jdbc.update(
                        "UPDATE global_objects SET payload = ? WHERE id = ?",
                        payload, object.getId());
            }
        }
    }

    public Optional<GlobalObject> findById(String objectId) {
        return jdbc.query(
                        "SELECT payload FROM global_objects WHERE id = ?",
                        (rs, rowNum) -> parse(rs.getBytes("payload")),
                        objectId)
                .stream()
                .findFirst();
    }

    public boolean exists(String objectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM global_objects WHERE id = ?",
                Integer.class,
                objectId);
        return count != null && count > 0;
    }

    private static GlobalObject parse(byte[] payload) {
        try {
            return GlobalObject.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored global object payload is not a valid protobuf message.", e);
        }
    }
}
