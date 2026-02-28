package com.consistent.hashing.common;

public record ServiceInstance(int id, String host, int port) {

    public String address() {
        return host + ":" + port;
    }
}
