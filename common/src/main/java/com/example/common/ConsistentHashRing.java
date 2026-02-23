package com.example.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConsistentHashRing<T> {

    private final TreeMap<Integer, T> ring = new TreeMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void addNode(String nodeKey, T node) {
        int hash = hash(nodeKey);
        lock.writeLock().lock();
        try {
            ring.put(hash, node);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeNode(String nodeKey) {
        int hash = hash(nodeKey);
        lock.writeLock().lock();
        try {
            ring.remove(hash);
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

    public int size() {
        lock.readLock().lock();
        try {
            return ring.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    static int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());
            return ((digest[0] & 0xFF) << 24)
                    | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8)
                    | (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
