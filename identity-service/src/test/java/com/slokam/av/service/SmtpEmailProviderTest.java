package com.slokam.av.service;

import com.slokam.av.util.BrandedEmailTemplate;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailProviderTest {
    @Test
    void embedsPackagedLogoAlongsidePosterAndQrWithoutChangingTextAlternative() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var provider = new SmtpEmailProvider(sender, new MockEnvironment(),
                "sender@example.com", "smtp.example.com");
        var content = BrandedEmailTemplate.otp("012345", 5);
        var image = "data:image/png;base64,iVBORw0KGgo=";

        provider.sendHtml("user@example.com", "Sign in", content.plainText(),
                content.html(), image, image);
        message.saveChanges();

        verify(sender).send(message);
        var logo = findPart(message, "<audience-verdict-logo>");
        assertNotNull(logo);
        assertTrue(logo.isMimeType("image/png"));
        assertEquals(Part.INLINE, logo.getDisposition());
        try (var expected = new ClassPathResource("logo/audience-verdict-logo.png").getInputStream();
                var actual = logo.getInputStream()) {
            assertArrayEquals(expected.readAllBytes(), actual.readAllBytes());
        }
        assertNotNull(findPart(message, "<movie-poster>"));
        assertNotNull(findPart(message, "<ticket-qr>"));
        assertTrue(containsText(message, "text/plain", content.plainText()));
        assertTrue(containsText(message, "text/html", "cid:audience-verdict-logo"));
    }

    private static Part findPart(Part part, String contentId) throws Exception {
        var ids = part.getHeader("Content-ID");
        if (ids != null && java.util.Arrays.asList(ids).contains(contentId)) return part;
        if (part.isMimeType("multipart/*")) {
            var multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                var found = findPart(multipart.getBodyPart(i), contentId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean containsText(Part part, String type, String text) throws Exception {
        if (part.isMimeType(type)) return part.getContent().toString().contains(text);
        if (part.isMimeType("multipart/*")) {
            var multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                if (containsText(multipart.getBodyPart(i), type, text)) return true;
            }
        }
        return false;
    }
}
