package com.consistent.hashing.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistentHashRingTest {

    @Test
    void emptyRingReturnsNull() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        assertThat(ring.getNode("0")).isNull();
    }

    @Test
    void singleNodeAlwaysReturned() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");

        for (int i = 0; i < 100; i++) {
            assertThat(ring.getNode(String.valueOf(i))).isEqualTo("node-1");
        }
    }

    @Test
    void deterministicRouting() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("0", "node-0");
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");
        ring.addNode("3", "node-3");

        // Same key always maps to the same node
        for (int i = 0; i < 16; i++) {
            String key = String.valueOf(i);
            String first = ring.getNode(key);
            String second = ring.getNode(key);
            assertThat(first).isEqualTo(second);
        }
    }

    @Test
    void nonNumericKeysWork() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("0", "node-0");
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");

        // Non-numeric keys should resolve without throwing
        assertThat(ring.getNode("user-alice")).isNotNull();
        assertThat(ring.getNode("user-bob")).isNotNull();
        assertThat(ring.getNode("550e8400-e29b-41d4-a716-446655440000")).isNotNull();

        // Same non-numeric key always returns the same node
        String first = ring.getNode("user-alice");
        String second = ring.getNode("user-alice");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void distributionAcrossAllNodes() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("0", "node-0");
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");

        Set<String> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(ring.getNode(String.valueOf(i)));
        }
        assertThat(results).containsExactlyInAnyOrder("node-0", "node-1", "node-2");
    }

    @Test
    void removeNodeRestoresRouting() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("0", "node-0");
        ring.addNode("1", "node-1");

        ring.removeNode("1");

        for (int i = 0; i < 100; i++) {
            assertThat(ring.getNode(String.valueOf(i))).isEqualTo("node-0");
        }
    }

    @Test
    void sizeTracksNodes() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        assertThat(ring.size()).isEqualTo(0);

        ring.addNode("1", "node-1");
        assertThat(ring.size()).isEqualTo(1);

        ring.addNode("2", "node-2");
        assertThat(ring.size()).isEqualTo(2);

        ring.removeNode("1");
        assertThat(ring.size()).isEqualTo(1);
    }

    @Test
    void threadSafety() throws InterruptedException {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");
        ring.addNode("3", "node-3");

        int threadCount = 10;
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        if (threadId % 3 == 0) {
                            ring.addNode(String.valueOf(100 + threadId), "dynamic-node-" + threadId);
                        } else if (threadId % 3 == 1) {
                            ring.removeNode(String.valueOf(100 + threadId - 1));
                        } else {
                            String result = ring.getNode(String.valueOf(i));
                            if (result == null) {
                                failed.set(true);
                            }
                        }
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertThat(failed.get()).isFalse();
    }
}
