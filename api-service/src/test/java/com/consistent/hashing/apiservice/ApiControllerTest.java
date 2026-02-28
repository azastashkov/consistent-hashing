package com.consistent.hashing.apiservice;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProcessingService processingService;

    @Mock
    private ZooKeeperRegistration zooKeeperRegistration;

    @BeforeEach
    void setUp() {
        ApiController controller = new ApiController(
                processingService, zooKeeperRegistration, new SimpleMeterRegistry());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void processReturnsCorrectResponseFields() throws Exception {
        when(processingService.process("user-1"))
                .thenReturn(new ProcessingService.UserResult("user-1", 1000));
        when(zooKeeperRegistration.getInstanceId()).thenReturn(42);

        mockMvc.perform(get("/api/process").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.processingTimeMs").isNumber())
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.instanceId").value(42));
    }
}
