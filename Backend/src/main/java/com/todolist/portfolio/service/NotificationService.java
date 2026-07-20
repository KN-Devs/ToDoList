package com.todolist.portfolio.service;

import com.todolist.portfolio.dto.NotificationResponse;
import com.todolist.portfolio.entity.Notification;
import com.todolist.portfolio.entity.NotificationType;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Task;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.NotificationRepository;
import com.todolist.portfolio.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

@Service
public class NotificationService {

    private static final int DUE_SOON_DAYS = 2;

    private final NotificationRepository notificationRepository;
    private final TaskRepository taskRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, TaskRepository taskRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.taskRepository = taskRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyProjectInvitation(User recipient, User inviter, Project project) {
        String message = inviter.getPrenom() + " " + inviter.getNom()
                + " vous a invité à rejoindre le projet \"" + project.getNom() + "\"";
        Notification notification = new Notification(
                recipient, NotificationType.PROJECT_INVITATION, message, project, null, Instant.now());
        notificationRepository.save(notification);
        push(notification);
    }

    public void notifyTaskComment(User recipient, User commenter, Task task) {
        String message = commenter.getPrenom() + " " + commenter.getNom()
                + " a commenté la tâche \"" + task.getNom() + "\"";
        Notification notification = new Notification(
                recipient, NotificationType.TASK_COMMENT, message, task.getProject(), task, Instant.now());
        notificationRepository.save(notification);
        push(notification);
    }

    private void push(Notification notification) {
        messagingTemplate.convertAndSendToUser(
                notification.getRecipient().getEmail(), "/queue/notifications", toResponse(notification));
    }

    /** Invitation acceptée ou annulée : la notification correspondante n'a plus lieu d'être. */
    public void resolveInvitationNotifications(User recipient, Project project) {
        notificationRepository
                .findByRecipientAndProjectAndTypeAndReadAtIsNull(recipient, project, NotificationType.PROJECT_INVITATION)
                .ifPresent(notification -> {
                    notification.setReadAt(Instant.now());
                    notificationRepository.save(notification);
                });
    }

    public long countUnread(User user) {
        long persisted = notificationRepository.countByRecipientAndReadAtIsNull(user);
        long dueDate = taskRepository.findDueSoonOrOverdueForUser(user, LocalDate.now().plusDays(DUE_SOON_DAYS)).size();
        return persisted + dueDate;
    }

    public List<NotificationResponse> getForUser(User user) {
        List<NotificationResponse> dueDateNotifications = taskRepository
                .findDueSoonOrOverdueForUser(user, LocalDate.now().plusDays(DUE_SOON_DAYS))
                .stream()
                .map(this::toDueDateResponse)
                .toList();

        List<NotificationResponse> persisted = notificationRepository.findTop50ByRecipientOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();

        return Stream.concat(dueDateNotifications.stream(), persisted.stream()).toList();
    }

    public void markRead(Integer notificationId, User currentUser) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(currentUser.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));

        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
    }

    public void markAllRead(User user) {
        List<Notification> unread = notificationRepository.findByRecipientAndReadAtIsNull(user);
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        notificationRepository.saveAll(unread);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getMessage(),
                notification.getProject() != null ? notification.getProject().getId() : null,
                notification.getTask() != null ? notification.getTask().getId() : null,
                notification.getCreatedAt(),
                notification.getReadAt() != null
        );
    }

    private NotificationResponse toDueDateResponse(Task task) {
        boolean overdue = task.getDueDate().isBefore(LocalDate.now());
        String type = overdue ? "TASK_OVERDUE" : "TASK_DUE_SOON";
        String message = (overdue ? "En retard : \"" : "Échéance proche : \"")
                + task.getNom() + "\" (" + task.getDueDate() + ")";
        Instant createdAt = task.getDueDate().atStartOfDay(ZoneOffset.UTC).toInstant();

        return new NotificationResponse(null, type, message, task.getProject().getId(), task.getId(), createdAt, false);
    }
}
