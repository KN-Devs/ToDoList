package com.todolist.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(@NotBlank @Size(max = 2000) String content) {
}
