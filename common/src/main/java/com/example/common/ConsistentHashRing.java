package com.example.common;

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
        int id = Integer.parseInt(key);
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }
            int hash = hash(id);
            Map.Entry<Integer, T> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    private int hash(int id) {
        return id % ring.size();
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
