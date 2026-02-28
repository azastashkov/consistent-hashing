package com.consistent.hashing.loadbalancer;

import com.consistent.hashing.common.ServiceInstance;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private ServiceRegistry serviceRegistry;

    private MockWebServer mockBackend;

    @BeforeEach
    void setUp() throws Exception {
        mockBackend = new MockWebServer();
        mockBackend.start();

        ProxyController controller = new ProxyController(serviceRegistry, WebClient.builder());
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockBackend.shutdown();
    }

    @Test
    void missingUserIdHeaderReturns400() {
        webTestClient.get().uri("/api/process")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .isEqualTo("Missing X-User-Id header");
    }

    @Test
    void blankUserIdHeaderReturns400() {
        webTestClient.get().uri("/api/process")
                .header("X-User-Id", "  ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .isEqualTo("Missing X-User-Id header");
    }

    @Test
    void noAvailableInstancesReturns503() {
        when(serviceRegistry.resolve("user-1")).thenReturn(null);

        webTestClient.get().uri("/api/process")
                .header("X-User-Id", "user-1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .isEqualTo("No available service instances");
    }

    @Test
    void validRequestRoutesToBackend() throws Exception {
        mockBackend.enqueue(new MockResponse()
                .setBody("{\"result\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        String host = mockBackend.getHostName();
        int port = mockBackend.getPort();
        when(serviceRegistry.resolve("user-1"))
                .thenReturn(new ServiceInstance(7, host, port));

        webTestClient.get().uri("/api/process")
                .header("X-User-Id", "user-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Routed-To", "7")
                .expectBody(String.class)
                .isEqualTo("{\"result\":\"ok\"}");

        RecordedRequest recorded = mockBackend.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/process");
        assertThat(recorded.getHeader("X-User-Id")).isEqualTo("user-1");
    }
}
