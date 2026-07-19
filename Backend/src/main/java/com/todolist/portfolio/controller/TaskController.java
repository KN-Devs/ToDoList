package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    public TaskResponse create(@PathVariable Integer projectId, @Valid @RequestBody TaskRequest request,
                                Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return taskService.create(projectId, request, currentUser);
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public List<TaskResponse> getAllForProject(@PathVariable Integer projectId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return taskService.getAllForProject(projectId, currentUser);
    }

    @GetMapping("/api/tasks/{id}")
    public TaskResponse getById(@PathVariable Integer id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return taskService.getById(id, currentUser);
    }

    @PutMapping("/api/tasks/{id}")
    public TaskResponse update(@PathVariable Integer id, @Valid @RequestBody TaskRequest request, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return taskService.update(id, request, currentUser);
    }

    @DeleteMapping("/api/tasks/{id}")
    public void delete(@PathVariable Integer id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        taskService.delete(id, currentUser);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
