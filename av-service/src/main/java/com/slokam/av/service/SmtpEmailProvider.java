package com.slokam.av.service;

import com.slokam.av.exception.custom.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SmtpEmailProvider implements EmailProvider {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);
    private final JavaMailSender mail;
    private final String from, host;
    private final boolean development;

    public SmtpEmailProvider(
            JavaMailSender mail,
            Environment environment,
            @Value("${notifications.email-from:}") String from,
            @Value("${spring.mail.host:}") String host) {
        this.mail = mail;
        this.from = from;
        this.host = host;
        this.development = environment.acceptsProfiles(Profiles.of("dev"));
        if (!development && (from.isBlank() || host.isBlank()))
            throw new IllegalStateException(
                    "Production requires SMTP_HOST and NOTIFICATION_EMAIL_FROM");
    }

    @Override
    public void send(String to, String subject, String body) {
        if (host.isBlank() && development) {
            log.debug("SMTP delivery skipped: development mode without SMTP host");
            return;
        }
        try {
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mail.send(message);
            log.debug("SMTP delivery accepted by configured mail server");
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            log.warn(
                    "SMTP delivery failed for host {} (cause type: {}, detail: {})",
                    host,
                    cause.getClass().getSimpleName(),
                    safeDetail(cause));
            throw new ApiException(
                    503, "EMAIL_UNAVAILABLE", "Unable to send email. Please try again later.");
        }
    }

    @Override
    public void sendHtml(
            String to, String subject, String plainText, String html, String inlinePosterDataUri) {
        sendHtml(to, subject, plainText, html, inlinePosterDataUri, null);
    }

    @Override
    public void sendHtml(
            String to,
            String subject,
            String plainText,
            String html,
            String inlinePosterDataUri,
            String inlineQrDataUri) {
        if (host.isBlank() && development) {
            log.debug("SMTP delivery skipped: development mode without SMTP host");
            return;
        }
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            if (html.contains("cid:audience-verdict-logo")) {
                helper.addInline("audience-verdict-logo",
                        new ClassPathResource("logo/audience-verdict-logo.png"), "image/png");
            }
            if (inlinePosterDataUri != null) {
                addInlineImage(helper, "movie-poster", inlinePosterDataUri);
            }
            if (inlineQrDataUri != null) addInlineImage(helper, "ticket-qr", inlineQrDataUri);
            mail.send(message);
            log.debug("SMTP delivery accepted by configured mail server");
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            log.warn(
                    "SMTP delivery failed for host {} (cause type: {}, detail: {})",
                    host,
                    cause.getClass().getSimpleName(),
                    safeDetail(cause));
            throw new ApiException(
                    503, "EMAIL_UNAVAILABLE", "Unable to send email. Please try again later.");
        }
    }

    private static void addInlineImage(
            MimeMessageHelper helper, String contentId, String dataUri) throws Exception {
        Matcher match =
                Pattern.compile(
                                "(?is)^data:(image/(?:png|jpeg|webp));base64,([a-z0-9+/=\\r"
                                        + "\\n"
                                        + "]+)$")
                        .matcher(dataUri);
        if (match.matches())
            helper.addInline(
                    contentId,
                    new ByteArrayResource(Base64.getMimeDecoder().decode(match.group(2))),
                    match.group(1));
    }

    private static String safeDetail(Throwable cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "no provider detail";
        String safe =
                message.replaceAll(
                                "(?i)(password|passcode|user|username|login|auth(?:entication)?|credential|token|secret)(\\s*[:=]\\s*)[^\\s,;]+",
                                "$1$2<redacted>")
                        .replaceAll("[\\r\\n\\t]", " ");
        return safe.substring(0, Math.min(240, safe.length()));
    }
}
