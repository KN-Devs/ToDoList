package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public TaskResponse create(TaskRequest request, User currentUser) {
        Task task = new Task(null, request.getNom(), request.getDescription(), request.getStatus(), currentUser);
        taskRepository.save(task);
        return toResponse(task);
    }

    public List<TaskResponse> getAll(User currentUser) {
        List<Task> tasks = currentUser.getRole() == Role.ADMIN
                ? taskRepository.findAll()
                : taskRepository.findByUser(currentUser);

        return tasks.stream().map(this::toResponse).toList();
    }

    public TaskResponse getById(Integer id, User currentUser) {
        Task task = findTaskOrThrow(id);
        checkOwnership(task, currentUser);
        return toResponse(task);
    }

    public TaskResponse update(Integer id, TaskRequest request, User currentUser) {
        Task task = findTaskOrThrow(id);
        checkOwnership(task, currentUser);

        task.setNom(request.getNom());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());

        taskRepository.save(task);
        return toResponse(task);
    }

    public void delete(Integer id, User currentUser) {
        Task task = findTaskOrThrow(id);
        checkOwnership(task, currentUser);
        taskRepository.delete(task);
    }

    private Task findTaskOrThrow(Integer id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche introuvable"));
    }

    private void checkOwnership(Task task, User currentUser) {
        boolean isOwner = task.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Vous n'avez pas accès à cette tâche");
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getNom(),
                task.getDescription(),
                task.getStatus(),
                task.getUser().getEmail()
        );
    }
}
