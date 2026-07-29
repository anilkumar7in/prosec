package com.prosec.saas.service;

import com.prosec.saas.proto.ContainerInventoryResponse;
import com.prosec.saas.proto.ContainerWorkload;
import com.prosec.saas.proto.ListContainerInventoryRequest;
import com.prosec.saas.proto.UpsertContainerInventoryRequest;
import com.prosec.saas.repository.ContainerWorkloadRepository;
import org.springframework.stereotype.Service;

@Service
public class ContainerInventoryService {

    private final ContainerWorkloadRepository containerWorkloadRepository;
    private final TenantService tenantService;
    private final SiteRegistryService siteRegistryService;
    private final EventLogService eventLogService;
    private final ClockService clockService;

    public ContainerInventoryService(
            ContainerWorkloadRepository containerWorkloadRepository,
            TenantService tenantService,
            SiteRegistryService siteRegistryService,
            EventLogService eventLogService,
            ClockService clockService) {
        this.containerWorkloadRepository = containerWorkloadRepository;
        this.tenantService = tenantService;
        this.siteRegistryService = siteRegistryService;
        this.eventLogService = eventLogService;
        this.clockService = clockService;
    }

    public ContainerInventoryResponse upsert(UpsertContainerInventoryRequest request) {
        tenantService.requireActive(request.getTenantId());
        siteRegistryService.requireSite(request.getTenantId(), request.getSiteId());

        ContainerInventoryResponse.Builder response = ContainerInventoryResponse.newBuilder();
        for (ContainerWorkload workload : request.getContainersList()) {
            String workloadId = workload.getId().isBlank()
                    ? stableContainerId(request.getTenantId(), request.getSiteId(), workload)
                    : workload.getId();
            ContainerWorkload saved = workload.toBuilder()
                    .setId(workloadId)
                    .setTenantId(request.getTenantId())
                    .setSiteId(request.getSiteId())
                    .setObservedAtEpochMs(clockService.nowEpochMs())
                    .build();
            containerWorkloadRepository.save(saved);
            response.addContainers(saved);
        }
        eventLogService.record(
                request.getTenantId(), "inventory.upserted", request.getSiteId(), request.getSiteId(),
                "Published " + request.getContainersCount() + " container workload(s).");
        return response.build();
    }

    public ContainerInventoryResponse list(ListContainerInventoryRequest request) {
        tenantService.requireActive(request.getTenantId());
        ContainerInventoryResponse.Builder response = ContainerInventoryResponse.newBuilder();
        containerWorkloadRepository
                .list(request.getTenantId(), request.getSiteId(), request.getNamespace())
                .forEach(response::addContainers);
        return response.build();
    }

    private String stableContainerId(String tenantId, String siteId, ContainerWorkload workload) {
        String raw = String.join("|",
                tenantId,
                siteId,
                workload.getClusterName(),
                workload.getNamespace(),
                workload.getPodName(),
                workload.getContainerName(),
                workload.getImageDigest());
        return "container_" + Integer.toUnsignedString(raw.hashCode(), 16);
    }
}
