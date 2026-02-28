package com.consistent.hashing.common;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConsistentHashRing<T> {

    private final TreeMap<Integer, T> ring = new TreeMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void addNode(String nodeKey, T node) {
        int id = Integer.parseInt(nodeKey);
        lock.writeLock().lock();
        try {
            ring.put(id, node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeNode(String nodeKey) {
        int id = Integer.parseInt(nodeKey);
        lock.writeLock().lock();
        try {
            ring.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public T getNode(String key) {
        int hash = hash(key);
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }
            Map.Entry<Integer, T> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    private int hash(String key) {
        return Math.abs(key.hashCode()) % (ring.isEmpty() ? 1 : ring.size());
    }

    public int size() {
        lock.readLock().lock();
        try {
            return ring.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
