package com.slokam.av.service;
import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.exception.custom.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class MetaWhatsAppProviderTest {
    @Test void sendsCodeInBodyAndCopyCodeButton() {
        var provider=new MetaWhatsAppProvider(new OtpChannelConfig("WHATSAPP"),"123","secret","otp","en_US","v25.0");
        var builder=RestClient.builder().baseUrl("https://graph.facebook.com/v25.0");
        var server=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(provider,"client",builder.build());
        server.expect(requestTo("https://graph.facebook.com/v25.0/123/messages"))
            .andExpect(header("Authorization","Bearer secret"))
            .andExpect(jsonPath("$.to").value("918881178888"))
            .andExpect(jsonPath("$.template.name").value("otp"))
            .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("012345"))
            .andExpect(jsonPath("$.template.components[1].sub_type").value("url"))
            .andExpect(jsonPath("$.template.components[1].parameters[0].text").value("012345"))
            .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.test\"}]}",MediaType.APPLICATION_JSON));
        provider.sendOtp("+918881178888","012345"); server.verify();
    }
    @Test void requiresWhatsAppSettingsForRegistrationInEveryMode() {
        assertThrows(IllegalArgumentException.class,()->new MetaWhatsAppProvider(new OtpChannelConfig("WHATSAPP"),"","","otp","","v25.0"));
        assertThrows(IllegalArgumentException.class,()->new MetaWhatsAppProvider(new OtpChannelConfig("EMAIL"),"","","otp","","v25.0"));
        assertThrows(IllegalArgumentException.class,()->new OtpChannelConfig("SMS"));
        assertEquals(409,assertThrows(ApiException.class,()->new OtpChannelConfig("WHATSAPP").requireEmail()).status);
    }
    @Test void providerFailureDoesNotExposeResponse() {
        var provider=new MetaWhatsAppProvider(new OtpChannelConfig("WHATSAPP"),"123","secret","otp","en","v25.0");
        var builder=RestClient.builder().baseUrl("https://graph.facebook.com/v25.0");
        var server=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(provider,"client",builder.build());
        server.expect(requestTo("https://graph.facebook.com/v25.0/123/messages"))
            .andRespond(withBadRequest().body("sensitive-provider-response"));
        var error=assertThrows(ApiException.class,()->provider.sendOtp("+918881178888","123456"));
        assertEquals(503,error.status); assertFalse(error.getMessage().contains("sensitive")); server.verify();
    }
}
