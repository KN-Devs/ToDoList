package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AcceptInvitationRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.InvitationAcceptResponse;
import com.todolist.portfolio.dto.InviteMemberRequest;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.ProjectRepository;
import com.todolist.portfolio.repository.UserRepository;
import com.todolist.portfolio.repository.VerificationTokenRepository;
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
class ProjectIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

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

    private void inviteAndAccept(String ownerToken, Integer projectId, String memberEmail) {
        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest(memberEmail), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        Project project = projectRepository.findById(projectId).orElseThrow();
        User member = userRepository.findByEmail(memberEmail).orElseThrow();
        String token = verificationTokenRepository
                .findByProjectAndUserAndTypeAndConsumedAtIsNull(project, member, TokenType.PROJECT_INVITATION)
                .orElseThrow()
                .getToken();

        HttpEntity<AcceptInvitationRequest> acceptEntity = new HttpEntity<>(new AcceptInvitationRequest(token));
        restTemplate.postForEntity("/api/invitations/accept", acceptEntity, InvitationAcceptResponse.class);
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
    void inviteMember_createsPendingInvitation_notYetAMember() {
        String ownerToken = registerAndGetToken("project2-owner@test.com");
        registerAndGetToken("project2-member@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("project2-member@test.com"), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> inviteResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        assertThat(inviteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inviteResponse.getBody().members()).isEmpty();
        assertThat(inviteResponse.getBody().pendingInvitations()).containsExactly("project2-member@test.com");
    }

    @Test
    void acceptInvitation_addsMemberAndAllowsAccess() {
        String ownerToken = registerAndGetToken("project2c-owner@test.com");
        String memberToken = registerAndGetToken("project2c-member@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        inviteAndAccept(ownerToken, projectId, "project2c-member@test.com");

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(memberToken));
        ResponseEntity<ProjectResponse> memberViewResponse = restTemplate.exchange(
                "/api/projects/" + projectId, HttpMethod.GET, getEntity, ProjectResponse.class);

        assertThat(memberViewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberViewResponse.getBody().members()).extracting("email").containsExactly("project2c-member@test.com");
        assertThat(memberViewResponse.getBody().pendingInvitations()).isEmpty();
    }

    @Test
    void acceptInvitation_withAlreadyConsumedToken_returns410() {
        String ownerToken = registerAndGetToken("project2d-owner@test.com");
        registerAndGetToken("project2d-member@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("project2d-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        Project project = projectRepository.findById(projectId).orElseThrow();
        User member = userRepository.findByEmail("project2d-member@test.com").orElseThrow();
        String token = verificationTokenRepository
                .findByProjectAndUserAndTypeAndConsumedAtIsNull(project, member, TokenType.PROJECT_INVITATION)
                .orElseThrow()
                .getToken();

        HttpEntity<AcceptInvitationRequest> acceptEntity = new HttpEntity<>(new AcceptInvitationRequest(token));
        ResponseEntity<InvitationAcceptResponse> firstAccept =
                restTemplate.postForEntity("/api/invitations/accept", acceptEntity, InvitationAcceptResponse.class);
        assertThat(firstAccept.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> secondAccept =
                restTemplate.postForEntity("/api/invitations/accept", acceptEntity, String.class);
        assertThat(secondAccept.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void inviteMember_withDifferentCaseEmail_findsExistingUser() {
        String ownerToken = registerAndGetToken("project2b-owner@test.com");
        registerAndGetToken("project2b-member@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("Project2b-Member@Test.com"), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> inviteResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        assertThat(inviteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inviteResponse.getBody().pendingInvitations()).containsExactly("project2b-member@test.com");
    }

    @Test
    void inviteMember_whenNotOwner_returns403() {
        String ownerToken = registerAndGetToken("project3-owner@test.com");
        String strangerToken = registerAndGetToken("project3-stranger@test.com");
        registerAndGetToken("project3-target@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("project3-target@test.com"), authHeaders(strangerToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelInvitation_whenOwner_removesPendingInvitation() {
        String ownerToken = registerAndGetToken("project3b-owner@test.com");
        registerAndGetToken("project3b-target@test.com");

        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(sampleProject(), authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        Integer projectId = createResponse.getBody().id();

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("project3b-target@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        HttpEntity<Void> cancelEntity = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<ProjectResponse> cancelResponse = restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations/project3b-target@test.com",
                HttpMethod.DELETE, cancelEntity, ProjectResponse.class);

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().pendingInvitations()).isEmpty();
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
