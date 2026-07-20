package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.VerificationTokenRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    @Mock
    private VerificationTokenRepository tokenRepository;

    @InjectMocks
    private VerificationTokenService tokenService;

    private User bob;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
    }

    @Test
    void createToken_generatesTokenValidFor24Hours() {
        when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.findByUserAndTypeAndConsumedAtIsNull(bob, TokenType.EMAIL_VERIFICATION))
                .thenReturn(List.of());

        VerificationToken token = tokenService.createToken(bob, TokenType.EMAIL_VERIFICATION);

        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUser()).isEqualTo(bob);
        assertThat(token.getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);
        assertThat(token.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
        assertThat(token.getExpiresAt()).isBefore(Instant.now().plusSeconds(25 * 3600));
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void createToken_deletesPreviousUnconsumedTokensOfSameType() {
        when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VerificationToken oldToken = new VerificationToken("old", TokenType.EMAIL_VERIFICATION, bob, null,
                Instant.now().plusSeconds(3600), Instant.now());
        when(tokenRepository.findByUserAndTypeAndConsumedAtIsNull(bob, TokenType.EMAIL_VERIFICATION))
                .thenReturn(List.of(oldToken));

        tokenService.createToken(bob, TokenType.EMAIL_VERIFICATION);

        verify(tokenRepository).delete(oldToken);
    }

    @Test
    void consumeToken_whenValid_marksConsumedAndReturnsToken() {
        VerificationToken token = new VerificationToken("abc", TokenType.EMAIL_VERIFICATION, bob, null,
                Instant.now().plusSeconds(3600), Instant.now());
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));
        when(tokenRepository.save(token)).thenReturn(token);

        VerificationToken result = tokenService.consumeToken("abc", TokenType.EMAIL_VERIFICATION);

        assertThat(result.isConsumed()).isTrue();
        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    void consumeToken_whenUnknown_throwsNotFound() {
        when(tokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> tokenService.consumeToken("missing", TokenType.EMAIL_VERIFICATION));
    }

    @Test
    void consumeToken_whenWrongType_throwsNotFound() {
        VerificationToken token = new VerificationToken("abc", TokenType.PASSWORD_RESET, bob, null,
                Instant.now().plusSeconds(3600), Instant.now());
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        assertThrows(ResponseStatusException.class,
                () -> tokenService.consumeToken("abc", TokenType.EMAIL_VERIFICATION));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void consumeToken_whenAlreadyConsumed_throwsGone() {
        VerificationToken token = new VerificationToken("abc", TokenType.EMAIL_VERIFICATION, bob, null,
                Instant.now().plusSeconds(3600), Instant.now());
        token.setConsumedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> tokenService.consumeToken("abc", TokenType.EMAIL_VERIFICATION));
        assertThat(ex.getStatusCode().value()).isEqualTo(410);
    }

    @Test
    void consumeToken_whenExpired_throwsGone() {
        VerificationToken token = new VerificationToken("abc", TokenType.EMAIL_VERIFICATION, bob, null,
                Instant.now().minusSeconds(60), Instant.now().minusSeconds(3600));
        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> tokenService.consumeToken("abc", TokenType.EMAIL_VERIFICATION));
        assertThat(ex.getStatusCode().value()).isEqualTo(410);
    }

    @Test
    void createProjectInvitationToken_doesNotInvalidateTokensForOtherProjects() {
        when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Project projectA = new Project(1, "A", "desc", null, null, bob);
        Project projectB = new Project(2, "B", "desc", null, null, bob);
        VerificationToken tokenForA = new VerificationToken("a", TokenType.PROJECT_INVITATION, bob, projectA,
                Instant.now().plusSeconds(3600), Instant.now());
        when(tokenRepository.findByUserAndTypeAndConsumedAtIsNull(bob, TokenType.PROJECT_INVITATION))
                .thenReturn(List.of(tokenForA));

        tokenService.createToken(bob, TokenType.PROJECT_INVITATION, projectB);

        verify(tokenRepository, never()).delete(tokenForA);
    }
}
