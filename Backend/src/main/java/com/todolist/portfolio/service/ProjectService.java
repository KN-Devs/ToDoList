package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.ProjectMemberResponse;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.ProjectMember;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.ProjectRepository;
import com.todolist.portfolio.repository.UserRepository;
import com.todolist.portfolio.repository.VerificationTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository,
                           VerificationTokenRepository verificationTokenRepository,
                           VerificationTokenService verificationTokenService, EmailService emailService,
                           NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.verificationTokenService = verificationTokenService;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public ProjectResponse create(ProjectRequest request, User owner) {
        validateDates(request);

        Project project = new Project(null, request.getNom(), request.getDescription(),
                request.getStartDate(), request.getEndDate(), owner);
        projectRepository.save(project);
        return toResponse(project);
    }

    public List<ProjectResponse> getAll(User currentUser) {
        List<Project> projects = currentUser.getRole() == Role.ADMIN
                ? projectRepository.findAll()
                : projectRepository.findAllForUser(currentUser);

        return projects.stream().map(this::toResponse).toList();
    }

    public ProjectResponse getById(Integer id, User currentUser) {
        Project project = findOrThrow(id);
        checkCanView(project, currentUser);
        return toResponse(project);
    }

    public ProjectResponse update(Integer id, ProjectRequest request, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);
        validateDates(request);

        project.setNom(request.getNom());
        project.setDescription(request.getDescription());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());

        projectRepository.save(project);
        return toResponse(project);
    }

    public void delete(Integer id, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);
        projectRepository.delete(project);
    }

    public ProjectResponse inviteMember(Integer id, String email, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);

        User invitee = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun utilisateur avec cet email"));

        if (invitee.getId().equals(project.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le propriétaire est déjà membre du projet");
        }

        if (findMember(project, invitee).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette personne est déjà membre du projet");
        }

        if (verificationTokenRepository
                .findByProjectAndUserAndTypeAndConsumedAtIsNull(project, invitee, TokenType.PROJECT_INVITATION)
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette personne a déjà été invitée");
        }

        VerificationToken invitationToken =
                verificationTokenService.createToken(invitee, TokenType.PROJECT_INVITATION, project);
        emailService.sendProjectInvitation(invitee, currentUser, project, invitationToken.getToken());
        notificationService.notifyProjectInvitation(invitee, currentUser, project);

        return toResponse(project);
    }

    public ProjectResponse cancelInvitation(Integer id, String email, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);

        User invitee = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun utilisateur avec cet email"));

        VerificationToken invitationToken = verificationTokenRepository
                .findByProjectAndUserAndTypeAndConsumedAtIsNull(project, invitee, TokenType.PROJECT_INVITATION)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune invitation en attente pour cette personne"));

        verificationTokenRepository.delete(invitationToken);
        notificationService.resolveInvitationNotifications(invitee, project);
        return toResponse(project);
    }

    public ProjectResponse removeMember(Integer id, String email, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);

        project.getMembers().removeIf(member -> member.getUser().getEmail().equalsIgnoreCase(email));
        projectRepository.save(project);
        return toResponse(project);
    }

    public ProjectResponse updateMemberPermission(Integer id, String email, boolean canManageTasks, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);

        ProjectMember member = project.getMembers().stream()
                .filter(m -> m.getUser().getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ce membre ne fait pas partie du projet"));

        member.setCanManageTasks(canManageTasks);
        projectRepository.save(project);
        return toResponse(project);
    }

    public Project findOrThrow(Integer id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable"));
    }

    public void checkCanView(Project project, User user) {
        boolean isOwner = project.getOwner().getId().equals(user.getId());
        boolean isMember = findMember(project, user).isPresent();
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isOwner && !isMember && !isAdmin) {
            throw new AccessDeniedException("Vous n'avez pas accès à ce projet");
        }
    }

    /**
     * Variante utilisée par JwtChannelInterceptor : contrairement aux requêtes HTTP
     * (couvertes par spring.jpa.open-in-view), le traitement des frames STOMP n'a pas
     * de session Hibernate ambiante. Charger le projet et vérifier l'accès dans la
     * même transaction garde sa collection "members" attachée le temps du contrôle.
     */
    @Transactional(readOnly = true)
    public void checkCanView(Integer projectId, User user) {
        checkCanView(findOrThrow(projectId), user);
    }

    public void checkCanManageTasks(Project project, User user) {
        if (!hasManageRights(project, user)) {
            throw new AccessDeniedException("Vous n'avez pas le droit de gérer les tâches de ce projet");
        }
    }

    public boolean hasManageRights(Project project, User user) {
        boolean isOwner = project.getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean canManage = findMember(project, user).map(ProjectMember::isCanManageTasks).orElse(false);

        return isOwner || isAdmin || canManage;
    }

    public void checkOwner(Project project, User user) {
        boolean isOwner = project.getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Seul le propriétaire du projet peut effectuer cette action");
        }
    }

    private Optional<ProjectMember> findMember(Project project, User user) {
        return project.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .findFirst();
    }

    private void validateDates(ProjectRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin doit être postérieure à la date de début");
        }
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getNom(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getOwner().getEmail(),
                project.getMembers().stream()
                        .map(m -> new ProjectMemberResponse(m.getUser().getEmail(), m.isCanManageTasks()))
                        .sorted((a, b) -> a.email().compareTo(b.email()))
                        .toList(),
                verificationTokenRepository.findByProjectAndTypeAndConsumedAtIsNull(project, TokenType.PROJECT_INVITATION)
                        .stream()
                        .map(t -> t.getUser().getEmail())
                        .sorted()
                        .toList()
        );
    }
}
