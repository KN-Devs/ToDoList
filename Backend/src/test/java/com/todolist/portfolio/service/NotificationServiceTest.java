package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.NotificationResponse;
import com.todolist.portfolio.entity.Notification;
import com.todolist.portfolio.entity.NotificationType;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.TaskStatus;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.NotificationRepository;
import com.todolist.portfolio.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private NotificationService notificationService;

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
    }

    @Test
    void notifyProjectInvitation_savesANotificationForTheInvitee() {
        notificationService.notifyProjectInvitation(carol, bob, bobProject);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getRecipient()).isEqualTo(carol);
        assertThat(saved.getType()).isEqualTo(NotificationType.PROJECT_INVITATION);
        assertThat(saved.getProject()).isEqualTo(bobProject);
        assertThat(saved.getMessage()).contains("Bob Dupont").contains("Projet de Bob");
    }

    @Test
    void notifyTaskComment_savesANotificationForTheTaskOwner() {
        notificationService.notifyTaskComment(bob, carol, bobTask);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getRecipient()).isEqualTo(bob);
        assertThat(saved.getType()).isEqualTo(NotificationType.TASK_COMMENT);
        assertThat(saved.getTask()).isEqualTo(bobTask);
        assertThat(saved.getProject()).isEqualTo(bobProject);
    }

    @Test
    void resolveInvitationNotifications_marksMatchingNotificationRead() {
        Notification notification = new Notification(carol, NotificationType.PROJECT_INVITATION, "msg",
                bobProject, null, Instant.now());
        when(notificationRepository.findByRecipientAndProjectAndTypeAndReadAtIsNull(
                carol, bobProject, NotificationType.PROJECT_INVITATION)).thenReturn(Optional.of(notification));

        notificationService.resolveInvitationNotifications(carol, bobProject);

        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void resolveInvitationNotifications_whenNoneFound_doesNothing() {
        when(notificationRepository.findByRecipientAndProjectAndTypeAndReadAtIsNull(
                carol, bobProject, NotificationType.PROJECT_INVITATION)).thenReturn(Optional.empty());

        notificationService.resolveInvitationNotifications(carol, bobProject);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void countUnread_combinesPersistedAndDueDateCounts() {
        when(notificationRepository.countByRecipientAndReadAtIsNull(bob)).thenReturn(2L);
        when(taskRepository.findDueSoonOrOverdueForUser(eq(bob), any(LocalDate.class)))
                .thenReturn(List.of(bobTask));

        long count = notificationService.countUnread(bob);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void getForUser_putsDueDateNotificationsBeforePersistedOnes() {
        Task overdueTask = new Task(21, "En retard", "description", TaskStatus.TODO, bob, bobProject);
        overdueTask.setDueDate(LocalDate.now().minusDays(1));
        when(taskRepository.findDueSoonOrOverdueForUser(eq(bob), any(LocalDate.class)))
                .thenReturn(List.of(overdueTask));

        Notification persisted = new Notification(bob, NotificationType.TASK_COMMENT, "Un commentaire",
                bobProject, bobTask, Instant.now());
        persisted.setId(99);
        when(notificationRepository.findTop50ByRecipientOrderByCreatedAtDesc(bob)).thenReturn(List.of(persisted));

        List<NotificationResponse> result = notificationService.getForUser(bob);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo("TASK_OVERDUE");
        assertThat(result.get(0).id()).isNull();
        assertThat(result.get(1).type()).isEqualTo("TASK_COMMENT");
        assertThat(result.get(1).id()).isNotNull();
    }

    @Test
    void getForUser_flagsDueSoonWhenNotYetOverdue() {
        Task soonTask = new Task(22, "Bientot", "description", TaskStatus.TODO, bob, bobProject);
        soonTask.setDueDate(LocalDate.now().plusDays(1));
        when(taskRepository.findDueSoonOrOverdueForUser(eq(bob), any(LocalDate.class))).thenReturn(List.of(soonTask));
        when(notificationRepository.findTop50ByRecipientOrderByCreatedAtDesc(bob)).thenReturn(List.of());

        List<NotificationResponse> result = notificationService.getForUser(bob);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("TASK_DUE_SOON");
    }

    @Test
    void markRead_whenRecipientMatches_setsReadAt() {
        Notification notification = new Notification(bob, NotificationType.TASK_COMMENT, "msg",
                bobProject, bobTask, Instant.now());
        notification.setId(7);
        when(notificationRepository.findById(7)).thenReturn(Optional.of(notification));

        notificationService.markRead(7, bob);

        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_whenRecipientDoesNotMatch_throwsNotFound() {
        Notification notification = new Notification(bob, NotificationType.TASK_COMMENT, "msg",
                bobProject, bobTask, Instant.now());
        notification.setId(7);
        when(notificationRepository.findById(7)).thenReturn(Optional.of(notification));

        assertThrows(ResponseStatusException.class, () -> notificationService.markRead(7, carol));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_whenUnknown_throwsNotFound() {
        when(notificationRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> notificationService.markRead(999, bob));
    }

    @Test
    void markAllRead_marksEveryUnreadNotification() {
        Notification first = new Notification(bob, NotificationType.TASK_COMMENT, "msg1", bobProject, bobTask, Instant.now());
        Notification second = new Notification(bob, NotificationType.PROJECT_INVITATION, "msg2", bobProject, null, Instant.now());
        when(notificationRepository.findByRecipientAndReadAtIsNull(bob)).thenReturn(List.of(first, second));

        notificationService.markAllRead(bob);

        assertThat(first.getReadAt()).isNotNull();
        assertThat(second.getReadAt()).isNotNull();
        verify(notificationRepository).saveAll(List.of(first, second));
    }
}
