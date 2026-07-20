package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.CommentResponse;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskComment;
import com.todolist.portfolio.entity.TaskStatus;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.TaskCommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class TaskCommentServiceTest {

    @Mock
    private TaskCommentRepository commentRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private ProjectService projectService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TaskCommentService taskCommentService;

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
    void getForTask_returnsCommentsInOrder() {
        TaskComment comment = new TaskComment(bobTask, carol, "Un commentaire", Instant.now());
        when(commentRepository.findByTaskOrderByCreatedAtAsc(bobTask)).thenReturn(List.of(comment));

        List<CommentResponse> result = taskCommentService.getForTask(20, bob);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).authorEmail()).isEqualTo("carol@test.com");
        verify(projectService).checkCanView(bobProject, bob);
    }

    @Test
    void getForTask_whenNoAccess_throwsAccessDenied() {
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanView(bobProject, carol);

        assertThrows(AccessDeniedException.class, () -> taskCommentService.getForTask(20, carol));
    }

    @Test
    void create_savesCommentAuthoredByCurrentUser() {
        CommentResponse result = taskCommentService.create(20, "Bonjour", carol);

        assertThat(result.content()).isEqualTo("Bonjour");
        assertThat(result.authorEmail()).isEqualTo("carol@test.com");
        verify(commentRepository).save(any(TaskComment.class));
    }

    @Test
    void create_whenCommenterIsNotTaskOwner_notifiesTaskOwner() {
        taskCommentService.create(20, "Bonjour", carol);

        verify(notificationService).notifyTaskComment(bob, carol, bobTask);
    }

    @Test
    void create_whenCommentingOnOwnTask_doesNotNotify() {
        taskCommentService.create(20, "Bonjour", bob);

        verify(notificationService, never()).notifyTaskComment(any(), any(), any());
    }

    @Test
    void create_whenNoAccess_throwsAccessDenied() {
        doThrow(new AccessDeniedException("no")).when(projectService).checkCanView(bobProject, carol);

        assertThrows(AccessDeniedException.class, () -> taskCommentService.create(20, "Bonjour", carol));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void delete_whenAuthor_deletesComment() {
        TaskComment comment = new TaskComment(bobTask, carol, "Bonjour", Instant.now());
        comment.setId(5);
        when(commentRepository.findById(5)).thenReturn(Optional.of(comment));

        taskCommentService.delete(20, 5, carol);

        verify(commentRepository).delete(comment);
    }

    @Test
    void delete_whenNotAuthor_throwsAccessDenied() {
        TaskComment comment = new TaskComment(bobTask, carol, "Bonjour", Instant.now());
        comment.setId(5);
        when(commentRepository.findById(5)).thenReturn(Optional.of(comment));

        assertThrows(AccessDeniedException.class, () -> taskCommentService.delete(20, 5, bob));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void delete_whenCommentBelongsToAnotherTask_throwsNotFound() {
        Task otherTask = new Task(21, "Autre", "description", TaskStatus.TODO, bob, bobProject);
        TaskComment comment = new TaskComment(otherTask, carol, "Bonjour", Instant.now());
        comment.setId(5);
        when(commentRepository.findById(5)).thenReturn(Optional.of(comment));

        assertThrows(ResponseStatusException.class, () -> taskCommentService.delete(20, 5, carol));
    }

    @Test
    void delete_whenCommentUnknown_throwsNotFound() {
        when(commentRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> taskCommentService.delete(20, 999, bob));
    }
}
