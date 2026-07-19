package com.todolist.portfolio.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberPermissionRequest(@NotNull Boolean canManageTasks) {
}
