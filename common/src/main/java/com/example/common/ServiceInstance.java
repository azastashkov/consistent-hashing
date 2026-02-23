package com.example.common;

public record ServiceInstance(int id, String host, int port) {

    public String address() {
        return host + ":" + port;
    }
}
