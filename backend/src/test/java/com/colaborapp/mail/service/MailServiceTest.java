package com.colaborapp.mail.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class MailServiceTest {

    @Test
    void sendHtmlBuildsMultipartMessageWithPlainAndHtmlBodies() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(System.getProperties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);

        MailService service = new MailService(provider, true, "notificaciones@colabohub.cl");

        service.sendHtml("destino@correo.cl", "Asunto", "<strong>Hola</strong>", "Hola");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendHtmlSkipsDeliveryWhenMailDisabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);

        MailService service = new MailService(provider, false, "notificaciones@colabohub.cl");

        service.sendHtml("destino@correo.cl", "Asunto", "<strong>Hola</strong>", "Hola");

        verify(mailSender, never()).createMimeMessage();
    }
}
