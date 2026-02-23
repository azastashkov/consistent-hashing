package com.example.loadbalancer;

import com.example.common.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final ServiceRegistry serviceRegistry;
    private final WebClient webClient;

    public ProxyController(ServiceRegistry serviceRegistry, WebClient.Builder webClientBuilder) {
        this.serviceRegistry = serviceRegistry;
        this.webClient = webClientBuilder.build();
    }

    @RequestMapping("/**")
    public Mono<byte[]> proxy(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst("X-User-Id");

        if (userId == null || userId.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return Mono.just("Missing X-User-Id header".getBytes());
        }

        ServiceInstance target = serviceRegistry.resolve(userId);
        if (target == null) {
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return Mono.just("No available service instances".getBytes());
        }

        String targetUrl = "http://" + target.address() + request.getURI().getPath();
        String query = request.getURI().getRawQuery();
        if (query != null) {
            targetUrl += "?" + query;
        }

        log.debug("Routing user={} to instance={} at {}", userId, target.id(), target.address());

        HttpHeaders headers = new HttpHeaders();
        headers.addAll(request.getHeaders());
        headers.remove(HttpHeaders.HOST);

        return webClient.method(request.getMethod())
                .uri(targetUrl)
                .headers(h -> h.addAll(headers))
                .body(request.getBody(), byte[].class)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnNext(body -> {
                    exchange.getResponse().getHeaders()
                            .add("X-Routed-To", String.valueOf(target.id()));
                });
    }
}
