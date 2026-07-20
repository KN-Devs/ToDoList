package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.InvitationAcceptResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.ProjectMember;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InvitationService {

    private final VerificationTokenService verificationTokenService;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;

    public InvitationService(VerificationTokenService verificationTokenService, ProjectRepository projectRepository,
                              NotificationService notificationService) {
        this.verificationTokenService = verificationTokenService;
        this.projectRepository = projectRepository;
        this.notificationService = notificationService;
    }

    public InvitationAcceptResponse accept(String tokenValue) {
        VerificationToken token = verificationTokenService.consumeToken(tokenValue, TokenType.PROJECT_INVITATION);

        Project project = token.getProject();
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation invalide");
        }

        boolean alreadyMember = project.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(token.getUser().getId()));

        if (!alreadyMember) {
            project.getMembers().add(new ProjectMember(project, token.getUser(), false));
            projectRepository.save(project);
        }

        notificationService.resolveInvitationNotifications(token.getUser(), project);

        return new InvitationAcceptResponse(project.getId(), project.getNom());
    }
}
