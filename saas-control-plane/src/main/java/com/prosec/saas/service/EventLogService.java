package com.prosec.saas.service;

import com.prosec.saas.proto.EventListResponse;
import com.prosec.saas.proto.EventRecord;
import com.prosec.saas.proto.ListEventsRequest;
import com.prosec.saas.repository.EventLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Append-only event log. Serves two purposes with one immutable record stream:
 * an audit trail (latest-first queries) and a change feed for sites and UIs
 * (ascending after_seq polling).
 */
@Service
public class EventLogService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final EventLogRepository eventLogRepository;
    private final ClockService clockService;

    public EventLogService(EventLogRepository eventLogRepository, ClockService clockService) {
        this.eventLogRepository = eventLogRepository;
        this.clockService = clockService;
    }

    public void record(String tenantId, String eventType, String actor, String objectId, String detail) {
        EventRecord event = EventRecord.newBuilder()
                .setTenantId(tenantId)
                .setEventType(eventType)
                .setActor(actor)
                .setObjectId(objectId)
                .setDetail(detail)
                .setOccurredAtEpochMs(clockService.nowEpochMs())
                .build();
        eventLogRepository.append(event);
    }

    public EventListResponse list(ListEventsRequest request) {
        int limit = request.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(request.getLimit(), MAX_LIMIT);
        EventListResponse.Builder response = EventListResponse.newBuilder();
        List<EventRecord> events = eventLogRepository
                .list(request.getTenantId(), request.getEventType(), request.getAfterSeq(), limit);
        events.forEach(response::addEvents);
        if (request.getAfterSeq() > 0) {
            // Change-feed mode: the cursor must be the last DELIVERED event so a
            // poller never skips events that fell outside this page. With no new
            // events the cursor stays where it was.
            long cursor = events.isEmpty()
                    ? request.getAfterSeq()
                    : events.get(events.size() - 1).getSeq();
            response.setLatestSeq(cursor);
        } else {
            // Audit view: report the newest sequence in the log.
            response.setLatestSeq(eventLogRepository.latestSeq());
        }
        return response.build();
    }
}
