package com.example.common;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
        assertThat(ring.getNode("any-key")).isNull();
    }

    @Test
    void singleNodeAlwaysReturned() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");

        for (int i = 0; i < 100; i++) {
            assertThat(ring.getNode("key-" + i)).isEqualTo("node-1");
        }
    }

    @Test
    void deterministicRouting() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");
        ring.addNode("3", "node-3");

        for (int i = 0; i < 100; i++) {
            String key = "user-" + i;
            String first = ring.getNode(key);
            String second = ring.getNode(key);
            assertThat(first).isEqualTo(second);
        }
    }

    @Test
    void minimalRemappingOnAddNode() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");
        ring.addNode("3", "node-3");

        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            before.put(key, ring.getNode(key));
        }

        ring.addNode("4", "node-4");

        int remapped = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            String after = ring.getNode(key);
            if (!before.get(key).equals(after)) {
                remapped++;
                // Keys that moved must have moved to the new node
                assertThat(after).isEqualTo("node-4");
            }
        }

        // Some keys should have remapped, but not all
        assertThat(remapped).isGreaterThan(0).isLessThan(1000);
    }

    @Test
    void minimalRemappingOnRemoveNode() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");
        ring.addNode("3", "node-3");
        ring.addNode("4", "node-4");

        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            before.put(key, ring.getNode(key));
        }

        ring.removeNode("4");

        int remapped = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            String after = ring.getNode(key);
            if (!before.get(key).equals(after)) {
                remapped++;
                // The key must have been on node-4 before
                assertThat(before.get(key)).isEqualTo("node-4");
            }
        }

        // Some keys should have remapped, but not all
        assertThat(remapped).isGreaterThan(0).isLessThan(1000);
    }

    @Test
    void wrapAround() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");

        // All keys should resolve to one of the two nodes (including wrap-around case)
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            results.add(ring.getNode("wrap-test-" + i));
        }
        assertThat(results).containsExactlyInAnyOrder("node-1", "node-2");
    }

    @Test
    void removeNodeRestoresRouting() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>();
        ring.addNode("1", "node-1");
        ring.addNode("2", "node-2");

        ring.removeNode("2");

        for (int i = 0; i < 100; i++) {
            assertThat(ring.getNode("key-" + i)).isEqualTo("node-1");
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
                            ring.addNode("dynamic-" + threadId, "dynamic-node-" + threadId);
                        } else if (threadId % 3 == 1) {
                            ring.removeNode("dynamic-" + (threadId - 1));
                        } else {
                            String result = ring.getNode("key-" + i);
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
