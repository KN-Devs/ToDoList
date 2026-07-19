package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.AddMemberRequest;
import com.todolist.portfolio.dto.ProjectRequest;
import com.todolist.portfolio.dto.ProjectResponse;
import com.todolist.portfolio.dto.UpdateMemberPermissionRequest;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.create(request, currentUser);
    }

    @GetMapping
    public List<ProjectResponse> getAll(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.getAll(currentUser);
    }

    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable Integer id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.getById(id, currentUser);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Integer id, @Valid @RequestBody ProjectRequest request,
                                   Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.update(id, request, currentUser);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        projectService.delete(id, currentUser);
    }

    @PostMapping("/{id}/members")
    public ProjectResponse addMember(@PathVariable Integer id, @Valid @RequestBody AddMemberRequest request,
                                      Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.addMember(id, request.getEmail(), currentUser);
    }

    @DeleteMapping("/{id}/members/{email}")
    public ProjectResponse removeMember(@PathVariable Integer id, @PathVariable String email,
                                         Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.removeMember(id, email, currentUser);
    }

    @PatchMapping("/{id}/members/{email}")
    public ProjectResponse updateMemberPermission(@PathVariable Integer id, @PathVariable String email,
                                                    @Valid @RequestBody UpdateMemberPermissionRequest request,
                                                    Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return projectService.updateMemberPermission(id, email, request.canManageTasks(), currentUser);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
