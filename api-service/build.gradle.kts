plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val curatorVersion: String by rootProject.extra

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.apache.curator:curator-framework:$curatorVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
