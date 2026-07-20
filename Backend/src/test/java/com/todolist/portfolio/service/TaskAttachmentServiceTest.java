package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.AttachmentResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskAttachment;
import com.todolist.portfolio.entity.TaskStatus;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAttachmentServiceTest {

    @Mock
    private TaskAttachmentRepository attachmentRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private TaskAttachmentService taskAttachmentService;

    private User bob;
    private User carol;
    private Project bobProject;
    private Task bobTask;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
        carol = new User(3, "Petit", "Carol", "carol@test.com", "hash", Role.USER);
        bobProject = new Project(10, "Projet de Bob", "description",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), bob);
        bobTask = new Task(20, "Tache", "description", TaskStatus.TODO, bob, bobProject);
        when(taskService.findTaskOrThrow(20)).thenReturn(bobTask);
    }

    @Test
    void getForTask_returnsMetadata() {
        AttachmentResponse metadata = new AttachmentResponse(1, "plan.pdf", "application/pdf", 1024, "bob@test.com", Instant.now());
        when(attachmentRepository.findMetadataByTask(bobTask)).thenReturn(List.of(metadata));

        List<AttachmentResponse> result = taskAttachmentService.getForTask(20, bob);

        assertThat(result).containsExactly(metadata);
        verify(projectService).checkCanView(bobProject, bob);
    }

    @Test
    void upload_whenManageRights_savesAttachment() {
        MockMultipartFile file = new MockMultipartFile("file", "plan.pdf", "application/pdf", "contenu".getBytes());

        AttachmentResponse result = taskAttachmentService.upload(20, file, bob);

        assertThat(result.filename()).isEqualTo("plan.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.uploadedByEmail()).isEqualTo("bob@test.com");
        verify(attachmentRepository).save(any(TaskAttachment.class));
    }

    @Test
    void upload_whenNotAllowed_throwsAccessDenied() {
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanManageTasks(bobProject, carol);
        MockMultipartFile file = new MockMultipartFile("file", "plan.pdf", "application/pdf", "contenu".getBytes());

        assertThrows(AccessDeniedException.class, () -> taskAttachmentService.upload(20, file, carol));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_whenEmptyFile_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "vide.txt", "text/plain", new byte[0]);

        assertThrows(ResponseStatusException.class, () -> taskAttachmentService.upload(20, file, bob));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_whenFileTooLarge_throwsPayloadTooLarge() {
        byte[] tooLarge = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "gros.bin", "application/octet-stream", tooLarge);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> taskAttachmentService.upload(20, file, bob));
        assertThat(ex.getStatusCode().value()).isEqualTo(413);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void findForDownload_whenAllowed_returnsAttachment() {
        TaskAttachment attachment = new TaskAttachment(bobTask, bob, "plan.pdf", "application/pdf", 7,
                "contenu".getBytes(), Instant.now());
        attachment.setId(1);
        when(attachmentRepository.findById(1)).thenReturn(Optional.of(attachment));

        TaskAttachment result = taskAttachmentService.findForDownload(20, 1, carol);

        assertThat(result.getFilename()).isEqualTo("plan.pdf");
        verify(projectService).checkCanView(bobProject, carol);
    }

    @Test
    void findForDownload_whenBelongsToAnotherTask_throwsNotFound() {
        Task otherTask = new Task(21, "Autre", "description", TaskStatus.TODO, bob, bobProject);
        TaskAttachment attachment = new TaskAttachment(otherTask, bob, "plan.pdf", "application/pdf", 7,
                "contenu".getBytes(), Instant.now());
        attachment.setId(1);
        when(attachmentRepository.findById(1)).thenReturn(Optional.of(attachment));

        assertThrows(ResponseStatusException.class, () -> taskAttachmentService.findForDownload(20, 1, bob));
    }

    @Test
    void delete_whenManageRights_deletesAttachment() {
        TaskAttachment attachment = new TaskAttachment(bobTask, bob, "plan.pdf", "application/pdf", 7,
                "contenu".getBytes(), Instant.now());
        attachment.setId(1);
        when(attachmentRepository.findById(1)).thenReturn(Optional.of(attachment));

        taskAttachmentService.delete(20, 1, bob);

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void delete_whenNotAllowed_throwsAccessDenied() {
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanManageTasks(bobProject, carol);

        assertThrows(AccessDeniedException.class, () -> taskAttachmentService.delete(20, 1, carol));
        verify(attachmentRepository, never()).delete(any());
    }
}
