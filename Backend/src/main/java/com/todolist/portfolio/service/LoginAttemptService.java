package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.UserRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class LoginAttemptService {

    private static final int ATTEMPTS_BEFORE_LOCKOUT = 3;
    private static final Duration[] LOCKOUT_DURATIONS = {
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
    };
    private static final int PERMANENT_LOCKOUT_STAGE = LOCKOUT_DURATIONS.length + 1;

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public LoginAttemptService(UserRepository userRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    public void checkNotLocked(User user) {
        if (user.isAccountNonLocked()) {
            return;
        }
        throw new LockedException(lockMessage(user));
    }

    public void onSuccessfulLogin(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockoutStage(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    public void onFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;

        if (attempts < ATTEMPTS_BEFORE_LOCKOUT) {
            user.setFailedLoginAttempts(attempts);
            userRepository.save(user);
            return;
        }

        int nextStage = user.getLockoutStage() + 1;
        user.setFailedLoginAttempts(0);
        user.setLockoutStage(nextStage);
        user.setLockedUntil(
                nextStage <= LOCKOUT_DURATIONS.length
                        ? Instant.now().plus(LOCKOUT_DURATIONS[nextStage - 1])
                        : null
        );
        userRepository.save(user);
    }

    public void adminResetPassword(User user, String encodedPassword) {
        user.setPassword(encodedPassword);
        user.setFailedLoginAttempts(0);
        user.setLockoutStage(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Un mot de passe changé (par un admin ou via le lien "mot de passe oublié")
        // invalide toutes les sessions existantes : si le mot de passe a été
        // compromis, un éventuel attaquant avec un refresh token valide ne doit
        // pas pouvoir continuer à obtenir de nouveaux access tokens.
        refreshTokenService.revokeAllForUser(user);
    }

    private String lockMessage(User user) {
        if (user.getLockoutStage() >= PERMANENT_LOCKOUT_STAGE) {
            return "Compte verrouillé après plusieurs échecs de connexion. "
                    + "Contactez un administrateur pour réinitialiser votre mot de passe.";
        }

        long minutesLeft = Math.max(
                1,
                Duration.between(Instant.now(), user.getLockedUntil()).plusSeconds(59).toMinutes()
        );

        return "Compte temporairement verrouillé suite à plusieurs échecs de connexion. "
                + "Réessayez dans " + minutesLeft + " minute" + (minutesLeft > 1 ? "s" : "") + ".";
    }
}
