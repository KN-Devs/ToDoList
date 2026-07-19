package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AddMemberRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.dto.UpdateMemberPermissionRequest;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==")
@AutoConfigureTestRestTemplate
class TaskIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    private String registerAndGetToken(String email) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "Password123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Integer createProject(String token) {
        ProjectRequest projectRequest = new ProjectRequest("Projet", "description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(projectRequest, authHeaders(token));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        return createResponse.getBody().id();
    }

    @Test
    void register_thenLogin_returnsValidToken() {
        String email = "integration1@test.com";
        registerAndGetToken(email);

        LoginRequest loginRequest = new LoginRequest(email, "Password123!");
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
        Integer projectId = createProject(token);

        TaskRequest taskRequest = new TaskRequest("Ma tache", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(token));
        ResponseEntity<TaskResponse> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<TaskResponse[]> listResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/tasks", HttpMethod.GET, getEntity, TaskResponse[].class);

        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void accessTaskAsStranger_returns403() {
        String tokenA = registerAndGetToken("integration4a@test.com");
        String tokenB = registerAndGetToken("integration4b@test.com");
        Integer projectId = createProject(tokenA);

        TaskRequest taskRequest = new TaskRequest("Tache A", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(tokenA));
        ResponseEntity<TaskResponse> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);
        Integer taskId = createResponse.getBody().id();

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(tokenB));
        ResponseEntity<String> response = restTemplate.exchange("/api/tasks/" + taskId, HttpMethod.GET, getEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accessTasksWithoutToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/projects/1/tasks", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberWithoutPermission_canViewButNotCreateTask() {
        String ownerToken = registerAndGetToken("integration5-owner@test.com");
        String memberToken = registerAndGetToken("integration5-member@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<AddMemberRequest> addMemberEntity =
                new HttpEntity<>(new AddMemberRequest("integration5-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange("/api/projects/" + projectId + "/members", HttpMethod.POST, addMemberEntity, ProjectResponse.class);

        TaskRequest taskRequest = new TaskRequest("Tache", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(memberToken));
        ResponseEntity<String> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(memberToken));
        ResponseEntity<TaskResponse[]> listResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/tasks", HttpMethod.GET, getEntity, TaskResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void memberWithoutPermission_canChangeTaskStatusButNotRenameIt() {
        String ownerToken = registerAndGetToken("integration7-owner@test.com");
        String memberToken = registerAndGetToken("integration7-member@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<AddMemberRequest> addMemberEntity =
                new HttpEntity<>(new AddMemberRequest("integration7-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange("/api/projects/" + projectId + "/members", HttpMethod.POST, addMemberEntity, ProjectResponse.class);

        TaskRequest taskRequest = new TaskRequest("Tache", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(ownerToken));
        ResponseEntity<TaskResponse> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);
        Integer taskId = createResponse.getBody().id();

        TaskRequest statusOnlyChange = new TaskRequest("Tache", "description", TaskStatus.DONE);
        HttpEntity<TaskRequest> statusEntity = new HttpEntity<>(statusOnlyChange, authHeaders(memberToken));
        ResponseEntity<TaskResponse> statusResponse =
                restTemplate.exchange("/api/tasks/" + taskId, HttpMethod.PUT, statusEntity, TaskResponse.class);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody().status()).isEqualTo(TaskStatus.DONE);

        TaskRequest renameAttempt = new TaskRequest("Renommee", "description", TaskStatus.DONE);
        HttpEntity<TaskRequest> renameEntity = new HttpEntity<>(renameAttempt, authHeaders(memberToken));
        ResponseEntity<String> renameResponse =
                restTemplate.exchange("/api/tasks/" + taskId, HttpMethod.PUT, renameEntity, String.class);
        assertThat(renameResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void memberGrantedPermission_canCreateTask() {
        String ownerToken = registerAndGetToken("integration6-owner@test.com");
        String memberToken = registerAndGetToken("integration6-member@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<AddMemberRequest> addMemberEntity =
                new HttpEntity<>(new AddMemberRequest("integration6-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange("/api/projects/" + projectId + "/members", HttpMethod.POST, addMemberEntity, ProjectResponse.class);

        HttpEntity<UpdateMemberPermissionRequest> grantEntity =
                new HttpEntity<>(new UpdateMemberPermissionRequest(true), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> grantResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/members/integration6-member@test.com",
                HttpMethod.PATCH, grantEntity, ProjectResponse.class);
        assertThat(grantResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        TaskRequest taskRequest = new TaskRequest("Tache", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(memberToken));
        ResponseEntity<TaskResponse> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
