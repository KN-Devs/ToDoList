package com.todolist.portfolio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @NotBlank
    @Size(max = 40)
    private String nom;

    @NotBlank
    @Size(max = 40)
    private String prenom;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    public UpdateProfileRequest() {
    }

    public UpdateProfileRequest(String nom, String prenom, String email) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getEmail() {
        return email;
    }
}
