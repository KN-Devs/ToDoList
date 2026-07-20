package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.AttachmentResponse;
import com.todolist.portfolio.entity.TaskAttachment;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.TaskAttachmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
@Tag(name = "Pièces jointes", description = "Fichiers attachés à une tâche")
public class TaskAttachmentController {

    private final TaskAttachmentService attachmentService;

    public TaskAttachmentController(TaskAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentResponse> getForTask(@PathVariable Integer taskId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return attachmentService.getForTask(taskId, currentUser);
    }

    @PostMapping
    public AttachmentResponse upload(@PathVariable Integer taskId, @RequestParam("file") MultipartFile file,
                                      Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return attachmentService.upload(taskId, file, currentUser);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Integer taskId, @PathVariable Integer attachmentId,
                                            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        TaskAttachment attachment = attachmentService.findForDownload(taskId, attachmentId, currentUser);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(attachment.getFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(attachment.getContent());
    }

    @DeleteMapping("/{attachmentId}")
    public void delete(@PathVariable Integer taskId, @PathVariable Integer attachmentId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        attachmentService.delete(taskId, attachmentId, currentUser);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
