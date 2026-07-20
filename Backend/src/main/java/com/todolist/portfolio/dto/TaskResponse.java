package com.todolist.portfolio.dto;

import com.todolist.portfolio.entity.TaskStatus;

import java.time.LocalDate;

public record TaskResponse(Integer id, String nom, String description, TaskStatus status, String email,
                            LocalDate dueDate) {
}
