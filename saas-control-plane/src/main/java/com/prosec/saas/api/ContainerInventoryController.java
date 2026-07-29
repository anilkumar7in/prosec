package com.prosec.saas.api;

import com.prosec.saas.proto.ContainerInventoryResponse;
import com.prosec.saas.proto.ListContainerInventoryRequest;
import com.prosec.saas.proto.UpsertContainerInventoryRequest;
import com.prosec.saas.security.SiteIdentity;
import com.prosec.saas.service.ContainerInventoryService;
import com.prosec.saas.service.SiteCredentialService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/v1/containers", produces = "application/x-protobuf")
public class ContainerInventoryController {

    private final ContainerInventoryService containerInventoryService;
    private final SiteCredentialService siteCredentialService;

    public ContainerInventoryController(
            ContainerInventoryService containerInventoryService,
            SiteCredentialService siteCredentialService) {
        this.containerInventoryService = containerInventoryService;
        this.siteCredentialService = siteCredentialService;
    }

    @PostMapping(path = "/inventory:upsert", consumes = "application/x-protobuf")
    public ContainerInventoryResponse upsert(
            @RequestBody UpsertContainerInventoryRequest request,
            @RequestHeader(name = SiteCredentialService.SITE_TOKEN_HEADER, required = false) String siteToken) {
        SiteIdentity identity = siteCredentialService.authenticate(siteToken);
        siteCredentialService.requireMatch(identity, request.getTenantId(), request.getSiteId());
        return containerInventoryService.upsert(request);
    }

    @PostMapping(path = "/inventory:list", consumes = "application/x-protobuf")
    public ContainerInventoryResponse list(@RequestBody ListContainerInventoryRequest request) {
        return containerInventoryService.list(request);
    }
}
