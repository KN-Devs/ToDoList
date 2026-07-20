package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.AcceptInvitationRequest;
import com.todolist.portfolio.dto.InvitationAcceptResponse;
import com.todolist.portfolio.service.InvitationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "Acceptation des invitations à un projet")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @SecurityRequirements
    @PostMapping("/accept")
    public InvitationAcceptResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return invitationService.accept(request.token());
    }
}
