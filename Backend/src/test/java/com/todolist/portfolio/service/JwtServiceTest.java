package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User bob;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "c9LFPMTyngIGNVVOyCJsFDR9NBigIi672n5yVkrCJ5WSTUsASKUC3TgTfhornn4fMcMKDfv7wtfdZ1y5SKaHjw==");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);

        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
    }

    @Test
    void generateToken_thenExtractUsername_returnsSameEmail() {
        String token = jwtService.generateToken(bob);
        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("bob@test.com");
    }

    @Test
    void isTokenValid_forCorrectUser_returnsTrue() {
        String token = jwtService.generateToken(bob);

        assertThat(jwtService.isTokenValid(token, bob)).isTrue();
    }

    @Test
    void isTokenValid_forDifferentUser_returnsFalse() {
        String token = jwtService.generateToken(bob);
        User alice = new User(2, "Martin", "Alice", "alice@test.com", "hash", Role.ADMIN);

        assertThat(jwtService.isTokenValid(token, alice)).isFalse();
    }

    @Test
    void isTokenValid_forExpiredToken_returnsFalseWithoutThrowing() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String expiredToken = jwtService.generateToken(bob);

        assertThat(jwtService.isTokenValid(expiredToken, bob)).isFalse();
    }

    @Test
    void isTokenValid_forMalformedToken_returnsFalseWithoutThrowing() {
        assertThat(jwtService.isTokenValid("token.invalide.ici", bob)).isFalse();
    }
}
