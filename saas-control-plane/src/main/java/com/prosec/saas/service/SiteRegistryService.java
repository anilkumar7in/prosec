package com.prosec.saas.service;

import com.prosec.saas.proto.ConnectSiteRequest;
import com.prosec.saas.proto.Site;
import com.prosec.saas.proto.SiteConnectionState;
import com.prosec.saas.proto.SiteCredential;
import com.prosec.saas.proto.SiteResponse;
import com.prosec.saas.repository.SiteRepository;
import org.springframework.stereotype.Service;

@Service
public class SiteRegistryService {

    private final SiteRepository siteRepository;
    private final TenantService tenantService;
    private final SiteCredentialService siteCredentialService;
    private final EventLogService eventLogService;
    private final IdFactory idFactory;
    private final ClockService clockService;

    public SiteRegistryService(
            SiteRepository siteRepository,
            TenantService tenantService,
            SiteCredentialService siteCredentialService,
            EventLogService eventLogService,
            IdFactory idFactory,
            ClockService clockService) {
        this.siteRepository = siteRepository;
        this.tenantService = tenantService;
        this.siteCredentialService = siteCredentialService;
        this.eventLogService = eventLogService;
        this.idFactory = idFactory;
        this.clockService = clockService;
    }

    public SiteResponse connect(ConnectSiteRequest request) {
        tenantService.requireActive(request.getTenantId());
        Site site = Site.newBuilder()
                .setId(idFactory.newId("site"))
                .setTenantId(request.getTenantId())
                .setDisplayName(request.getDisplayName())
                .setRegion(request.getRegion())
                .setConnectionState(SiteConnectionState.SITE_CONNECTED)
                .setLastSeenEpochMs(clockService.nowEpochMs())
                .build();
        siteRepository.save(site);
        SiteCredential credential = siteCredentialService.issue(site.getTenantId(), site.getId());
        eventLogService.record(
                site.getTenantId(), "site.connected", "admin", site.getId(),
                "Site connected: " + site.getDisplayName() + " (" + site.getRegion() + ")");
        return SiteResponse.newBuilder()
                .setSite(site)
                .setCredential(credential)
                .build();
    }

    public SiteResponse rotateCredential(String tenantId, String siteId) {
        tenantService.requireActive(tenantId);
        Site site = requireSite(tenantId, siteId);
        SiteCredential credential = siteCredentialService.issue(tenantId, siteId);
        eventLogService.record(
                tenantId, "site.credential_rotated", "admin", siteId,
                "Site credential rotated.");
        return SiteResponse.newBuilder()
                .setSite(site)
                .setCredential(credential)
                .build();
    }

    public Site heartbeat(String tenantId, String siteId) {
        tenantService.requireActive(tenantId);
        Site current = requireSite(tenantId, siteId);
        Site updated = current.toBuilder()
                .setConnectionState(SiteConnectionState.SITE_CONNECTED)
                .setLastSeenEpochMs(clockService.nowEpochMs())
                .build();
        siteRepository.save(updated);
        return updated;
    }

    public Site requireSite(String tenantId, String siteId) {
        return siteRepository.findByTenantAndId(tenantId, siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site is not connected for tenant: " + siteId));
    }
}
