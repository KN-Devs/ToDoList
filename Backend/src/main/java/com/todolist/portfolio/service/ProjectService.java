package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.ProjectRepository;
import com.todolist.portfolio.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
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
        checkMembership(project, currentUser);
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

    public ProjectResponse addMember(Integer id, String email, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);

        User newMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun utilisateur avec cet email"));

        if (newMember.getId().equals(project.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le propriétaire est déjà membre du projet");
        }

        project.getMembers().add(newMember);
        projectRepository.save(project);
        return toResponse(project);
    }

    public ProjectResponse removeMember(Integer id, String email, User currentUser) {
        Project project = findOrThrow(id);
        checkOwner(project, currentUser);

        project.getMembers().removeIf(member -> member.getEmail().equalsIgnoreCase(email));
        projectRepository.save(project);
        return toResponse(project);
    }

    private Project findOrThrow(Integer id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable"));
    }

    private void checkMembership(Project project, User user) {
        boolean isOwner = project.getOwner().getId().equals(user.getId());
        boolean isMember = project.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isOwner && !isMember && !isAdmin) {
            throw new AccessDeniedException("Vous n'avez pas accès à ce projet");
        }
    }

    private void checkOwner(Project project, User user) {
        boolean isOwner = project.getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Seul le propriétaire du projet peut effectuer cette action");
        }
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
                project.getMembers().stream().map(User::getEmail).sorted().toList()
        );
    }
}
