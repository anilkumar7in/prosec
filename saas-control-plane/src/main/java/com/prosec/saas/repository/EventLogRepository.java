package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.EventRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventLogRepository {

    private final JdbcTemplate jdbc;

    public EventLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(EventRecord event) {
        jdbc.update(
                "INSERT INTO event_log (tenant_id, event_type, payload) VALUES (?, ?, ?)",
                event.getTenantId(), event.getEventType(), event.toByteArray());
    }

    public List<EventRecord> list(String tenantId, String eventType, long afterSeq, int limit) {
        StringBuilder sql = new StringBuilder("SELECT seq, payload FROM event_log WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!tenantId.isBlank()) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (!eventType.isBlank()) {
            sql.append(" AND event_type = ?");
            params.add(eventType);
        }
        if (afterSeq > 0) {
            sql.append(" AND seq > ? ORDER BY seq ASC");
            params.add(afterSeq);
        } else {
            sql.append(" ORDER BY seq DESC");
        }
        sql.append(" LIMIT ?");
        params.add(limit);
        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> parse(rs.getBytes("payload")).toBuilder()
                        .setSeq(rs.getLong("seq"))
                        .build(),
                params.toArray());
    }

    public long latestSeq() {
        Long seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) FROM event_log", Long.class);
        return seq == null ? 0L : seq;
    }

    private static EventRecord parse(byte[] payload) {
        try {
            return EventRecord.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored event payload is not a valid protobuf message.", e);
        }
    }
}
