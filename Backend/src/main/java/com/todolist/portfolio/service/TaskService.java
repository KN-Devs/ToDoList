package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.TaskRequest;
import com.todolist.portfolio.dto.TaskResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;

    public TaskService(TaskRepository taskRepository, ProjectService projectService) {
        this.taskRepository = taskRepository;
        this.projectService = projectService;
    }

    public TaskResponse create(Integer projectId, TaskRequest request, User currentUser) {
        Project project = projectService.findOrThrow(projectId);
        projectService.checkCanManageTasks(project, currentUser);

        Task task = new Task(null, request.getNom(), request.getDescription(), request.getStatus(), currentUser, project);
        taskRepository.save(task);
        return toResponse(task);
    }

    public List<TaskResponse> getAllForProject(Integer projectId, User currentUser) {
        Project project = projectService.findOrThrow(projectId);
        projectService.checkCanView(project, currentUser);

        return taskRepository.findByProject(project).stream().map(this::toResponse).toList();
    }

    public TaskResponse getById(Integer id, User currentUser) {
        Task task = findTaskOrThrow(id);
        projectService.checkCanView(task.getProject(), currentUser);
        return toResponse(task);
    }

    public TaskResponse update(Integer id, TaskRequest request, User currentUser) {
        Task task = findTaskOrThrow(id);
        projectService.checkCanManageTasks(task.getProject(), currentUser);

        task.setNom(request.getNom());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());

        taskRepository.save(task);
        return toResponse(task);
    }

    public void delete(Integer id, User currentUser) {
        Task task = findTaskOrThrow(id);
        projectService.checkCanManageTasks(task.getProject(), currentUser);
        taskRepository.delete(task);
    }

    private Task findTaskOrThrow(Integer id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche introuvable"));
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
