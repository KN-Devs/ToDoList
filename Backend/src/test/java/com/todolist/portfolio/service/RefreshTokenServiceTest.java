package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.RefreshToken;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User bob;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
    }

    @Test
    void issue_savesAHashedTokenValidFor30Days() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.issue(bob);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getUser()).isEqualTo(bob);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(29L * 24 * 3600));
        assertThat(saved.getExpiresAt()).isBefore(Instant.now().plusSeconds(31L * 24 * 3600));
    }

    @Test
    void validate_withValidToken_returnsUser() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String rawToken = refreshTokenService.issue(bob);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        when(refreshTokenRepository.findByTokenHash(captor.getValue().getTokenHash()))
                .thenReturn(Optional.of(captor.getValue()));

        assertThat(refreshTokenService.validate(rawToken)).isEqualTo(bob);
    }

    @Test
    void validate_withUnknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> refreshTokenService.validate("unknown"));
    }

    @Test
    void validate_withRevokedToken_throwsUnauthorized() {
        RefreshToken revoked = new RefreshToken("hash", bob, Instant.now().plusSeconds(3600), Instant.now());
        revoked.setRevokedAt(Instant.now());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThrows(ResponseStatusException.class, () -> refreshTokenService.validate("some-token"));
    }

    @Test
    void validate_withExpiredToken_throwsUnauthorized() {
        RefreshToken expired = new RefreshToken("hash", bob, Instant.now().minusSeconds(60), Instant.now().minusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThrows(ResponseStatusException.class, () -> refreshTokenService.validate("some-token"));
    }

    @Test
    void revoke_marksTheMatchingTokenAsRevoked() {
        RefreshToken token = new RefreshToken("hash", bob, Instant.now().plusSeconds(3600), Instant.now());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        refreshTokenService.revoke("some-token");

        assertThat(token.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revoke_withUnknownToken_doesNothing() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown");

        verify(refreshTokenRepository, times(0)).save(any());
    }

    @Test
    void revokeAllForUser_revokesEveryActiveToken() {
        RefreshToken tokenA = new RefreshToken("hash-a", bob, Instant.now().plusSeconds(3600), Instant.now());
        RefreshToken tokenB = new RefreshToken("hash-b", bob, Instant.now().plusSeconds(3600), Instant.now());
        when(refreshTokenRepository.findByUserAndRevokedAtIsNull(bob)).thenReturn(List.of(tokenA, tokenB));

        refreshTokenService.revokeAllForUser(bob);

        assertThat(tokenA.getRevokedAt()).isNotNull();
        assertThat(tokenB.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).saveAll(List.of(tokenA, tokenB));
    }
}
