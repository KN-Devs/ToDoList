package com.todolist.portfolio.dto;

import com.todolist.portfolio.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TaskRequest {

    @NotBlank
    @Size(max = 40)
    private String nom;

    @NotBlank
    private String description;

    @NotNull
    private TaskStatus status;

    public TaskRequest() {
    }

    public TaskRequest(String nom, String description, TaskStatus status) {
        this.nom = nom;
        this.description = description;
        this.status = status;
    }

    public String getNom() {
        return nom;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }
}
