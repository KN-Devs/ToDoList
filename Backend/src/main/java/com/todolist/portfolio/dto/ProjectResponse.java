package com.todolist.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record ProjectResponse(
        Integer id,
        String nom,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String ownerEmail,
        List<ProjectMemberResponse> members
) {
}
