package com.todolist.portfolio.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.CommentEvent;
import com.todolist.portfolio.dto.CommentRequest;
import com.todolist.portfolio.dto.CommentResponse;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.TaskEvent;
import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.TaskStatus;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==")
@AutoConfigureTestRestTemplate
class RealtimeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private String registerAndGetToken(String email) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "Password123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        return response.getBody().token();
    }

    private Integer createProject(String token) {
        ProjectRequest projectRequest = new ProjectRequest("Projet temps réel", "description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<ProjectRequest> createEntity = new HttpEntity<>(projectRequest, headers);
        ResponseEntity<ProjectResponse> createResponse =
                restTemplate.postForEntity("/api/projects", createEntity, ProjectResponse.class);
        return createResponse.getBody().id();
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        client.setMessageConverter(converter);
        return client;
    }

    private StompSession connect(WebSocketStompClient client, String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * SUBSCRIBE is sent asynchronously; without a short pause, a broadcast triggered
     * right after subscribing can race the server-side subscription registration.
     */
    private void awaitSubscriptionRegistered() throws InterruptedException {
        Thread.sleep(300);
    }

    private static class QueueingFrameHandler<T> implements org.springframework.messaging.simp.stomp.StompFrameHandler {
        private final Class<T> type;
        final BlockingQueue<T> queue = new LinkedBlockingQueue<>();

        QueueingFrameHandler(Class<T> type) {
            this.type = type;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return type;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.add(type.cast(payload));
        }
    }

    /**
     * DefaultStompSession routes an ERROR frame to the top-level session handler's
     * handleFrame — this is the only case where that method is invoked outside of a
     * per-subscription StompFrameHandler, so any callback here means the server sent
     * back an ERROR (i.e. the SUBSCRIBE was rejected by JwtChannelInterceptor).
     */
    private static class ErrorCapturingSessionHandler extends StompSessionHandlerAdapter {
        final BlockingQueue<StompHeaders> errorFrames = new LinkedBlockingQueue<>();

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errorFrames.add(headers);
        }
    }

    @Test
    void createTask_broadcastsCreatedEventToSubscribedMember() throws Exception {
        String email = "realtime-owner1@test.com";
        String token = registerAndGetToken(email);
        markEmailVerified(email);
        Integer projectId = createProject(token);

        WebSocketStompClient client = stompClient();
        StompSession session = connect(client, token);

        QueueingFrameHandler<TaskEvent> handler = new QueueingFrameHandler<>(TaskEvent.class);
        session.subscribe("/topic/projects/" + projectId + "/tasks", handler);
        awaitSubscriptionRegistered();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        TaskRequest taskRequest = new TaskRequest("Tache temps réel", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, headers);
        restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);

        TaskEvent event = handler.queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.action()).isEqualTo("CREATED");
        assertThat(event.task().nom()).isEqualTo("Tache temps réel");

        session.disconnect();
    }

    @Test
    void createComment_broadcastsCreatedEventToSubscribedMember() throws Exception {
        String email = "realtime-owner2@test.com";
        String token = registerAndGetToken(email);
        markEmailVerified(email);
        Integer projectId = createProject(token);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        TaskRequest taskRequest = new TaskRequest("Tache avec commentaires", "description", TaskStatus.TODO);
        HttpEntity<TaskRequest> createEntity = new HttpEntity<>(taskRequest, headers);
        ResponseEntity<TaskResponse> createResponse =
                restTemplate.postForEntity("/api/projects/" + projectId + "/tasks", createEntity, TaskResponse.class);
        Integer taskId = createResponse.getBody().id();

        WebSocketStompClient client = stompClient();
        StompSession session = connect(client, token);

        QueueingFrameHandler<CommentEvent> handler = new QueueingFrameHandler<>(CommentEvent.class);
        session.subscribe("/topic/projects/" + projectId + "/tasks/" + taskId + "/comments", handler);
        awaitSubscriptionRegistered();

        org.springframework.http.HttpHeaders commentHeaders = new org.springframework.http.HttpHeaders();
        commentHeaders.setBearerAuth(token);
        HttpEntity<CommentRequest> commentEntity = new HttpEntity<>(new CommentRequest("Un commentaire"), commentHeaders);
        restTemplate.postForEntity("/api/tasks/" + taskId + "/comments", commentEntity, CommentResponse.class);

        CommentEvent event = handler.queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.action()).isEqualTo("CREATED");
        assertThat(event.comment().content()).isEqualTo("Un commentaire");

        session.disconnect();
    }

    @Test
    void subscribeToProjectTopic_asNonMember_isRejected() throws Exception {
        String ownerEmail = "realtime-owner3@test.com";
        String ownerToken = registerAndGetToken(ownerEmail);
        markEmailVerified(ownerEmail);
        Integer projectId = createProject(ownerToken);

        String strangerEmail = "realtime-stranger3@test.com";
        String strangerToken = registerAndGetToken(strangerEmail);
        markEmailVerified(strangerEmail);

        WebSocketStompClient client = stompClient();
        ErrorCapturingSessionHandler sessionHandler = new ErrorCapturingSessionHandler();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + strangerToken);
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                        connectHeaders, sessionHandler)
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/projects/" + projectId + "/tasks", new QueueingFrameHandler<>(TaskEvent.class));

        StompHeaders errorHeaders = sessionHandler.errorFrames.poll(5, TimeUnit.SECONDS);
        assertThat(errorHeaders).isNotNull();

        if (session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    void connect_withoutValidToken_isRejected() {
        WebSocketStompClient client = stompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer not-a-real-token");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                                connectHeaders, new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS));
    }

    private void markEmailVerified(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
    }
}
