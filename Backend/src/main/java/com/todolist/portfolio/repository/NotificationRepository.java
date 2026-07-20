package com.todolist.portfolio.repository;

import com.todolist.portfolio.entity.Notification;
import com.todolist.portfolio.entity.NotificationType;
import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findTop50ByRecipientOrderByCreatedAtDesc(User recipient);

    List<Notification> findByRecipientAndReadAtIsNull(User recipient);

    long countByRecipientAndReadAtIsNull(User recipient);

    Optional<Notification> findByRecipientAndProjectAndTypeAndReadAtIsNull(
            User recipient, Project project, NotificationType type);
}
