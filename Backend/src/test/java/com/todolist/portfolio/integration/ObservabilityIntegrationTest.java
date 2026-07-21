package com.todolist.portfolio.integration;

import com.todolist.portfolio.config.CorrelationIdFilter;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==",
                "metrics.scrape-token=test-scrape-token"
        })
@AutoConfigureTestRestTemplate
// Spring Boot désactive par défaut les exporteurs de métriques externes
// (Prometheus inclus) dans un @SpringBootTest, pour éviter qu'un test ne
// pousse des métriques vers un vrai backend de monitoring.
@AutoConfigureMetrics
class ObservabilityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private String registerAndGetToken(String email, Role role) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "Password123!");
        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(role);
        user.setEmailVerified(true);
        userRepository.save(user);

        ResponseEntity<AuthResponse> login =
                restTemplate.postForEntity("/api/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);
        return login.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void health_isPubliclyAccessibleWithoutDetails() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).doesNotContain("\"components\"");
    }

    @Test
    void health_showsComponentDetailsForAdmin() {
        String adminToken = registerAndGetToken("obs-admin1@test.com", Role.ADMIN);

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"components\"");
    }

    @Test
    void health_doesNotShowComponentDetailsForNonAdmin() {
        String userToken = registerAndGetToken("obs-user1@test.com", Role.USER);

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("\"components\"");
    }

    @Test
    void metrics_requiresAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void metrics_forbiddenForNonAdmin() {
        String userToken = registerAndGetToken("obs-user2@test.com", Role.USER);

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(userToken));
        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/metrics", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void metrics_accessibleForAdmin() {
        String adminToken = registerAndGetToken("obs-admin2@test.com", Role.ADMIN);

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/metrics", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheus_accessibleForAdminOnly() {
        String adminToken = registerAndGetToken("obs-admin3@test.com", Role.ADMIN);

        HttpEntity<Void> adminEntity = new HttpEntity<>(authHeaders(adminToken));
        ResponseEntity<String> adminResponse =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, adminEntity, String.class);
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> anonymousResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(anonymousResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void prometheus_accessibleWithTheScrapeToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-scrape-token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheus_rejectsAWrongScrapeToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("wrong-scrape-token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/prometheus", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void correlationId_isGeneratedAndReturnedInResponseHeader() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        String requestId = response.getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
    }

    @Test
    void correlationId_reusesTheProvidedRequestId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.REQUEST_ID_HEADER, "test-correlation-id-42");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", HttpMethod.GET, entity, String.class);

        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER))
                .isEqualTo("test-correlation-id-42");
    }
}
