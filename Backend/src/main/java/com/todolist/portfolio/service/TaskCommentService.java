package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.CommentResponse;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskComment;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskCommentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class TaskCommentService {

    private final TaskCommentRepository commentRepository;
    private final TaskService taskService;
    private final ProjectService projectService;

    public TaskCommentService(TaskCommentRepository commentRepository, TaskService taskService,
                               ProjectService projectService) {
        this.commentRepository = commentRepository;
        this.taskService = taskService;
        this.projectService = projectService;
    }

    public List<CommentResponse> getForTask(Integer taskId, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanView(task.getProject(), currentUser);

        return commentRepository.findByTaskOrderByCreatedAtAsc(task).stream()
                .map(this::toResponse)
                .toList();
    }

    public CommentResponse create(Integer taskId, String content, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanView(task.getProject(), currentUser);

        TaskComment comment = new TaskComment(task, currentUser, content, Instant.now());
        commentRepository.save(comment);
        return toResponse(comment);
    }

    public void delete(Integer taskId, Integer commentId, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanView(task.getProject(), currentUser);

        TaskComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire introuvable"));

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Vous ne pouvez supprimer que vos propres commentaires");
        }

        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(TaskComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getEmail(),
                comment.getCreatedAt()
        );
    }
}
