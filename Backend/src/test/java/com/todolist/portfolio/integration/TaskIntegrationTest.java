package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TaskIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    private String registerAndGetToken(String email) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void register_thenLogin_returnsValidToken() {
        String email = "integration1@test.com";
        registerAndGetToken(email);

        LoginRequest loginRequest = new LoginRequest(email, "password123");
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().token()).isNotBlank();
    }

    @Test
    void login_withWrongPassword_returns401() {
        registerAndGetToken("integration2@test.com");

        LoginRequest loginRequest = new LoginRequest("integration2@test.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login", loginRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createTask_thenListTasks_ownerSeesIt() {
        String token = registerAndGetToken("integration3@test.com");

        TaskRequest taskRequest = new TaskRequest("Ma tache", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(token));
        ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity("/api/tasks", createEntity, TaskResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<TaskResponse[]> listResponse = restTemplate.exchange("/api/tasks", HttpMethod.GET, getEntity, TaskResponse[].class);

        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void accessOtherUsersTask_returns403() {
        String tokenA = registerAndGetToken("integration4a@test.com");
        String tokenB = registerAndGetToken("integration4b@test.com");

        TaskRequest taskRequest = new TaskRequest("Tache A", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(tokenA));
        ResponseEntity<TaskResponse> createResponse = restTemplate.postForEntity("/api/tasks", createEntity, TaskResponse.class);
        Integer taskId = createResponse.getBody().id();

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(tokenB));
        ResponseEntity<String> response = restTemplate.exchange("/api/tasks/" + taskId, HttpMethod.GET, getEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accessTasksWithoutToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/tasks", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
