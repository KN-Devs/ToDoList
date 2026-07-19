package com.todolist.portfolio.integration;

import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.UpdateProfileRequest;
import com.todolist.portfolio.entity.User;
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
class ProfileIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    private String registerAndGetToken(String email) {
        RegisterRequest request = new RegisterRequest("Nom", "Prenom", email, "Password123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void updateProfile_changesNameAndEmail_andNewTokenWorks() {
        String token = registerAndGetToken("profile1@test.com");

        UpdateProfileRequest update = new UpdateProfileRequest("Dupont", "Marie", "profile1-new@test.com");
        HttpEntity<UpdateProfileRequest> updateEntity = new HttpEntity<>(update, authHeaders(token));
        ResponseEntity<AuthResponse> updateResponse =
                restTemplate.exchange("/api/auth/me", HttpMethod.PUT, updateEntity, AuthResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newToken = updateResponse.getBody().token();
        assertThat(newToken).isNotBlank();

        HttpEntity<Void> meEntity = new HttpEntity<>(authHeaders(newToken));
        ResponseEntity<User> meResponse = restTemplate.exchange("/api/auth/me", HttpMethod.GET, meEntity, User.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody().getNom()).isEqualTo("Dupont");
        assertThat(meResponse.getBody().getPrenom()).isEqualTo("Marie");
        assertThat(meResponse.getBody().getEmail()).isEqualTo("profile1-new@test.com");
    }

    @Test
    void updateProfile_withEmailTakenByAnotherUser_returns409() {
        registerAndGetToken("profile2-taken@test.com");
        String token = registerAndGetToken("profile2@test.com");

        UpdateProfileRequest update = new UpdateProfileRequest("Nom", "Prenom", "profile2-taken@test.com");
        HttpEntity<UpdateProfileRequest> updateEntity = new HttpEntity<>(update, authHeaders(token));
        ResponseEntity<String> response =
                restTemplate.exchange("/api/auth/me", HttpMethod.PUT, updateEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateProfile_withBlankName_returns400() {
        String token = registerAndGetToken("profile3@test.com");

        UpdateProfileRequest update = new UpdateProfileRequest("", "Prenom", "profile3@test.com");
        HttpEntity<UpdateProfileRequest> updateEntity = new HttpEntity<>(update, authHeaders(token));
        ResponseEntity<String> response =
                restTemplate.exchange("/api/auth/me", HttpMethod.PUT, updateEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateProfile_normalizesEmailToLowercase() {
        String token = registerAndGetToken("profile5@test.com");

        UpdateProfileRequest update = new UpdateProfileRequest("Nom", "Prenom", "Profile5-New@Test.COM");
        HttpEntity<UpdateProfileRequest> updateEntity = new HttpEntity<>(update, authHeaders(token));
        ResponseEntity<AuthResponse> updateResponse =
                restTemplate.exchange("/api/auth/me", HttpMethod.PUT, updateEntity, AuthResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpEntity<Void> meEntity = new HttpEntity<>(authHeaders(updateResponse.getBody().token()));
        ResponseEntity<User> meResponse = restTemplate.exchange("/api/auth/me", HttpMethod.GET, meEntity, User.class);

        assertThat(meResponse.getBody().getEmail()).isEqualTo("profile5-new@test.com");
    }

    @Test
    void updateProfile_keepingSameEmail_succeeds() {
        String token = registerAndGetToken("profile4@test.com");

        UpdateProfileRequest update = new UpdateProfileRequest("Nouveau", "Nom", "profile4@test.com");
        HttpEntity<UpdateProfileRequest> updateEntity = new HttpEntity<>(update, authHeaders(token));
        ResponseEntity<AuthResponse> response =
                restTemplate.exchange("/api/auth/me", HttpMethod.PUT, updateEntity, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
