package com.prosec.saas.api;

import com.prosec.saas.proto.ConnectSiteRequest;
import com.prosec.saas.proto.RotateSiteCredentialRequest;
import com.prosec.saas.proto.SiteHeartbeatRequest;
import com.prosec.saas.proto.SiteResponse;
import com.prosec.saas.security.SiteIdentity;
import com.prosec.saas.service.SiteCredentialService;
import com.prosec.saas.service.SiteRegistryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/x-protobuf")
public class SiteController {

    private final SiteRegistryService siteRegistryService;
    private final SiteCredentialService siteCredentialService;

    public SiteController(SiteRegistryService siteRegistryService, SiteCredentialService siteCredentialService) {
        this.siteRegistryService = siteRegistryService;
        this.siteCredentialService = siteCredentialService;
    }

    @PostMapping(path = "/v1/sites/connect", consumes = "application/x-protobuf")
    public SiteResponse connect(@RequestBody ConnectSiteRequest request) {
        return siteRegistryService.connect(request);
    }

    @PostMapping(path = "/v1/sites/credentials:rotate", consumes = "application/x-protobuf")
    public SiteResponse rotate(@RequestBody RotateSiteCredentialRequest request) {
        return siteRegistryService.rotateCredential(request.getTenantId(), request.getSiteId());
    }

    @PostMapping(path = "/v1/sites/heartbeat", consumes = "application/x-protobuf")
    public SiteResponse heartbeat(
            @RequestBody SiteHeartbeatRequest request,
            @RequestHeader(name = SiteCredentialService.SITE_TOKEN_HEADER, required = false) String siteToken) {
        SiteIdentity identity = siteCredentialService.authenticate(siteToken);
        siteCredentialService.requireMatch(identity, request.getTenantId(), request.getSiteId());
        return SiteResponse.newBuilder()
                .setSite(siteRegistryService.heartbeat(request.getTenantId(), request.getSiteId()))
                .build();
    }
}
