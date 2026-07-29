package com.prosec.saas.service;

import org.springframework.stereotype.Component;

@Component
public class ClockService {

    public long nowEpochMs() {
        return System.currentTimeMillis();
    }
}
