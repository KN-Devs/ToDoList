package com.todolist.portfolio.dto;

public class RegisterRequest {
    private String nom;
    private String prenom;
    private String email;
    private String password;

    public RegisterRequest() {
    }

    public RegisterRequest(String nom, String prenom, String email, String password) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.password = password;
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

    public String getPassword() {
        return password;
    }
}
