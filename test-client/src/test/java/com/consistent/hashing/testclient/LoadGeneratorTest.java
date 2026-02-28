package com.consistent.hashing.testclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "load.num-users=1",
        "load.interval-ms=500",
        "load.duration-seconds=2"
})
class LoadGeneratorTest {

    private static MockWebServer mockServer;

    @Autowired
    private LoadGenerator loadGenerator;

    @BeforeAll
    static void startServer() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("load-balancer.url", () -> "http://localhost:" + mockServer.getPort());
    }

    @Test
    void configurationPropertiesAreBound() {
        assertThat(loadGenerator).isNotNull();
    }

    @Test
    void sendsRequestsWithCorrectUrlAndHeaders() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        }

        Thread runner = new Thread(() -> loadGenerator.run());
        runner.start();

        RecordedRequest request = mockServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/process");
        assertThat(request.getHeader("X-User-Id")).isEqualTo("0");

        runner.join(5000);
    }
}
