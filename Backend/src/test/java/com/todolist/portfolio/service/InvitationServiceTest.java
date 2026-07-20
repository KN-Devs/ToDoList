package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.InvitationAcceptResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private VerificationTokenService verificationTokenService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InvitationService invitationService;

    private User bob;
    private User carol;
    private Project bobProject;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
        carol = new User(3, "Petit", "Carol", "carol@test.com", "hash", Role.USER);
        bobProject = new Project(10, "Projet de Bob", "description", null, null, bob);
    }

    @Test
    void accept_addsInviteeAsMember() {
        VerificationToken token = new VerificationToken("tok", TokenType.PROJECT_INVITATION, carol, bobProject,
                Instant.now().plusSeconds(3600), Instant.now());
        token.setConsumedAt(Instant.now());
        when(verificationTokenService.consumeToken("tok", TokenType.PROJECT_INVITATION)).thenReturn(token);

        InvitationAcceptResponse response = invitationService.accept("tok");

        assertThat(response.projectId()).isEqualTo(10);
        assertThat(response.projectNom()).isEqualTo("Projet de Bob");
        assertThat(bobProject.getMembers()).extracting(m -> m.getUser().getEmail()).containsExactly("carol@test.com");
        verify(projectRepository).save(bobProject);
        verify(notificationService).resolveInvitationNotifications(carol, bobProject);
    }

    @Test
    void accept_whenAlreadyMember_doesNotDuplicate() {
        bobProject.getMembers().add(new com.todolist.portfolio.entity.ProjectMember(bobProject, carol, false));
        VerificationToken token = new VerificationToken("tok", TokenType.PROJECT_INVITATION, carol, bobProject,
                Instant.now().plusSeconds(3600), Instant.now());
        when(verificationTokenService.consumeToken("tok", TokenType.PROJECT_INVITATION)).thenReturn(token);

        invitationService.accept("tok");

        assertThat(bobProject.getMembers()).hasSize(1);
        verify(projectRepository, never()).save(any());
    }
}
