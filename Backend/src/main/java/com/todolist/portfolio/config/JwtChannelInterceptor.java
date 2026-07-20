package com.todolist.portfolio.config;

import com.todolist.portfolio.entity.User;
import com.todolist.portfolio.service.CustomUserDetailsService;
import com.todolist.portfolio.service.JwtService;
import com.todolist.portfolio.service.ProjectService;
import io.jsonwebtoken.JwtException;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authentifie la connexion STOMP à partir du JWT porté par l'en-tête
 * Authorization de la frame CONNECT (la poignée de main WebSocket elle-même
 * est en accès libre côté Spring Security, voir SecurityConfig : un WebSocket
 * natif ne permet pas d'y joindre un en-tête personnalisé). Vérifie en plus,
 * à chaque frame SUBSCRIBE sur un topic de projet, que l'utilisateur a bien
 * accès à ce projet — sans ce contrôle, n'importe quel utilisateur connecté
 * pourrait s'abonner aux tâches et commentaires d'un projet auquel il n'a pas
 * accès en changeant simplement l'identifiant dans la destination.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Pattern PROJECT_DESTINATION = Pattern.compile("^/topic/projects/(\\d+)(/.*)?$");

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ProjectService projectService;

    public JwtChannelInterceptor(JwtService jwtService, CustomUserDetailsService userDetailsService,
                                  @Lazy ProjectService projectService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.projectService = projectService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Authentification requise");
        }

        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(token, userDetails)) {
                throw new AccessDeniedException("Token invalide ou expiré");
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            accessor.setUser(authToken);
        } catch (JwtException | UsernameNotFoundException e) {
            throw new AccessDeniedException("Token invalide ou expiré");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = PROJECT_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        User currentUser = extractUser(accessor);
        Integer projectId = Integer.valueOf(matcher.group(1));
        projectService.checkCanView(projectId, currentUser);
    }

    private User extractUser(StompHeaderAccessor accessor) {
        Object principal = accessor.getUser();
        if (principal instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getPrincipal() instanceof User user) {
            return user;
        }
        throw new AccessDeniedException("Authentification requise");
    }
}
