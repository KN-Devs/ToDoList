package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.ResetPasswordRequest;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.UserRepository;
import com.todolist.portfolio.repository.VerificationTokenRepository;
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
class RefreshTokenIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    private AuthResponse registerAndVerify(String email) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "Password123!");
        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);
        return loginResponse.getBody();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void login_returnsAccessAndRefreshTokens() {
        AuthResponse auth = registerAndVerify("refresh1@test.com");

        assertThat(auth.token()).isNotBlank();
        assertThat(auth.refreshToken()).isNotBlank();
    }

    @Test
    void refresh_withValidToken_returnsNewWorkingAccessToken() {
        AuthResponse auth = registerAndVerify("refresh2@test.com");

        ResponseEntity<AuthResponse> refreshResponse = restTemplate.postForEntity(
                "/api/auth/refresh", java.util.Map.of("refreshToken", auth.refreshToken()), AuthResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccessToken = refreshResponse.getBody().token();
        assertThat(newAccessToken).isNotBlank();

        HttpEntity<Void> meEntity = new HttpEntity<>(authHeaders(newAccessToken));
        ResponseEntity<User> meResponse = restTemplate.exchange("/api/auth/me", HttpMethod.GET, meEntity, User.class);
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody().getEmail()).isEqualTo("refresh2@test.com");
    }

    @Test
    void refresh_withUnknownToken_returns401() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/refresh", java.util.Map.of("refreshToken", "does-not-exist"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_withExpiredOrMissingAuth_returns401NotForbidden() {
        HttpEntity<Void> noAuthEntity = new HttpEntity<>(new HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET, noAuthEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_revokesRefreshToken_thenRefreshFails() {
        AuthResponse auth = registerAndVerify("refresh3@test.com");

        ResponseEntity<Void> logoutResponse = restTemplate.postForEntity(
                "/api/auth/logout", java.util.Map.of("refreshToken", auth.refreshToken()), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
                "/api/auth/refresh", java.util.Map.of("refreshToken", auth.refreshToken()), String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void resettingPassword_revokesExistingRefreshTokens() {
        String email = "refresh4@test.com";
        AuthResponse auth = registerAndVerify(email);

        restTemplate.postForEntity("/api/auth/forgot-password", java.util.Map.of("email", email), Void.class);
        User user = userRepository.findByEmail(email).orElseThrow();
        String resetToken = verificationTokenRepository
                .findByUserAndTypeAndConsumedAtIsNull(user, TokenType.PASSWORD_RESET)
                .get(0)
                .getToken();

        restTemplate.postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(resetToken, "NewPassword123!"), Void.class);

        ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
                "/api/auth/refresh", java.util.Map.of("refreshToken", auth.refreshToken()), String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
