package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.Project;
import com.todolist.portfolio.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender,
                         @Value("${mail.enabled}") boolean enabled,
                         @Value("${mail.from}") String fromAddress,
                         @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    public void sendEmailConfirmation(User user, String token) {
        String link = frontendUrl + "/confirm-email?token=" + token;
        send(user.getEmail(), "Confirmez votre adresse email",
                "Bonjour " + user.getPrenom() + ",\n\n"
                        + "Merci de vous être inscrit sur ToDoList. Confirmez votre adresse email "
                        + "en cliquant sur le lien suivant (valable 24 heures) :\n" + link + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette inscription, ignorez cet email.");
    }

    public void sendPasswordReset(User user, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(user.getEmail(), "Réinitialisation de votre mot de passe",
                "Bonjour " + user.getPrenom() + ",\n\n"
                        + "Vous avez demandé la réinitialisation de votre mot de passe. Cliquez sur le lien "
                        + "suivant pour en choisir un nouveau (valable 24 heures) :\n" + link + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet email : "
                        + "votre mot de passe actuel reste inchangé.");
    }

    public void sendProjectInvitation(User invitee, User inviter, Project project, String token) {
        String link = frontendUrl + "/invitations/accept?token=" + token;
        send(invitee.getEmail(), "Invitation au projet \"" + project.getNom() + "\"",
                "Bonjour " + invitee.getPrenom() + ",\n\n"
                        + inviter.getPrenom() + " " + inviter.getNom() + " (" + inviter.getEmail() + ") "
                        + "vous invite à rejoindre le projet \"" + project.getNom() + "\" sur ToDoList.\n\n"
                        + "Acceptez l'invitation en cliquant sur le lien suivant (valable 24 heures) :\n" + link + "\n\n"
                        + "Si vous ne connaissez pas cette personne, ignorez cet email.");
    }

    private void send(String to, String subject, String text) {
        if (!enabled) {
            log.info("Envoi d'email désactivé (mail.enabled=false) : \"{}\" à {} ignoré", subject, to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Échec de l'envoi de l'email \"{}\" à {}", subject, to, ex);
        }
    }
}
