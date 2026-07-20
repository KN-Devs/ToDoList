package com.todolist.portfolio.config;

import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.CustomUserDetailsService;
import com.todolist.portfolio.service.JwtService;
import com.todolist.portfolio.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private JwtChannelInterceptor interceptor;

    private User bob;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, User principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        if (principal != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_withValidToken_setsAuthenticatedPrincipal() {
        when(jwtService.extractUsername("valid-token")).thenReturn(bob.getEmail());
        when(userDetailsService.loadUserByUsername(bob.getEmail())).thenReturn(bob);
        when(jwtService.isTokenValid("valid-token", bob)).thenReturn(true);

        Message<byte[]> message = connectMessage("Bearer valid-token");
        interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void connect_withoutAuthorizationHeader_throwsAccessDenied() {
        Message<byte[]> message = connectMessage(null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_withMalformedAuthorizationHeader_throwsAccessDenied() {
        Message<byte[]> message = connectMessage("NotBearer abc");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_withInvalidToken_throwsAccessDenied() {
        when(jwtService.extractUsername("bad-token")).thenReturn(bob.getEmail());
        when(userDetailsService.loadUserByUsername(bob.getEmail())).thenReturn(bob);
        when(jwtService.isTokenValid("bad-token", bob)).thenReturn(false);

        Message<byte[]> message = connectMessage("Bearer bad-token");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void connect_withUnknownUser_throwsAccessDenied() {
        when(jwtService.extractUsername("token")).thenReturn("ghost@test.com");
        when(userDetailsService.loadUserByUsername("ghost@test.com"))
                .thenThrow(new UsernameNotFoundException("Aucun utilisateur avec cet email : ghost@test.com"));

        Message<byte[]> message = connectMessage("Bearer token");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toProjectTopic_whenAuthorizedMember_passesThrough() {
        Message<byte[]> message = subscribeMessage("/topic/projects/10/tasks", bob);
        interceptor.preSend(message, null);

        verify(projectService).checkCanView(10, bob);
    }

    @Test
    void subscribe_toProjectCommentsTopic_whenAuthorizedMember_passesThrough() {
        Message<byte[]> message = subscribeMessage("/topic/projects/10/tasks/5/comments", bob);
        interceptor.preSend(message, null);

        verify(projectService).checkCanView(10, bob);
    }

    @Test
    void subscribe_toProjectTopic_whenNotAMember_throwsAccessDenied() {
        doThrow(new AccessDeniedException("Accès refusé"))
                .when(projectService).checkCanView(eq(10), any());

        Message<byte[]> message = subscribeMessage("/topic/projects/10/tasks", bob);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_withoutAuthenticatedPrincipal_throwsAccessDenied() {
        Message<byte[]> message = subscribeMessage("/topic/projects/10/tasks", null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribe_toNonProjectDestination_passesThroughWithoutCheckingPermissions() {
        Message<byte[]> message = subscribeMessage("/user/queue/notifications", bob);
        interceptor.preSend(message, null);

        verify(projectService, never()).checkCanView(any(Integer.class), any());
    }
}
