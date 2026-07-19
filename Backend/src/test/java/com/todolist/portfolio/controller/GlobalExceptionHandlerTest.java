package com.todolist.portfolio.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDataIntegrityViolation_returns409WithGenericMessage() {
        ResponseEntity<String> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).doesNotContain("constraint", "users_email_key", "SQLState");
    }

    @Test
    void handleResponseStatusException_preservesStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Tâche introuvable");

        ResponseEntity<String> response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("Tâche introuvable");
    }

    @Test
    void handleUnexpectedException_returns500WithoutLeakingInternals() {
        RuntimeException ex = new RuntimeException("NullPointerException at com.todolist.portfolio.internal.SecretClass");

        ResponseEntity<String> response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).doesNotContain("NullPointerException", "com.todolist.portfolio.internal");
    }
}
