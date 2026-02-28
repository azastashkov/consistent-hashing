# Consistent Hashing Load Balancer

A distributed load balancing system that demonstrates consistent hashing for routing requests to backend services. Features dynamic service discovery via ZooKeeper, reactive request proxying, and full observability with Prometheus and Grafana.

## Architecture

```
                          ┌──────────────┐
                          │  Test Client │
                          │  (load gen)  │
                          └──────┬───────┘
                                 │
                                 ▼
                          ┌──────────────┐
                          │Load Balancer │◄──── Consistent Hash Ring
                          │  (WebFlux)   │
                          └──────┬───────┘
                                 │
            ┌────────────┬───────┴───────┬────────────┐
            ▼            ▼               ▼            ▼
      ┌───────────┐┌───────────┐   ┌───────────┐┌───────────┐
      │API Svc  0 ││API Svc  1 │   │API Svc  2 ││API Svc  3 │
      └─────┬─────┘└─────┬─────┘   └─────┬─────┘└─────┬─────┘
            │            │               │            │
            └────────────┴───────┬───────┴────────────┘
                                 ▼
                          ┌──────────────┐
                          │  ZooKeeper   │
                          │  (discovery) │
                          └──────────────┘
```

API service instances register themselves as ephemeral nodes in ZooKeeper. The load balancer watches ZooKeeper for changes and maintains a consistent hash ring. Incoming requests are routed to a backend instance based on the `X-User-Id` header — the same user always hits the same backend, enabling per-user caching.

## Tech Stack

- **Java 21**, **Spring Boot 3.4.1**
- **Spring WebFlux** — reactive load balancer proxy
- **Apache Curator 5.7.1** — ZooKeeper client
- **Micrometer + Prometheus** — metrics
- **Grafana** — dashboards
- **Docker Compose** — orchestration
- **Gradle** — multi-project build
- **JUnit 5 + AssertJ** — testing

## Project Structure

```
consistent-hashing/
├── common/           # Shared library: ConsistentHashRing, ServiceInstance
├── api-service/      # Backend REST service (port 8081)
├── load-balancer/    # Reactive proxy with consistent hashing (port 8080)
├── test-client/      # HTTP load generator
├── infra/            # Docker Compose, Prometheus, Grafana configs
└── docs/             # Architecture diagrams (draw.io)
```

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Run with Docker Compose

```bash
# Build all modules
./gradlew assemble

# Start the system
cd infra
docker compose up --build
```

This starts:

| Service        | URL                    | Description                  |
|----------------|------------------------|------------------------------|
| Load Balancer  | http://localhost:8080   | Entry point for requests     |
| Prometheus     | http://localhost:9090   | Metrics                      |
| Grafana        | http://localhost:3000   | Dashboards (admin / admin)   |
| ZooNavigator   | http://localhost:9000   | ZooKeeper web UI             |

### Send a test request

```bash
curl -H "X-User-Id: user-42" http://localhost:8080/api/process
```

The response includes the instance that handled the request. Repeating the same `X-User-Id` always routes to the same backend.

### Run the load generator

```bash
cd infra
docker compose --profile test up test-client
```

Generates traffic from 16 simulated users, sending requests every 100ms for 5 minutes.

## Local Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run individual services (requires a local ZooKeeper on port 2181)
./gradlew :api-service:bootRun
./gradlew :load-balancer:bootRun
./gradlew :test-client:bootRun
```

## Configuration

Configuration is managed via environment variables or `application.yml` in each module.

| Variable                    | Default                  | Description                    |
|-----------------------------|--------------------------|--------------------------------|
| `ZOOKEEPER_CONNECT_STRING`  | `localhost:2181`         | ZooKeeper connection string    |
| `HOSTNAME`                  | `localhost`              | API service hostname           |
| `LOAD_BALANCER_URL`         | `http://localhost:8080`  | Load balancer URL (test client)|
| `NUM_USERS`                 | `20`                     | Simulated users (test client)  |
| `INTERVAL_MS`               | `500`                    | Request interval (test client) |
| `DURATION_SECONDS`          | `300`                    | Test duration (test client)    |

## How It Works

### Consistent Hash Ring

The `ConsistentHashRing<T>` in the `common` module is a thread-safe generic implementation backed by a `TreeMap`. Nodes are placed on the ring by their integer key. To route a request, the key is hashed and the ring finds the nearest node clockwise using `ceilingEntry()`, wrapping around to the first node if needed.

This ensures:
- **Deterministic routing** — the same key always maps to the same node
- **Minimal disruption** — adding or removing a node only affects keys that were mapped to that node

### Service Discovery

Each API service instance registers an ephemeral-sequential ZNode under `/services/api/` on startup. The load balancer uses a `PathChildrenCache` to watch this path and automatically updates the hash ring when instances join or leave.

### Monitoring

Metrics are exported via Spring Boot Actuator (`/actuator/prometheus`) and scraped by Prometheus every 5 seconds. A pre-configured Grafana dashboard visualizes request rates, latencies, and per-instance distribution.
