package com.todolist.portfolio.dto;

import java.time.Instant;

public record NotificationResponse(Integer id, String type, String message, Integer projectId, Integer taskId,
                                    Instant createdAt, boolean read) {
}
