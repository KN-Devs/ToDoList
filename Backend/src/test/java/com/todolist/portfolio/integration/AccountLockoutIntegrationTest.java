package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AdminResetPasswordRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==")
@AutoConfigureTestRestTemplate
class AccountLockoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private void register(String email, String password) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, password);
        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
    }

    private void registerAndVerify(String email, String password) {
        register(email, password);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private ResponseEntity<String> attemptLogin(String email, String password) {
        return restTemplate.postForEntity("/api/auth/login", new LoginRequest(email, password), String.class);
    }

    @Test
    void login_afterThreeWrongPasswords_locksAccountEvenWithCorrectPassword() {
        String email = "lockout1@test.com";
        register(email, "Password123!");

        attemptLogin(email, "wrong1");
        attemptLogin(email, "wrong2");
        ResponseEntity<String> thirdAttempt = attemptLogin(email, "wrong3");
        assertThat(thirdAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> correctPasswordAttempt = attemptLogin(email, "Password123!");

        assertThat(correctPasswordAttempt.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void successfulLogin_resetsFailedAttemptCounter() {
        String email = "lockout2@test.com";
        registerAndVerify(email, "Password123!");

        attemptLogin(email, "wrong1");
        attemptLogin(email, "wrong2");

        ResponseEntity<String> successful = attemptLogin(email, "Password123!");
        assertThat(successful.getStatusCode()).isEqualTo(HttpStatus.OK);

        attemptLogin(email, "wrong1");
        ResponseEntity<String> secondWrongAfterReset = attemptLogin(email, "wrong2");

        assertThat(secondWrongAfterReset.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminResetPassword_allowsLoginWithNewPasswordAfterLockout() {
        String email = "lockout3@test.com";
        registerAndVerify(email, "Password123!");

        attemptLogin(email, "wrong1");
        attemptLogin(email, "wrong2");
        attemptLogin(email, "wrong3");

        User lockedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(lockedUser.isAccountNonLocked()).isFalse();

        String adminEmail = "lockout-admin@test.com";
        registerAndVerify(adminEmail, "Password123!");
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        ResponseEntity<AuthResponse> adminLogin = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(adminEmail, "Password123!"), AuthResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminLogin.getBody().token());
        HttpEntity<AdminResetPasswordRequest> resetEntity =
                new HttpEntity<>(new AdminResetPasswordRequest("NewPassword123!"), headers);

        ResponseEntity<Void> resetResponse = restTemplate.exchange(
                "/api/auth/users/" + lockedUser.getId() + "/reset-password",
                HttpMethod.PUT, resetEntity, Void.class);

        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> oldPasswordAttempt = attemptLogin(email, "Password123!");
        assertThat(oldPasswordAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> newPasswordAttempt = attemptLogin(email, "NewPassword123!");
        assertThat(newPasswordAttempt.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void nonAdmin_cannotResetAnotherUsersPassword() {
        String email = "lockout4@test.com";
        register(email, "Password123!");
        User targetUser = userRepository.findByEmail(email).orElseThrow();

        String otherEmail = "lockout4b@test.com";
        ResponseEntity<AuthResponse> otherLogin = restTemplate.postForEntity(
                "/api/auth/register", new RegisterRequest("Nom", "Prenom", otherEmail, "Password123!"), AuthResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(otherLogin.getBody().token());
        HttpEntity<AdminResetPasswordRequest> resetEntity =
                new HttpEntity<>(new AdminResetPasswordRequest("NewPassword123!"), headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/users/" + targetUser.getId() + "/reset-password",
                HttpMethod.PUT, resetEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
