package com.todolist.portfolio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class InviteMemberRequest {

    @NotBlank
    @Email
    private String email;

    public InviteMemberRequest() {
    }

    public InviteMemberRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
