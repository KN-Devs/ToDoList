package com.todolist.portfolio.dto;

import java.time.Instant;

public record CommentResponse(Integer id, String content, String authorEmail, Instant createdAt) {
}
