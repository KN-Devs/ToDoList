package com.todolist.portfolio.controller;

import com.todolist.portfolio.dto.AdminResetPasswordRequest;
import com.todolist.portfolio.dto.AuthResponse;
import com.todolist.portfolio.dto.LoginRequest;
import com.todolist.portfolio.dto.RegisterRequest;
import com.todolist.portfolio.dto.UpdateProfileRequest;
import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.repository.UserRepository;
import com.todolist.portfolio.service.JwtService;
import com.todolist.portfolio.service.LoginAttemptService;
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

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, AuthenticationManager authenticationManager,
                           LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
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

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return new AuthResponse(token);
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
            loginAttemptService.onSuccessfulLogin(authenticatedUser);

            String token = jwtService.generateToken(authenticatedUser);
            return new AuthResponse(token);
        } catch (BadCredentialsException ex) {
            if (user != null) {
                loginAttemptService.onFailedLogin(user);
            }
            throw ex;
        }
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
        return new AuthResponse(token);
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
