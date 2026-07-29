package com.prosec.saas.service;

import com.prosec.saas.proto.CreateTenantRequest;
import com.prosec.saas.proto.Tenant;
import com.prosec.saas.proto.TenantLifecycle;
import com.prosec.saas.repository.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final EventLogService eventLogService;
    private final IdFactory idFactory;
    private final ClockService clockService;

    public TenantService(
            TenantRepository tenantRepository,
            EventLogService eventLogService,
            IdFactory idFactory,
            ClockService clockService) {
        this.tenantRepository = tenantRepository;
        this.eventLogService = eventLogService;
        this.idFactory = idFactory;
        this.clockService = clockService;
    }

    public Tenant create(CreateTenantRequest request) {
        String id = idFactory.newId("tenant");
        Tenant tenant = Tenant.newBuilder()
                .setId(id)
                .setDisplayName(request.getDisplayName())
                .setLifecycle(TenantLifecycle.TENANT_ACTIVE)
                .setCreatedAtEpochMs(clockService.nowEpochMs())
                .build();
        tenantRepository.save(tenant);
        eventLogService.record(
                id, "tenant.created", "admin", id,
                "Tenant created: " + tenant.getDisplayName());
        return tenant;
    }

    public Optional<Tenant> find(String tenantId) {
        return tenantRepository.findById(tenantId);
    }

    public void requireActive(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getLifecycle() != TenantLifecycle.TENANT_ACTIVE) {
            throw new IllegalArgumentException("Tenant is not active: " + tenantId);
        }
    }
}
