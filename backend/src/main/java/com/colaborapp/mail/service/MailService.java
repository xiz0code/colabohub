package com.colaborapp.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean mailEnabled;
    private final String mailFrom;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.enabled:true}") boolean mailEnabled,
            @Value("${app.mail.from:}") String mailFrom) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom == null ? "" : mailFrom.trim();
    }

    public void send(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("Mail delivery disabled by configuration. To={}, Subject={}", to, subject);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info(
                    "Mail infrastructure is not configured. Email delivery skipped. To={}, Subject={}, BodyLength={}",
                    to,
                    subject,
                    body != null ? body.length() : 0);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("Failed to send email. To={}, Subject={}, BodyLength={}",
                    to,
                    subject,
                    body != null ? body.length() : 0,
                    exception);
        }
    }

    public void sendHtml(String to, String subject, String htmlBody, String plainTextBody) {
        if (!mailEnabled) {
            log.info("HTML mail delivery disabled by configuration. To={}, Subject={}", to, subject);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info(
                    "Mail infrastructure is not configured. HTML email delivery skipped. To={}, Subject={}, HtmlBodyLength={}, TextBodyLength={}",
                    to,
                    subject,
                    htmlBody != null ? htmlBody.length() : 0,
                    plainTextBody != null ? plainTextBody.length() : 0);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (!mailFrom.isBlank()) {
                helper.setFrom(mailFrom);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainTextBody, htmlBody);
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("Failed to send HTML email. To={}, Subject={}, HtmlBodyLength={}, TextBodyLength={}",
                    to,
                    subject,
                    htmlBody != null ? htmlBody.length() : 0,
                    plainTextBody != null ? plainTextBody.length() : 0,
                    exception);
        }
    }
}
