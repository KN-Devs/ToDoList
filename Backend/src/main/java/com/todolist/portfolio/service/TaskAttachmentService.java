package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.AttachmentResponse;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskAttachment;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskAttachmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class TaskAttachmentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final TaskAttachmentRepository attachmentRepository;
    private final TaskService taskService;
    private final ProjectService projectService;

    public TaskAttachmentService(TaskAttachmentRepository attachmentRepository, TaskService taskService,
                                  ProjectService projectService) {
        this.attachmentRepository = attachmentRepository;
        this.taskService = taskService;
        this.projectService = projectService;
    }

    public List<AttachmentResponse> getForTask(Integer taskId, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanView(task.getProject(), currentUser);

        return attachmentRepository.findMetadataByTask(task);
    }

    public AttachmentResponse upload(Integer taskId, MultipartFile file, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanManageTasks(task.getProject(), currentUser);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier est vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Le fichier dépasse la taille maximale autorisée (5 Mo)");
        }

        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "fichier";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier");
        }

        TaskAttachment attachment = new TaskAttachment(
                task, currentUser, filename, contentType, file.getSize(), content, Instant.now());
        attachmentRepository.save(attachment);
        return toResponse(attachment);
    }

    public TaskAttachment findForDownload(Integer taskId, Integer attachmentId, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanView(task.getProject(), currentUser);

        return findAttachmentInTask(taskId, attachmentId);
    }

    public void delete(Integer taskId, Integer attachmentId, User currentUser) {
        Task task = taskService.findTaskOrThrow(taskId);
        projectService.checkCanManageTasks(task.getProject(), currentUser);

        attachmentRepository.delete(findAttachmentInTask(taskId, attachmentId));
    }

    private TaskAttachment findAttachmentInTask(Integer taskId, Integer attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .filter(a -> a.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pièce jointe introuvable"));
    }

    private AttachmentResponse toResponse(TaskAttachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedBy().getEmail(),
                attachment.getCreatedAt()
        );
    }
}
