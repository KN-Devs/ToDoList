package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.VerificationTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class VerificationTokenService {

    private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

    private final VerificationTokenRepository tokenRepository;

    public VerificationTokenService(VerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public VerificationToken createToken(User user, TokenType type) {
        return createToken(user, type, null);
    }

    public VerificationToken createToken(User user, TokenType type, Project project) {
        invalidateExisting(user, type, project);

        Instant now = Instant.now();
        VerificationToken token = new VerificationToken(
                UUID.randomUUID().toString(), type, user, project, now.plus(TOKEN_VALIDITY), now);
        return tokenRepository.save(token);
    }

    public VerificationToken consumeToken(String value, TokenType expectedType) {
        VerificationToken token = tokenRepository.findByToken(value)
                .filter(t -> t.getType() == expectedType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien invalide ou déjà utilisé"));

        if (token.isConsumed()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien a déjà été utilisé");
        }
        if (token.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien a expiré, merci d'en redemander un nouveau");
        }

        token.setConsumedAt(Instant.now());
        return tokenRepository.save(token);
    }

    public void invalidateExisting(User user, TokenType type, Project project) {
        tokenRepository.findByUserAndTypeAndConsumedAtIsNull(user, type).stream()
                .filter(t -> java.util.Objects.equals(t.getProject(), project))
                .forEach(tokenRepository::delete);
    }
}
