package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.NotificationResponse;
import com.todolist.portfolio.dto.UnreadCountResponse;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notifications in-app de l'utilisateur connecté")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return new UnreadCountResponse(notificationService.countUnread(currentUser));
    }

    @GetMapping
    public List<NotificationResponse> getAll(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return notificationService.getForUser(currentUser);
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Integer id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        notificationService.markRead(id, currentUser);
    }

    @PostMapping("/read-all")
    public void markAllRead(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        notificationService.markAllRead(currentUser);
    }
}
