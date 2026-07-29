package com.prosec.saas.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdFactory {

    public String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
