package com.prosec.saas.api;

import com.prosec.saas.proto.AccessDecisionResponse;
import com.prosec.saas.proto.EvaluateAccessRequest;
import com.prosec.saas.proto.GrantObjectAccessRequest;
import com.prosec.saas.proto.ListObjectGrantsRequest;
import com.prosec.saas.proto.ObjectGrantListResponse;
import com.prosec.saas.proto.ObjectGrantResponse;
import com.prosec.saas.proto.RevokeObjectAccessRequest;
import com.prosec.saas.service.OuRbacService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/x-protobuf")
public class OuRbacController {

    private final OuRbacService ouRbacService;

    public OuRbacController(OuRbacService ouRbacService) {
        this.ouRbacService = ouRbacService;
    }

    @PostMapping(path = "/v1/ou-rbac/grants", consumes = "application/x-protobuf")
    public ObjectGrantResponse grant(@RequestBody GrantObjectAccessRequest request) {
        return ObjectGrantResponse.newBuilder()
                .setGrant(ouRbacService.grant(request))
                .build();
    }

    @PostMapping(path = "/v1/ou-rbac/grants:list", consumes = "application/x-protobuf")
    public ObjectGrantListResponse listGrants(@RequestBody ListObjectGrantsRequest request) {
        ObjectGrantListResponse.Builder response = ObjectGrantListResponse.newBuilder();
        ouRbacService.listGrants(request.getTenantId()).forEach(response::addGrants);
        return response.build();
    }

    @PostMapping(path = "/v1/ou-rbac/grants:revoke", consumes = "application/x-protobuf")
    public ObjectGrantResponse revoke(@RequestBody RevokeObjectAccessRequest request) {
        return ObjectGrantResponse.newBuilder()
                .setGrant(ouRbacService.revoke(request.getTenantId(), request.getObjectId()))
                .build();
    }

    @PostMapping(path = "/v1/ou-rbac/decisions", consumes = "application/x-protobuf")
    public AccessDecisionResponse evaluate(@RequestBody EvaluateAccessRequest request) {
        return ouRbacService.evaluate(request);
    }
}
