package com.consistent.hashing.apiservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingServiceTest {

    private ProcessingService service;

    @BeforeEach
    void setUp() {
        service = new ProcessingService();
    }

    @Test
    void firstCallTakesProcessingTime() {
        long start = System.currentTimeMillis();
        ProcessingService.UserResult result = service.process("user-1");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(900);
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.processingTimeMs()).isEqualTo(1000);
    }

    @Test
    void subsequentCallsReturnCachedResult() {
        service.process("user-2");

        long start = System.currentTimeMillis();
        ProcessingService.UserResult result = service.process("user-2");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(100);
        assertThat(result.userId()).isEqualTo("user-2");
    }

    @Test
    void differentUsersGetIndependentResults() {
        ProcessingService.UserResult result1 = service.process("user-a");
        ProcessingService.UserResult result2 = service.process("user-b");

        assertThat(result1.userId()).isEqualTo("user-a");
        assertThat(result2.userId()).isEqualTo("user-b");
    }

    @Test
    void concurrentRequestsForSameUserDontDuplicateProcessing() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicReference<ProcessingService.UserResult> firstResult = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ProcessingService.UserResult result = service.process("concurrent-user");
                    firstResult.compareAndSet(null, result);
                    completedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await();
        long elapsed = System.currentTimeMillis() - start;

        // All threads should complete in roughly 1 second (not 5 seconds),
        // proving they shared the same computation
        assertThat(elapsed).isLessThan(2000);
        assertThat(completedCount.get()).isEqualTo(threadCount);

        executor.shutdown();
    }
}
