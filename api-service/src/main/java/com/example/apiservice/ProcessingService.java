package com.example.apiservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);
    private static final long PROCESSING_TIME_MS = 5000;

    private final ConcurrentHashMap<String, CompletableFuture<UserResult>> cache = new ConcurrentHashMap<>();

    public UserResult process(String userId) {
        CompletableFuture<UserResult> future = cache.computeIfAbsent(userId, id -> {
            log.info("First request for user {}, processing for {}ms", id, PROCESSING_TIME_MS);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(PROCESSING_TIME_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new UserResult(id, PROCESSING_TIME_MS);
            });
        });

        return future.join();
    }

    public record UserResult(String userId, long processingTimeMs) {}
}
