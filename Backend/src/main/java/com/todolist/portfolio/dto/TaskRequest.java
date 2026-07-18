package com.todolist.portfolio.dto;

import com.todolist.portfolio.entity.TaskStatus;

public class TaskRequest {

    private String nom;
    private String description;
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
