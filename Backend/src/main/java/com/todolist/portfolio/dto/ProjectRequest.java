package com.todolist.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ProjectRequest {

    @NotBlank
    @Size(max = 100)
    private String nom;

    @NotBlank
    private String description;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    public ProjectRequest() {
    }

    public ProjectRequest(String nom, String description, LocalDate startDate, LocalDate endDate) {
        this.nom = nom;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getNom() {
        return nom;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
