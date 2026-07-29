package com.prosec.saas.api;

import com.prosec.saas.proto.EventListResponse;
import com.prosec.saas.proto.ListEventsRequest;
import com.prosec.saas.service.EventLogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/x-protobuf")
public class EventController {

    private final EventLogService eventLogService;

    public EventController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @PostMapping(path = "/v1/events:list", consumes = "application/x-protobuf")
    public EventListResponse list(@RequestBody ListEventsRequest request) {
        return eventLogService.list(request);
    }
}
