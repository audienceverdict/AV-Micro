package com.slokam.av.service;

public interface EmailProvider {
    void send(String to, String subject, String body);

    default void sendHtml(
            String to, String subject, String plainText, String html, String inlinePosterDataUri) {
        send(to, subject, plainText);
    }

    default void sendHtml(
            String to,
            String subject,
            String plainText,
            String html,
            String inlinePosterDataUri,
            String inlineQrDataUri) {
        sendHtml(to, subject, plainText, html, inlinePosterDataUri);
    }
}
