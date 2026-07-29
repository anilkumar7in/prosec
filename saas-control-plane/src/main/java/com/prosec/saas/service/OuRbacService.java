package com.prosec.saas.service;

import com.prosec.saas.proto.AccessDecisionResponse;
import com.prosec.saas.proto.AccessEffect;
import com.prosec.saas.proto.EvaluateAccessRequest;
import com.prosec.saas.proto.GrantObjectAccessRequest;
import com.prosec.saas.proto.ObjectGrant;
import com.prosec.saas.repository.ObjectGrantRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OuRbacService {

    private final ObjectGrantRepository objectGrantRepository;
    private final TenantService tenantService;
    private final GlobalObjectService globalObjectService;
    private final EventLogService eventLogService;

    public OuRbacService(
            ObjectGrantRepository objectGrantRepository,
            TenantService tenantService,
            GlobalObjectService globalObjectService,
            EventLogService eventLogService) {
        this.objectGrantRepository = objectGrantRepository;
        this.tenantService = tenantService;
        this.globalObjectService = globalObjectService;
        this.eventLogService = eventLogService;
    }

    public ObjectGrant grant(GrantObjectAccessRequest request) {
        ObjectGrant grant = request.getGrant();
        tenantService.requireActive(grant.getTenantId());
        globalObjectService.requireObject(grant.getObjectId());
        objectGrantRepository.save(grant);
        eventLogService.record(
                grant.getTenantId(), "grant.created", "admin", grant.getObjectId(),
                "Grant created: users=" + grant.getUserIdsList() + " roles=" + grant.getRolesList()
                        + " actions=" + grant.getActionsList());
        return grant;
    }

    public List<ObjectGrant> listGrants(String tenantId) {
        tenantService.requireActive(tenantId);
        return objectGrantRepository.listByTenant(tenantId);
    }

    public ObjectGrant revoke(String tenantId, String objectId) {
        tenantService.requireActive(tenantId);
        ObjectGrant existing = objectGrantRepository.find(tenantId, objectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No grant exists for object " + objectId + " in tenant " + tenantId));
        objectGrantRepository.delete(tenantId, objectId);
        eventLogService.record(
                tenantId, "grant.revoked", "admin", objectId,
                "Grant revoked.");
        return existing;
    }

    public AccessDecisionResponse evaluate(EvaluateAccessRequest request) {
        tenantService.requireActive(request.getTenantId());
        globalObjectService.requireObject(request.getObjectId());
        ObjectGrant grant = objectGrantRepository
                .find(request.getTenantId(), request.getObjectId())
                .orElse(null);

        AccessDecisionResponse decision = decide(grant, request);
        eventLogService.record(
                request.getTenantId(), "access.evaluated", request.getUserId(), request.getObjectId(),
                "action=" + request.getAction() + " effect="
                        + (decision.getEffect() == AccessEffect.ACCESS_ALLOWED ? "ALLOWED" : "DENIED")
                        + " reason=" + decision.getReason());
        return decision;
    }

    private AccessDecisionResponse decide(ObjectGrant grant, EvaluateAccessRequest request) {
        if (grant == null) {
            return deny("Object is not granted to tenant.");
        }
        boolean userListed = grant.getUserIdsList().contains(request.getUserId());
        boolean roleMatched = request.getRolesList().stream()
                .anyMatch(role -> grant.getRolesList().contains(role));
        if (!userListed && !roleMatched) {
            return deny("Neither the user nor any asserted role is listed on the object grant.");
        }
        if (!grant.getActionsList().contains(request.getAction()) && !grant.getActionsList().contains("*")) {
            return deny("Action is not allowed by the object grant.");
        }
        String basis = userListed ? "user identity" : "role membership";
        return AccessDecisionResponse.newBuilder()
                .setEffect(AccessEffect.ACCESS_ALLOWED)
                .setReason("Access allowed by object user RBAC grant (" + basis + ").")
                .build();
    }

    private AccessDecisionResponse deny(String reason) {
        return AccessDecisionResponse.newBuilder()
                .setEffect(AccessEffect.ACCESS_DENIED)
                .setReason(reason)
                .build();
    }
}
