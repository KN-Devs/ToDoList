package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AddMemberRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.RegisterRequest;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProjectIntegrationTest {

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

    private ProjectRequest sampleProject() {
        return new ProjectRequest("Refonte du site", "Migration vers Angular",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
    }

    @Test
    void createProject_thenListProjects_ownerSeesIt() {
        String token = registerAndGetToken("project1@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(token));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody().ownerEmail()).isEqualTo("project1@test.com");

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<ProjectResponse[]> listResponse =
                restTemplate.exchange("/api/projects", HttpMethod.GET, getEntity, ProjectResponse[].class);

        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void addMember_thenMemberSeesProject() {
        String ownerToken = registerAndGetToken("project2-owner@test.com");
        String memberToken = registerAndGetToken("project2-member@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<AddMemberRequest> addMemberEntity =
                new HttpEntity<>(new AddMemberRequest("project2-member@test.com"), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> addMemberResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/members", HttpMethod.POST, addMemberEntity, ProjectResponse.class);

        assertThat(addMemberResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addMemberResponse.getBody().members()).extracting("email").containsExactly("project2-member@test.com");

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(memberToken));
        ResponseEntity<ProjectResponse> memberViewResponse = restTemplate.exchange(
                "/api/projects/" + projectId, HttpMethod.GET, getEntity, ProjectResponse.class);

        assertThat(memberViewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void addMember_whenNotOwner_returns403() {
        String ownerToken = registerAndGetToken("project3-owner@test.com");
        String strangerToken = registerAndGetToken("project3-stranger@test.com");
        registerAndGetToken("project3-target@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<AddMemberRequest> addMemberEntity =
                new HttpEntity<>(new AddMemberRequest("project3-target@test.com"), authHeaders(strangerToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/projects/" + projectId + "/members", HttpMethod.POST, addMemberEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accessProjectAsStranger_returns403() {
        String ownerToken = registerAndGetToken("project4-owner@test.com");
        String strangerToken = registerAndGetToken("project4-stranger@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(strangerToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/projects/" + projectId, HttpMethod.GET, getEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createProject_withEndDateBeforeStartDate_returns400() {
        String token = registerAndGetToken("project5@test.com");
        ProjectRequest invalid = new ProjectRequest("Projet invalide", "desc",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1));

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(invalid, authHeaders(token));
        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/projects", createEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
