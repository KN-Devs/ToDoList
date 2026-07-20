package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.AdminResetPasswordRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.ConfirmEmailRequest;
import com.todolist.portfolio.dto.EmailOnlyRequest;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RefreshTokenRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.ResetPasswordRequest;
import com.todolist.portfolio.dto.UpdateProfileRequest;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.TokenType;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.entity.VerificationToken;
import com.todolist.portfolio.repository.UserRepository;
import com.todolist.portfolio.service.EmailService;
import com.todolist.portfolio.service.JwtService;
import com.todolist.portfolio.service.LoginAttemptService;
import com.todolist.portfolio.service.RefreshTokenService;
import com.todolist.portfolio.service.VerificationTokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Inscription, connexion et gestion du profil")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, AuthenticationManager authenticationManager,
                           LoginAttemptService loginAttemptService,
                           VerificationTokenService verificationTokenService, EmailService emailService,
                           RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
        this.verificationTokenService = verificationTokenService;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
    }

    @SecurityRequirements
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.getEmail().toLowerCase();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
        }

        User user = new User(
                null,
                request.getNom(),
                request.getPrenom(),
                email,
                passwordEncoder.encode(request.getPassword()),
                Role.USER
        );
        user.setEmailVerified(false);

        userRepository.save(user);

        VerificationToken confirmationToken = verificationTokenService.createToken(user, TokenType.EMAIL_VERIFICATION);
        emailService.sendEmailConfirmation(user, confirmationToken.getToken());

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user);

        return new AuthResponse(token, refreshToken);
    }

    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        String email = request.getEmail().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            loginAttemptService.checkNotLocked(user);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );

            User authenticatedUser = (User) authentication.getPrincipal();

            if (!authenticatedUser.isEmailVerified()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Veuillez confirmer votre adresse email avant de vous connecter. Vérifiez votre boîte de réception.");
            }

            loginAttemptService.onSuccessfulLogin(authenticatedUser);

            String token = jwtService.generateToken(authenticatedUser);
            String refreshToken = refreshTokenService.issue(authenticatedUser);
            return new AuthResponse(token, refreshToken);
        } catch (BadCredentialsException ex) {
            if (user != null) {
                loginAttemptService.onFailedLogin(user);
            }
            throw ex;
        }
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        User user = refreshTokenService.validate(request.refreshToken());
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, request.refreshToken());
    }

    @SecurityRequirements
    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @SecurityRequirements
    @PostMapping("/confirm-email")
    public void confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        VerificationToken token = verificationTokenService.consumeToken(request.token(), TokenType.EMAIL_VERIFICATION);

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @SecurityRequirements
    @PostMapping("/resend-confirmation")
    public void resendConfirmation(@Valid @RequestBody EmailOnlyRequest request) {
        userRepository.findByEmail(request.email().toLowerCase())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    VerificationToken token = verificationTokenService.createToken(user, TokenType.EMAIL_VERIFICATION);
                    emailService.sendEmailConfirmation(user, token.getToken());
                });
    }

    @SecurityRequirements
    @PostMapping("/forgot-password")
    public void forgotPassword(@Valid @RequestBody EmailOnlyRequest request) {
        userRepository.findByEmail(request.email().toLowerCase())
                .ifPresent(user -> {
                    VerificationToken token = verificationTokenService.createToken(user, TokenType.PASSWORD_RESET);
                    emailService.sendPasswordReset(user, token.getToken());
                });
    }

    @SecurityRequirements
    @PostMapping("/reset-password")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        VerificationToken token = verificationTokenService.consumeToken(request.getToken(), TokenType.PASSWORD_RESET);

        loginAttemptService.adminResetPassword(token.getUser(), passwordEncoder.encode(request.getNewPassword()));
    }

    @GetMapping("/me")
    public User me(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    @PutMapping("/me")
    public AuthResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        String email = request.getEmail().toLowerCase();

        boolean emailChanged = !email.equals(currentUser.getEmail());
        if (emailChanged && userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
        }

        currentUser.setNom(request.getNom());
        currentUser.setPrenom(request.getPrenom());
        currentUser.setEmail(email);
        userRepository.save(currentUser);

        String token = jwtService.generateToken(currentUser);
        String refreshToken = refreshTokenService.issue(currentUser);
        return new AuthResponse(token, refreshToken);
    }

    @PutMapping("/users/{id}/reset-password")
    public void resetPassword(@PathVariable Integer id, @Valid @RequestBody AdminResetPasswordRequest request,
                               Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Seul un administrateur peut réinitialiser le mot de passe d'un compte");
        }

        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        loginAttemptService.adminResetPassword(targetUser, passwordEncoder.encode(request.getNewPassword()));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<String> handleLockedException(LockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email ou mot de passe incorrect");
    }
}
