package com.prosec.saas.api;

import com.prosec.saas.proto.AccessDecisionResponse;
import com.prosec.saas.proto.AccessEffect;
import com.prosec.saas.security.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AccessDecisionResponse> handleBadRequest(IllegalArgumentException exception) {
        return deny(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<AccessDecisionResponse> handleUnauthorized(UnauthorizedException exception) {
        return deny(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    private ResponseEntity<AccessDecisionResponse> deny(HttpStatus status, String reason) {
        AccessDecisionResponse response = AccessDecisionResponse.newBuilder()
                .setEffect(AccessEffect.ACCESS_DENIED)
                .setReason(reason == null ? "Request rejected." : reason)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
