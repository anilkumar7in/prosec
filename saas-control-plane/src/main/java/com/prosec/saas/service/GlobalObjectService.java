package com.prosec.saas.service;

import com.prosec.saas.proto.CreateGlobalObjectRequest;
import com.prosec.saas.proto.GlobalObject;
import com.prosec.saas.proto.UpdateGlobalObjectRequest;
import com.prosec.saas.repository.GlobalObjectRepository;
import com.prosec.saas.repository.GlobalObjectVersionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalObjectService {

    private final GlobalObjectRepository globalObjectRepository;
    private final GlobalObjectVersionRepository globalObjectVersionRepository;
    private final EventLogService eventLogService;
    private final IdFactory idFactory;
    private final ClockService clockService;

    public GlobalObjectService(
            GlobalObjectRepository globalObjectRepository,
            GlobalObjectVersionRepository globalObjectVersionRepository,
            EventLogService eventLogService,
            IdFactory idFactory,
            ClockService clockService) {
        this.globalObjectRepository = globalObjectRepository;
        this.globalObjectVersionRepository = globalObjectVersionRepository;
        this.eventLogService = eventLogService;
        this.idFactory = idFactory;
        this.clockService = clockService;
    }

    public GlobalObject create(CreateGlobalObjectRequest request) {
        long now = clockService.nowEpochMs();
        GlobalObject object = GlobalObject.newBuilder()
                .setId(idFactory.newId("object"))
                .setObjectType(request.getObjectType())
                .setDisplayName(request.getDisplayName())
                .setProtobufPayload(request.getProtobufPayload())
                .setCreatedAtEpochMs(now)
                .setVersion(1)
                .setUpdatedAtEpochMs(now)
                .build();
        globalObjectRepository.save(object);
        globalObjectVersionRepository.saveVersion(object);
        eventLogService.record(
                "", "object.created", "admin", object.getId(),
                "Global object created: " + object.getDisplayName() + " (v1)");
        return object;
    }

    @Transactional
    public GlobalObject update(String objectId, UpdateGlobalObjectRequest request) {
        GlobalObject current = globalObjectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("Global object not found: " + objectId));
        long newVersion = Math.max(current.getVersion(), 1) + 1;
        GlobalObject.Builder builder = current.toBuilder()
                .setVersion(newVersion)
                .setUpdatedAtEpochMs(clockService.nowEpochMs());
        if (!request.getDisplayName().isBlank()) {
            builder.setDisplayName(request.getDisplayName());
        }
        if (!request.getProtobufPayload().isEmpty()) {
            builder.setProtobufPayload(request.getProtobufPayload());
        }
        GlobalObject updated = builder.build();
        try {
            // Version history is append-only: a duplicate (object_id, version)
            // means another update raced this one. The transaction rolls back
            // (head row included) and the caller can retry against the new head.
            globalObjectVersionRepository.saveVersion(updated);
        } catch (DuplicateKeyException raced) {
            throw new IllegalArgumentException(
                    "Concurrent update detected for object " + objectId + "; reload and retry.");
        }
        globalObjectRepository.save(updated);
        eventLogService.record(
                "", "object.updated", "admin", updated.getId(),
                "Global object updated: " + updated.getDisplayName() + " (v" + newVersion + ")");
        return updated;
    }

    public List<GlobalObject> versions(String objectId) {
        requireObject(objectId);
        return globalObjectVersionRepository.listVersions(objectId);
    }

    public Optional<GlobalObject> find(String objectId) {
        return globalObjectRepository.findById(objectId);
    }

    public void requireObject(String objectId) {
        if (!globalObjectRepository.exists(objectId)) {
            throw new IllegalArgumentException("Global object does not exist: " + objectId);
        }
    }
}
