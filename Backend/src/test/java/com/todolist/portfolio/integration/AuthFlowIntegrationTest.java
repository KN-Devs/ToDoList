package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.ConfirmEmailRequest;
import com.todolist.portfolio.dto.EmailOnlyRequest;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.ResetPasswordRequest;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.UserRepository;
import com.todolist.portfolio.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==")
@AutoConfigureTestRestTemplate
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    private void register(String email) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "Password123!");
        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
    }

    private String pendingEmailVerificationToken(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return verificationTokenRepository.findByUserAndTypeAndConsumedAtIsNull(user, TokenType.EMAIL_VERIFICATION)
                .get(0)
                .getToken();
    }

    @Test
    void login_whenEmailNotVerified_returns403() {
        String email = "authflow1@test.com";
        register(email);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void confirmEmail_thenLogin_succeeds() {
        String email = "authflow2@test.com";
        register(email);
        String token = pendingEmailVerificationToken(email);

        ResponseEntity<Void> confirmResponse = restTemplate.postForEntity(
                "/api/auth/confirm-email", new ConfirmEmailRequest(token), Void.class);
        assertThat(confirmResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(loginResponse.getBody().token()).isNotBlank();
    }

    @Test
    void confirmEmail_withUnknownToken_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/confirm-email", new ConfirmEmailRequest("does-not-exist"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void confirmEmail_withAlreadyConsumedToken_returns410() {
        String email = "authflow3@test.com";
        register(email);
        String token = pendingEmailVerificationToken(email);

        restTemplate.postForEntity("/api/auth/confirm-email", new ConfirmEmailRequest(token), Void.class);
        ResponseEntity<String> secondAttempt = restTemplate.postForEntity(
                "/api/auth/confirm-email", new ConfirmEmailRequest(token), String.class);

        assertThat(secondAttempt.getStatusCode().value()).isEqualTo(410);
    }

    @Test
    void resendConfirmation_forUnverifiedUser_replacesToken() {
        String email = "authflow4@test.com";
        register(email);
        String firstToken = pendingEmailVerificationToken(email);

        restTemplate.postForEntity("/api/auth/resend-confirmation", new EmailOnlyRequest(email), Void.class);

        String secondToken = pendingEmailVerificationToken(email);
        assertThat(secondToken).isNotEqualTo(firstToken);

        ResponseEntity<String> oldTokenAttempt = restTemplate.postForEntity(
                "/api/auth/confirm-email", new ConfirmEmailRequest(firstToken), String.class);
        assertThat(oldTokenAttempt.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void resendConfirmation_forUnknownEmail_stillReturns200() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/auth/resend-confirmation", new EmailOnlyRequest("ghost@test.com"), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void forgotPassword_thenResetPassword_allowsLoginWithNewPassword() {
        String email = "authflow5@test.com";
        register(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        restTemplate.postForEntity("/api/auth/forgot-password", new EmailOnlyRequest(email), Void.class);
        String resetToken = verificationTokenRepository
                .findByUserAndTypeAndConsumedAtIsNull(user, TokenType.PASSWORD_RESET)
                .get(0)
                .getToken();

        ResponseEntity<Void> resetResponse = restTemplate.postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(resetToken, "NewPassword123!"), Void.class);
        assertThat(resetResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> oldPasswordAttempt = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), String.class);
        assertThat(oldPasswordAttempt.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<AuthResponse> newPasswordAttempt = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "NewPassword123!"), AuthResponse.class);
        assertThat(newPasswordAttempt.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void forgotPassword_forUnknownEmail_stillReturns200() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/auth/forgot-password", new EmailOnlyRequest("ghost2@test.com"), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void resetPassword_withExpiredToken_returns410() {
        String email = "authflow6@test.com";
        register(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        restTemplate.postForEntity("/api/auth/forgot-password", new EmailOnlyRequest(email), Void.class);
        VerificationToken token = verificationTokenRepository
                .findByUserAndTypeAndConsumedAtIsNull(user, TokenType.PASSWORD_RESET)
                .get(0);
        token.setExpiresAt(Instant.now().minusSeconds(60));
        verificationTokenRepository.save(token);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(token.getToken(), "NewPassword123!"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(410);
    }
}
