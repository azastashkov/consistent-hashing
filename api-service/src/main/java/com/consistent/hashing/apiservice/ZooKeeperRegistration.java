package com.consistent.hashing.apiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ZooKeeperRegistration implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperRegistration.class);
    private static final String SERVICE_PATH = "/services/api/instance-";

    private final CuratorFramework curator;
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean running;
    private String createdPath;
    private int instanceId;

    public ZooKeeperRegistration(
            CuratorFramework curator,
            @Value("${service.host}") String host,
            @Value("${server.port}") int port) {
        this.curator = curator;
        this.host = host;
        this.port = port;
    }

    @Override
    public void start() {
        try {
            // Ensure parent path exists
            if (curator.checkExists().forPath("/services/api") == null) {
                curator.create().creatingParentsIfNeeded().forPath("/services/api");
            }

            byte[] data = objectMapper.writeValueAsBytes(
                    Map.of("host", host, "port", port));

            createdPath = curator.create()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(SERVICE_PATH, data);

            // Extract sequential integer from path like /services/api/instance-0000000001
            String seqStr = createdPath.substring(createdPath.lastIndexOf('-') + 1);
            instanceId = Integer.parseInt(seqStr);

            log.info("Registered in ZooKeeper: path={}, instanceId={}, address={}:{}",
                    createdPath, instanceId, host, port);
            running = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register with ZooKeeper", e);
        }
    }

    @Override
    public void stop() {
        try {
            if (createdPath != null) {
                curator.delete().forPath(createdPath);
            }
        } catch (Exception e) {
            log.warn("Failed to deregister from ZooKeeper", e);
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public int getInstanceId() {
        return instanceId;
    }
}
