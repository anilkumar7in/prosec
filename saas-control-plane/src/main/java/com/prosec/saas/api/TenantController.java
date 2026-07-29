package com.prosec.saas.api;

import com.prosec.saas.proto.CreateTenantRequest;
import com.prosec.saas.proto.TenantResponse;
import com.prosec.saas.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/tenants", produces = "application/x-protobuf")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping(consumes = "application/x-protobuf")
    public TenantResponse create(@RequestBody CreateTenantRequest request) {
        return TenantResponse.newBuilder()
                .setTenant(tenantService.create(request))
                .build();
    }

    @GetMapping(path = "/{tenantId}")
    public TenantResponse get(@PathVariable String tenantId) {
        return TenantResponse.newBuilder()
                .setTenant(tenantService.find(tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId)))
                .build();
    }
}

