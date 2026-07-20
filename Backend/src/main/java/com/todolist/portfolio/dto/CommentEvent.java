package com.todolist.portfolio.dto;

public record CommentEvent(String action, Integer taskId, CommentResponse comment) {
}
