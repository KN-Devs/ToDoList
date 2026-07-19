package com.todolist.portfolio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AddMemberRequest {

    @NotBlank
    @Email
    private String email;

    public AddMemberRequest() {
    }

    public AddMemberRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
