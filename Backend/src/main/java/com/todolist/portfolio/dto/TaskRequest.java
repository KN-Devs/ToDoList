package com.todolist.portfolio.dto;

import com.todolist.portfolio.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class TaskRequest {

    @NotBlank
    @Size(max = 40)
    private String nom;

    @NotBlank
    private String description;

    @NotNull
    private TaskStatus status;

    private LocalDate dueDate;

    public TaskRequest() {
    }

    public TaskRequest(String nom, String description, TaskStatus status) {
        this.nom = nom;
        this.description = description;
        this.status = status;
    }

    public TaskRequest(String nom, String description, TaskStatus status, LocalDate dueDate) {
        this.nom = nom;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
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

    public LocalDate getDueDate() {
        return dueDate;
    }
}
