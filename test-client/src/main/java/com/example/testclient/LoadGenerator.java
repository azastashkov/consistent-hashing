package com.example.testclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class LoadGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadGenerator.class);

    @Value("${load-balancer.url}")
    private String loadBalancerUrl;

    @Value("${load.num-users:16}")
    private int numUsers;

    @Value("${load.interval-ms:1000}")
    private long intervalMs;

    @Value("${load.duration-seconds:300}")
    private long durationSeconds;

    @Override
    public void run(String... args) {
        log.info("Starting load generator: users={}, interval={}ms, duration={}s, target={}",
                numUsers, intervalMs, durationSeconds, loadBalancerUrl);

        RestClient restClient = RestClient.create();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(numUsers);

        for (int i = 0; i < numUsers; i++) {
            String userId = String.valueOf(i);
            executor.scheduleAtFixedRate(() -> sendRequest(restClient, userId),
                    i * 50L, // stagger start times
                    intervalMs,
                    TimeUnit.MILLISECONDS);
        }

        try {
            Thread.sleep(durationSeconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Load generation complete, shutting down");
        executor.shutdown();
    }

    private void sendRequest(RestClient restClient, String userId) {
        try {
            String response = restClient.get()
                    .uri(loadBalancerUrl + "/api/process")
                    .header("X-User-Id", userId)
                    .retrieve()
                    .body(String.class);

            log.info("User={} response={}", userId, response);
        } catch (Exception e) {
            log.warn("Request failed for user={}: {}", userId, e.getMessage());
        }
    }
}
