package com.consistent.hashing.loadbalancer;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZooKeeperConfig {

    @Bean(initMethod = "start", destroyMethod = "close")
    public CuratorFramework curatorFramework(
            @Value("${zookeeper.connect-string}") String connectString) {
        return CuratorFrameworkFactory.newClient(
                connectString,
                new ExponentialBackoffRetry(1000, 3));
    }
}
