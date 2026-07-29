package com.prosec.saas.repository;

import com.google.protobuf.InvalidProtocolBufferException;
import com.prosec.saas.proto.ContainerWorkload;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContainerWorkloadRepository {

    private final JdbcTemplate jdbc;

    public ContainerWorkloadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ContainerWorkload workload) {
        byte[] payload = workload.toByteArray();
        int updated = jdbc.update(
                "UPDATE container_workloads SET site_id = ?, namespace = ?, pod_name = ?, "
                        + "container_name = ?, payload = ? WHERE tenant_id = ? AND workload_id = ?",
                workload.getSiteId(), workload.getNamespace(), workload.getPodName(),
                workload.getContainerName(), payload, workload.getTenantId(), workload.getId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO container_workloads (tenant_id, workload_id, site_id, namespace, "
                                + "pod_name, container_name, payload) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        workload.getTenantId(), workload.getId(), workload.getSiteId(),
                        workload.getNamespace(), workload.getPodName(), workload.getContainerName(), payload);
            } catch (DuplicateKeyException raced) {
                jdbc.update(
                        "UPDATE container_workloads SET site_id = ?, namespace = ?, pod_name = ?, "
                                + "container_name = ?, payload = ? WHERE tenant_id = ? AND workload_id = ?",
                        workload.getSiteId(), workload.getNamespace(), workload.getPodName(),
                        workload.getContainerName(), payload, workload.getTenantId(), workload.getId());
            }
        }
    }

    public List<ContainerWorkload> list(String tenantId, String siteId, String namespace) {
        StringBuilder sql = new StringBuilder("SELECT payload FROM container_workloads WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (!siteId.isBlank()) {
            sql.append(" AND site_id = ?");
            params.add(siteId);
        }
        if (!namespace.isBlank()) {
            sql.append(" AND namespace = ?");
            params.add(namespace);
        }
        sql.append(" ORDER BY namespace, pod_name, container_name");
        return jdbc.query(
                sql.toString(),
                (rs, rowNum) -> parse(rs.getBytes("payload")),
                params.toArray());
    }

    private static ContainerWorkload parse(byte[] payload) {
        try {
            return ContainerWorkload.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Stored container workload payload is not a valid protobuf message.", e);
        }
    }
}
