package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.CommentRequest;
import com.todolist.portfolio.dto.CommentResponse;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.TaskCommentService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@Tag(name = "Commentaires", description = "Commentaires sur une tâche")
public class TaskCommentController {

    private final TaskCommentService commentService;

    public TaskCommentController(TaskCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<CommentResponse> getForTask(@PathVariable Integer taskId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return commentService.getForTask(taskId, currentUser);
    }

    @PostMapping
    public CommentResponse create(@PathVariable Integer taskId, @Valid @RequestBody CommentRequest request,
                                   Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return commentService.create(taskId, request.content(), currentUser);
    }

    @DeleteMapping("/{commentId}")
    public void delete(@PathVariable Integer taskId, @PathVariable Integer commentId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        commentService.delete(taskId, commentId, currentUser);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
