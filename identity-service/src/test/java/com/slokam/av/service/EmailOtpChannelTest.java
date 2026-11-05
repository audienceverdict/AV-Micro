package com.slokam.av.service;
import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.dto.*;
import com.slokam.av.entity.User;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.security.JwtService;
import com.slokam.av.util.MobileNormalizer;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EmailOtpChannelTest {
    private static JwtService testJwt() {
        try { return new JwtService(new org.springframework.core.io.ClassPathResource("test-private.pem"),new org.springframework.core.io.ClassPathResource("test-public.pem"),3600000,"test-key"); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private final UserRepository users=mock(UserRepository.class);
    private final OtpService otp=mock(OtpService.class);
    private final JwtService jwt=testJwt();
    private ConfiguredOtpService service(String channel) {
        return new ConfiguredOtpService(new OtpChannelConfig(channel),otp,users,new MobileNormalizer("91",10),jwt,"+918881178888","admin@example.test");
    }
    @Test void emailModeUsesEmailOnlyAndNormalizesIdentity() {
        User user=new User(); user.email="viewer@example.test";
        when(users.findByEmailIgnoreCase(user.email)).thenReturn(Optional.of(user));
        service("WHATSAPP").request(" Viewer@Example.test ");
        verify(otp).requestEmail("viewer@example.test");
        verify(otp,never()).requestWhatsApp(anyString());
        assertThrows(ApiException.class,()->service("EMAIL").request("9000000001"));
    }
    @Test void switchRejectsRegistrationProofFromOtherChannel() {
        String proof=jwt.registrationProof("+919000000001","WHATSAPP",1L);
        assertEquals("INVALID_REGISTRATION_TOKEN",assertThrows(ApiException.class,()->service("EMAIL").register(
                new OtpRegistrationRequest(proof,"Viewer","viewer@example.test","9000000001"))).code);
        verifyNoInteractions(otp);
    }
    @Test void sameAccountIdIsUsedWhenSigningInThroughEmail() {
        User user=new User(); user.name="Viewer"; user.email="viewer@example.test"; user.mobile="+919000000001";
        when(users.findByEmailIgnoreCase(user.email)).thenReturn(Optional.of(user));
        when(otp.verifyChannel(user.email,"123456","EMAIL")).thenReturn(1L);
        var result=service("EMAIL").verify(new OtpVerifyRequest(user.email,"123456"));
        assertEquals(user.id,result.user().id()); assertFalse(result.registrationRequired());
        verify(otp).consumeChallenge(user.email,"EMAIL",1L);
    }
}
