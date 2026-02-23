package com.example.apiservice;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ApiController {

    private final ProcessingService processingService;
    private final ZooKeeperRegistration registration;
    private final Timer requestTimer;

    public ApiController(ProcessingService processingService,
                         ZooKeeperRegistration registration,
                         MeterRegistry meterRegistry) {
        this.processingService = processingService;
        this.registration = registration;
        this.requestTimer = Timer.builder("api.request.duration")
                .tag("instance_id", String.valueOf(registration.getInstanceId()))
                .register(meterRegistry);
    }

    @GetMapping("/api/process")
    public ResponseEntity<ApiResponse> process(
            @RequestHeader("X-User-Id") String userId) {
        return requestTimer.record(() -> {
            long start = System.currentTimeMillis();
            ProcessingService.UserResult result = processingService.process(userId);
            long elapsed = System.currentTimeMillis() - start;

            return ResponseEntity.ok(new ApiResponse(
                    result.userId(),
                    elapsed,
                    UUID.randomUUID().toString(),
                    registration.getInstanceId()));
        });
    }

    public record ApiResponse(String userId, long processingTimeMs,
                               String correlationId, int instanceId) {}
}
