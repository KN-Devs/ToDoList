package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AcceptInvitationRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.CommentRequest;
import com.todolist.portfolio.dto.CommentResponse;
import com.todolist.portfolio.dto.InvitationAcceptResponse;
import com.todolist.portfolio.dto.InviteMemberRequest;
import com.todolist.portfolio.dto.NotificationResponse;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.dto.UnreadCountResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==")
@AutoConfigureTestRestTemplate
class NotificationIntegrationTest {

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

    private UnreadCountResponse unreadCount(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        return restTemplate.exchange("/api/notifications/unread-count", HttpMethod.GET, entity, UnreadCountResponse.class)
                .getBody();
    }

    private NotificationResponse[] listNotifications(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        return restTemplate.exchange("/api/notifications", HttpMethod.GET, entity, NotificationResponse[].class)
                .getBody();
    }

    private void acceptInvitation(Integer projectId, String memberEmail) {
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
    void invitingAMember_createsANotificationForThem() {
        String ownerToken = registerAndGetToken("notif1-owner@test.com");
        String memberToken = registerAndGetToken("notif1-member@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("notif1-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        UnreadCountResponse count = unreadCount(memberToken);
        assertThat(count.count()).isEqualTo(1);

        NotificationResponse[] notifications = listNotifications(memberToken);
        assertThat(notifications).hasSize(1);
        assertThat(notifications[0].type()).isEqualTo("PROJECT_INVITATION");
        assertThat(notifications[0].read()).isFalse();
    }

    @Test
    void acceptingAnInvitation_resolvesItsNotification() {
        String ownerToken = registerAndGetToken("notif2-owner@test.com");
        String memberToken = registerAndGetToken("notif2-member@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("notif2-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        assertThat(unreadCount(memberToken).count()).isEqualTo(1);

        acceptInvitation(projectId, "notif2-member@test.com");

        assertThat(unreadCount(memberToken).count()).isEqualTo(0);
    }

    @Test
    void commentingOnSomeoneElsesTask_notifiesTheTaskOwner() {
        String ownerToken = registerAndGetToken("notif3-owner@test.com");
        String memberToken = registerAndGetToken("notif3-member@test.com");
        Integer projectId = createProject(ownerToken);
        Integer taskId = createTask(ownerToken, projectId, null);

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("notif3-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);
        acceptInvitation(projectId, "notif3-member@test.com");

        HttpEntity<CommentRequest> commentEntity =
                new HttpEntity<>(new CommentRequest("Un commentaire"), authHeaders(memberToken));
        restTemplate.postForEntity("/api/tasks/" + taskId + "/comments", commentEntity, CommentResponse.class);

        NotificationResponse[] ownerNotifications = listNotifications(ownerToken);
        assertThat(Arrays.stream(ownerNotifications).anyMatch(n -> "TASK_COMMENT".equals(n.type()))).isTrue();
    }

    @Test
    void commentingOnOwnTask_doesNotNotifySelf() {
        String token = registerAndGetToken("notif4@test.com");
        Integer projectId = createProject(token);
        Integer taskId = createTask(token, projectId, null);

        HttpEntity<CommentRequest> commentEntity =
                new HttpEntity<>(new CommentRequest("Un commentaire"), authHeaders(token));
        restTemplate.postForEntity("/api/tasks/" + taskId + "/comments", commentEntity, CommentResponse.class);

        assertThat(unreadCount(token).count()).isEqualTo(0);
    }

    @Test
    void taskWithNearDueDate_appearsAsAVirtualNotification() {
        String token = registerAndGetToken("notif5@test.com");
        Integer projectId = createProject(token);
        createTask(token, projectId, LocalDate.now().plusDays(1));

        NotificationResponse[] notifications = listNotifications(token);

        assertThat(notifications).hasSize(1);
        assertThat(notifications[0].id()).isNull();
        assertThat(notifications[0].type()).isEqualTo("TASK_DUE_SOON");
        assertThat(unreadCount(token).count()).isEqualTo(1);
    }

    @Test
    void overdueTask_isFlaggedAsOverdueNotDueSoon() {
        String token = registerAndGetToken("notif6@test.com");
        Integer projectId = createProject(token);
        createTask(token, projectId, LocalDate.now().minusDays(3));

        NotificationResponse[] notifications = listNotifications(token);

        assertThat(notifications).hasSize(1);
        assertThat(notifications[0].type()).isEqualTo("TASK_OVERDUE");
    }

    @Test
    void markRead_marksOnlyThatNotification() {
        String ownerToken = registerAndGetToken("notif7-owner@test.com");
        String memberToken = registerAndGetToken("notif7-member@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("notif7-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        NotificationResponse[] notifications = listNotifications(memberToken);
        Integer notificationId = notifications[0].id();

        HttpEntity<Void> markReadEntity = new HttpEntity<>(authHeaders(memberToken));
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/notifications/" + notificationId + "/read", HttpMethod.POST, markReadEntity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unreadCount(memberToken).count()).isEqualTo(0);
    }

    @Test
    void markRead_whenNotRecipient_returns404() {
        String ownerToken = registerAndGetToken("notif8-owner@test.com");
        String memberToken = registerAndGetToken("notif8-member@test.com");
        String strangerToken = registerAndGetToken("notif8-stranger@test.com");
        Integer projectId = createProject(ownerToken);

        HttpEntity<InviteMemberRequest> inviteEntity =
                new HttpEntity<>(new InviteMemberRequest("notif8-member@test.com"), authHeaders(ownerToken));
        restTemplate.exchange(
                "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);

        Integer notificationId = listNotifications(memberToken)[0].id();

        HttpEntity<Void> markReadEntity = new HttpEntity<>(authHeaders(strangerToken));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/notifications/" + notificationId + "/read", HttpMethod.POST, markReadEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void markAllRead_marksEveryPersistedNotification() {
        String ownerToken = registerAndGetToken("notif9-owner@test.com");
        String memberToken = registerAndGetToken("notif9-member@test.com");
        Integer projectA = createProject(ownerToken);
        Integer projectB = createProject(ownerToken);

        for (Integer projectId : new Integer[] {projectA, projectB}) {
            HttpEntity<InviteMemberRequest> inviteEntity =
                    new HttpEntity<>(new InviteMemberRequest("notif9-member@test.com"), authHeaders(ownerToken));
            restTemplate.exchange(
                    "/api/projects/" + projectId + "/invitations", HttpMethod.POST, inviteEntity, ProjectResponse.class);
        }

        assertThat(unreadCount(memberToken).count()).isEqualTo(2);

        HttpEntity<Void> markAllEntity = new HttpEntity<>(authHeaders(memberToken));
        restTemplate.postForEntity("/api/notifications/read-all", markAllEntity, Void.class);

        assertThat(unreadCount(memberToken).count()).isEqualTo(0);
    }
}
