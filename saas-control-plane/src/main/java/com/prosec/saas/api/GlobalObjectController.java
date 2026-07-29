package com.prosec.saas.api;

import com.prosec.saas.proto.CreateGlobalObjectRequest;
import com.prosec.saas.proto.GlobalObjectResponse;
import com.prosec.saas.proto.GlobalObjectVersionListResponse;
import com.prosec.saas.proto.UpdateGlobalObjectRequest;
import com.prosec.saas.service.GlobalObjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/x-protobuf")
public class GlobalObjectController {

    private final GlobalObjectService globalObjectService;

    public GlobalObjectController(GlobalObjectService globalObjectService) {
        this.globalObjectService = globalObjectService;
    }

    @PostMapping(path = "/v1/global-objects", consumes = "application/x-protobuf")
    public GlobalObjectResponse create(@RequestBody CreateGlobalObjectRequest request) {
        return GlobalObjectResponse.newBuilder()
                .setObject(globalObjectService.create(request))
                .build();
    }

    @GetMapping(path = "/v1/global-objects/{objectId}")
    public GlobalObjectResponse get(@PathVariable String objectId) {
        return GlobalObjectResponse.newBuilder()
                .setObject(globalObjectService.find(objectId)
                        .orElseThrow(() -> new IllegalArgumentException("Global object not found: " + objectId)))
                .build();
    }

    @PostMapping(path = "/v1/global-objects/{objectId}:update", consumes = "application/x-protobuf")
    public GlobalObjectResponse update(
            @PathVariable String objectId,
            @RequestBody UpdateGlobalObjectRequest request) {
        return GlobalObjectResponse.newBuilder()
                .setObject(globalObjectService.update(objectId, request))
                .build();
    }

    @GetMapping(path = "/v1/global-objects/{objectId}/versions")
    public GlobalObjectVersionListResponse versions(@PathVariable String objectId) {
        GlobalObjectVersionListResponse.Builder response = GlobalObjectVersionListResponse.newBuilder();
        globalObjectService.versions(objectId).forEach(response::addVersions);
        return response.build();
    }
}
