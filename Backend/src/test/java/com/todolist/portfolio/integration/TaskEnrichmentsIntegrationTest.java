package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AttachmentResponse;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.CommentRequest;
import com.todolist.portfolio.dto.CommentResponse;
import com.todolist.portfolio.dto.InviteMemberRequest;
import com.todolist.portfolio.dto.AcceptInvitationRequest;
import com.todolist.portfolio.dto.InvitationAcceptResponse;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.TaskStatus;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==")
@AutoConfigureTestRestTemplate
class TaskEnrichmentsIntegrationTest {

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

    private Integer createProject(String token) {
        ProjectRequest projectRequest = new ProjectRequest("Projet", "description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(projectRequest, authHeaders(token));
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        return createResponse.getBody().id();
    }

    private Integer createTask(String token, Integer projectId, LocalDate dueDate) {
        TaskRequest taskRequest = new TaskRequest("Tache", "description", TaskStatus.TODO, dueDate);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, authHeaders(token));
        ResponseEntity<TaskResponse> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);
        return createResponse.getBody().id();
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
    void createTask_withDueDate_returnsItInResponse() {
        String token = registerAndGetToken("enrich1@test.com");
        Integer projectId = createProject(token);
        LocalDate dueDate = LocalDate.of(2026, 3, 15);

        Integer taskId = createTask(token, projectId, dueDate);

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<TaskResponse> response =
                restTemplate.exchange("/api/tasks/" + taskId, HttpMethod.GET, getEntity, TaskResponse.class);

        assertThat(response.getBody().dueDate()).isEqualTo(dueDate);
    }

    @Test
    void postComment_thenListComments_returnsIt() {
        String ownerToken = registerAndGetToken("enrich2-owner@test.com");
        String memberToken = registerAndGetToken("enrich2-member@test.com");
        Integer projectId = createProject(ownerToken);
        Integer taskId = createTask(ownerToken, projectId, null);
        inviteAndAccept(ownerToken, projectId, "enrich2-member@test.com");

        HttpEntity<CommentRequest> commentEntity =
                new HttpEntity<>(new CommentRequest("Un commentaire"), authHeaders(memberToken));
        ResponseEntity<CommentResponse> createResponse = restTemplate.postForEntity(
                "/api/tasks/" + taskId + "/comments", commentEntity, CommentResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody().authorEmail()).isEqualTo("enrich2-member@test.com");

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(ownerToken));
        ResponseEntity<CommentResponse[]> listResponse = restTemplate.exchange(
                "/api/tasks/" + taskId + "/comments", HttpMethod.GET, getEntity, CommentResponse[].class);

        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0].content()).isEqualTo("Un commentaire");
    }

    @Test
    void deleteComment_whenNotAuthor_returns403() {
        String ownerToken = registerAndGetToken("enrich3-owner@test.com");
        String memberToken = registerAndGetToken("enrich3-member@test.com");
        Integer projectId = createProject(ownerToken);
        Integer taskId = createTask(ownerToken, projectId, null);
        inviteAndAccept(ownerToken, projectId, "enrich3-member@test.com");

        HttpEntity<CommentRequest> commentEntity =
                new HttpEntity<>(new CommentRequest("Un commentaire"), authHeaders(ownerToken));
        ResponseEntity<CommentResponse> createResponse = restTemplate.postForEntity(
                "/api/tasks/" + taskId + "/comments", commentEntity, CommentResponse.class);
        Integer commentId = createResponse.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(memberToken));
        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                "/api/tasks/" + taskId + "/comments/" + commentId, HttpMethod.DELETE, deleteEntity, String.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void uploadAttachment_thenDownloadIt_returnsSameBytes() {
        String token = registerAndGetToken("enrich4@test.com");
        Integer projectId = createProject(token);
        Integer taskId = createTask(token, projectId, null);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource("contenu du fichier".getBytes()) {
            @Override
            public String getFilename() {
                return "plan.txt";
            }
        };
        body.add("file", fileResource);

        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> uploadEntity = new HttpEntity<>(body, headers);

        ResponseEntity<AttachmentResponse> uploadResponse = restTemplate.postForEntity(
                "/api/tasks/" + taskId + "/attachments", uploadEntity, AttachmentResponse.class);

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploadResponse.getBody().filename()).isEqualTo("plan.txt");
        Integer attachmentId = uploadResponse.getBody().id();

        HttpEntity<Void> getEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<AttachmentResponse[]> listResponse = restTemplate.exchange(
                "/api/tasks/" + taskId + "/attachments", HttpMethod.GET, getEntity, AttachmentResponse[].class);
        assertThat(listResponse.getBody()).hasSize(1);

        ResponseEntity<byte[]> downloadResponse = restTemplate.exchange(
                "/api/tasks/" + taskId + "/attachments/" + attachmentId + "/download",
                HttpMethod.GET, getEntity, byte[].class);

        assertThat(downloadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(downloadResponse.getBody())).isEqualTo("contenu du fichier");
        assertThat(downloadResponse.getHeaders().getContentDisposition().getFilename()).isEqualTo("plan.txt");
    }

    @Test
    void uploadAttachment_whenViewOnlyMember_returns403() {
        String ownerToken = registerAndGetToken("enrich5-owner@test.com");
        String memberToken = registerAndGetToken("enrich5-member@test.com");
        Integer projectId = createProject(ownerToken);
        Integer taskId = createTask(ownerToken, projectId, null);
        inviteAndAccept(ownerToken, projectId, "enrich5-member@test.com");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource("contenu".getBytes()) {
            @Override
            public String getFilename() {
                return "plan.txt";
            }
        };
        body.add("file", fileResource);

        HttpHeaders headers = authHeaders(memberToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> uploadEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/tasks/" + taskId + "/attachments", uploadEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteAttachment_whenManageRights_removesIt() {
        String token = registerAndGetToken("enrich6@test.com");
        Integer projectId = createProject(token);
        Integer taskId = createTask(token, projectId, null);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource("contenu".getBytes()) {
            @Override
            public String getFilename() {
                return "plan.txt";
            }
        };
        body.add("file", fileResource);
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<AttachmentResponse> uploadResponse = restTemplate.postForEntity(
                "/api/tasks/" + taskId + "/attachments", new HttpEntity<>(body, headers), AttachmentResponse.class);
        Integer attachmentId = uploadResponse.getBody().id();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        restTemplate.exchange("/api/tasks/" + taskId + "/attachments/" + attachmentId,
                HttpMethod.DELETE, deleteEntity, Void.class);

        ResponseEntity<AttachmentResponse[]> listResponse = restTemplate.exchange(
                "/api/tasks/" + taskId + "/attachments", HttpMethod.GET, deleteEntity, AttachmentResponse[].class);
        assertThat(listResponse.getBody()).isEmpty();
    }
}
