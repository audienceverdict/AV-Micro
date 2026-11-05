package com.slokam.av.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.exception.custom.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
public class MetaWhatsAppProvider {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetaWhatsAppProvider.class);
    private final RestClient client;
    private final String phoneId, token, template, language;

    public MetaWhatsAppProvider(OtpChannelConfig config,
            @Value("${whatsapp.phone-number-id:}") String phoneId,
            @Value("${whatsapp.access-token:}") String token,
            @Value("${whatsapp.template-name:otp}") String template,
            @Value("${whatsapp.template-language:}") String language,
            @Value("${whatsapp.api-version:v25.0}") String version) {
        this.phoneId = phoneId;
        this.token = token;
        this.template = template;
        this.language = language;
        if ((phoneId.isBlank() || token.isBlank() || template.isBlank() || language.isBlank()))
            throw new IllegalArgumentException("WhatsApp OTP requires phone-number-id, access-token, template-name and template-language");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.client = RestClient.builder().baseUrl("https://graph.facebook.com/" + version)
                .requestFactory(factory).build();
    }

    public void sendOtp(String mobile, String code) {
        var parameters = List.of(Map.of("type", "text", "text", code));
        var payload = Map.of("messaging_product", "whatsapp", "to", mobile.replace("+", ""),
                "type", "template", "template", Map.of("name", template,
                "language", Map.of("code", language), "components", List.of(
                Map.of("type", "body", "parameters", parameters),
                Map.of("type", "button", "sub_type", "url", "index", "0", "parameters", parameters))));
        try {
            JsonNode result = client.post().uri("/{phone}/messages", phoneId)
                    .headers(h -> h.setBearerAuth(token)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(JsonNode.class);
            if (result == null || result.path("messages").path(0).path("id").asText().isBlank())
                throw new IllegalStateException("Missing message id");
            JsonNode message = result.path("messages").path(0);
            log.info("WhatsApp OTP accepted by Meta messageId={} status={}; delivery is not confirmed",
                    message.path("id").asText(), message.path("message_status").asText("unspecified"));
        } catch (RuntimeException failure) {
            // Do not expose provider responses, tokens or OTPs through errors/logs.
            throw new ApiException(503, "OTP_DELIVERY_UNAVAILABLE", "Unable to send WhatsApp OTP. Please try again later.");
        }
    }
}
