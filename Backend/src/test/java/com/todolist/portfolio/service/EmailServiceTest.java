package com.todolist.portfolio.service;

import com.todolist.portfolio.entity.Role;
import com.todolist.portfolio.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private User bob;

    @BeforeEach
    void setUp() {
        bob = new User(1, "Dupont", "Bob", "bob@test.com", "hash", Role.USER);
    }

    @Test
    void sendEmailConfirmation_whenDisabled_doesNotSend() {
        EmailService emailService = new EmailService(mailSender, false, "noreply@todolist.app", "http://localhost:4200");

        emailService.sendEmailConfirmation(bob, "some-token");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void sendEmailConfirmation_whenFromAddressIsBlank_fallsBackToDefaultSender() {
        // Reproduit l'incident de production où mail.from résolvait à une
        // chaîne vide (MAIL_FROM et MAIL_USERNAME tous deux absents) : sans ce
        // filet de sécurité, MimeMessageHelper.setFrom("") fait échouer
        // silencieusement tout envoi (MailParseException).
        EmailService emailService = new EmailService(mailSender, true, "", "http://localhost:4200");

        emailService.sendEmailConfirmation(bob, "some-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@todolist.app");
    }

    @Test
    void sendEmailConfirmation_whenEnabled_sendsMessageWithLink() {
        EmailService emailService = new EmailService(mailSender, true, "noreply@todolist.app", "http://localhost:4200");

        emailService.sendEmailConfirmation(bob, "some-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("bob@test.com");
        assertThat(message.getText()).contains("http://localhost:4200/confirm-email?token=some-token");
    }

    @Test
    void sendPasswordReset_whenEnabled_sendsMessageWithLink() {
        EmailService emailService = new EmailService(mailSender, true, "noreply@todolist.app", "http://localhost:4200");

        emailService.sendPasswordReset(bob, "reset-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("http://localhost:4200/reset-password?token=reset-token");
    }

    @Test
    void sendProjectInvitation_whenEnabled_sendsMessageWithLinkAndInviterName() {
        EmailService emailService = new EmailService(mailSender, true, "noreply@todolist.app", "http://localhost:4200");
        User alice = new User(2, "Martin", "Alice", "alice@test.com", "hash", Role.USER);
        com.todolist.portfolio.entity.Project project = new com.todolist.portfolio.entity.Project(
                1, "Refonte du site", "desc", null, null, alice);

        emailService.sendProjectInvitation(bob, alice, project, "invite-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("bob@test.com");
        assertThat(message.getText()).contains("http://localhost:4200/invitations/accept?token=invite-token");
        assertThat(message.getText()).contains("Alice");
        assertThat(message.getSubject()).contains("Refonte du site");
    }
}
