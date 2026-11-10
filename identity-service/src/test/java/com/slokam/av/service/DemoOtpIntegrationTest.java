package com.slokam.av.service;

import com.slokam.av.dto.OtpVerifyRequest;
import com.slokam.av.entity.AuthLock;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=ConfiguredOtpIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "auth.demo.enabled=true", "auth.demo.email=demo@example.test",
    "auth.demo.otp=123456"})
@ActiveProfiles("otp-test")
class DemoOtpIntegrationTest {
    @MockitoBean MetaWhatsAppProvider whatsapp;
    @MockitoBean EmailDeliveryService email;
    @Autowired ConfiguredOtpService auth;
    @Autowired OtpService otp;
    @Autowired OtpVerificationRepository records;
    @Autowired UserRepository users;
    @Autowired AuthLockRepository locks;

    @BeforeEach void resetData() {
        records.deleteAll(); reset(email, whatsapp);
        for (int i=0;i<64;i++) { AuthLock lock=new AuthLock(); lock.id=i; locks.save(lock); }
    }

    @Test void provisionedAccountCanLoginRepeatedlyAndChallengeCannotBeReused() {
        var user=users.findByEmailIgnoreCase("demo@example.test").orElseThrow();
        assertEquals(com.slokam.av.entity.Role.USER,user.role);
        for (int i=0;i<2;i++) {
            auth.request(" DEMO@EXAMPLE.TEST ");
            var row=records.findFirstByMobileAndChannelOrderByIdDesc(user.email,"EMAIL").orElseThrow();
            assertNotEquals("123456",row.otpHash);
            assertEquals(user.id,auth.verify(new OtpVerifyRequest(user.email,"123456")).user().id());
            assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(user.email,"123456")));
            records.deleteAll(); // Avoid waiting for the normal resend cooldown.
        }
        verifyNoInteractions(email,whatsapp);
    }

    @Test void wrongExpiredAndMissingChallengesFailAndCooldownRemains() {
        String address="demo@example.test";
        assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(address,"123456")));
        auth.request(address);
        assertEquals(429,assertThrows(ApiException.class,()->auth.request(address)).status);
        assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(address,"000000")));
        var row=records.findFirstByMobileAndChannelOrderByIdDesc(address,"EMAIL").orElseThrow();
        assertEquals(1,row.attemptCount);
        row.expiresAt=Instant.EPOCH; records.saveAndFlush(row);
        assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(address,"123456")));
    }

    @Test void deletionAndRegistrationStillDeliverCodes() {
        otp.requestDeletion("demo@example.test");
        otp.requestRegistrationEmail("demo@example.test","binding");
        otp.requestWhatsApp("+919000000001");
        verify(email,times(2)).sendHtml(eq("demo@example.test"),anyString(),anyString(),anyString(),isNull());
        verify(whatsapp).sendOtp(eq("+919000000001"),matches("[0-9]{6}"));
    }
}
