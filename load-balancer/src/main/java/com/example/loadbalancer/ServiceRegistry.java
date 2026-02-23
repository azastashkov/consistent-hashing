package com.example.loadbalancer;

import com.example.common.ConsistentHashRing;
import com.example.common.ServiceInstance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);
    private static final String SERVICE_PATH = "/services/api";

    private final CuratorFramework curator;
    private final ConsistentHashRing<ServiceInstance> ring = new ConsistentHashRing<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PathChildrenCache cache;

    public ServiceRegistry(CuratorFramework curator) {
        this.curator = curator;
    }

    @PostConstruct
    public void init() throws Exception {
        // Ensure parent path exists
        if (curator.checkExists().forPath(SERVICE_PATH) == null) {
            curator.create().creatingParentsIfNeeded().forPath(SERVICE_PATH);
        }

        cache = new PathChildrenCache(curator, SERVICE_PATH, true);
        cache.getListenable().addListener(this::onEvent);
        cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);

        // Process initial children
        cache.getCurrentData().forEach(childData -> {
            try {
                addInstance(childData.getPath(), childData.getData());
            } catch (Exception e) {
                log.error("Failed to process initial child: {}", childData.getPath(), e);
            }
        });

        log.info("ServiceRegistry initialized with {} instances", ring.size());
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (cache != null) {
            cache.close();
        }
    }

    private void onEvent(CuratorFramework client, PathChildrenCacheEvent event) {
        switch (event.getType()) {
            case CHILD_ADDED -> {
                try {
                    addInstance(event.getData().getPath(), event.getData().getData());
                } catch (Exception e) {
                    log.error("Failed to add instance", e);
                }
            }
            case CHILD_REMOVED -> {
                String path = event.getData().getPath();
                String nodeKey = extractNodeKey(path);
                ring.removeNode(nodeKey);
                log.info("Removed instance from ring: nodeKey={}", nodeKey);
            }
            default -> {}
        }
    }

    private void addInstance(String path, byte[] data) throws Exception {
        JsonNode json = objectMapper.readTree(data);
        String host = json.get("host").asText();
        int port = json.get("port").asInt();
        String nodeKey = extractNodeKey(path);
        int nodeKeyId = Integer.parseInt(nodeKey);

        ServiceInstance instance = new ServiceInstance(nodeKeyId, host, port);
        ring.addNode(nodeKey, instance);
        log.info("Added instance to ring: nodeKey={}, address={}:{}", nodeKey, host, port);
    }

    private String extractNodeKey(String path) {
        // Path like /services/api/instance-0000000001 -> "0000000001" -> parsed to int -> back to string
        String name = path.substring(path.lastIndexOf('-') + 1);
        return String.valueOf(Integer.parseInt(name));
    }

    public ServiceInstance resolve(String key) {
        return ring.getNode(key);
    }

    public int size() {
        return ring.size();
    }
}
