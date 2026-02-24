package com.example.common;

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

        for (int i = 0; i < 16; i++) {
            String key = String.valueOf(i);
            int nodeId = i % 4;
            String node = ring.getNode(key);
            assertThat(node).isEqualTo("node-" + nodeId);
        }
    }

    @Test
    void moduloDistribution() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("0", "node-0");
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");
        ring.addNode("3", "node-3");

        // With 4 nodes at positions 0,1,2,3: key id % 4 maps directly
        assertThat(ring.getNode("0")).isEqualTo("node-0");  // 0 % 4 = 0
        assertThat(ring.getNode("1")).isEqualTo("node-1");  // 1 % 4 = 1
        assertThat(ring.getNode("2")).isEqualTo("node-2");  // 2 % 4 = 2
        assertThat(ring.getNode("3")).isEqualTo("node-3");  // 3 % 4 = 3
        assertThat(ring.getNode("4")).isEqualTo("node-0");  // 4 % 4 = 0
        assertThat(ring.getNode("5")).isEqualTo("node-1");  // 5 % 4 = 1
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
