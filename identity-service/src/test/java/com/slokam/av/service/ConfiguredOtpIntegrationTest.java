package com.slokam.av.service;

import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.entity.Role;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import com.slokam.av.security.JwtService;
import com.slokam.av.util.MobileNormalizer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=ConfiguredOtpIntegrationTest.Config.class,properties={
    "spring.datasource.url=jdbc:h2:mem:otp;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "auth.otp.channel=WHATSAPP",
    "jwt.secret=otp-test-secret-at-least-thirty-two-bytes", "app.admin.mobile=+918881178888"})
@ActiveProfiles("otp-test")
class ConfiguredOtpIntegrationTest {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @EntityScan("com.slokam.av.entity") @EnableJpaRepositories("com.slokam.av.repository")
    @Import({ConfiguredOtpService.class,OtpService.class,OtpChannelConfig.class,MobileNormalizer.class,JwtService.class})
    static class Config {}
    @MockitoBean MetaWhatsAppProvider whatsapp;
    @MockitoBean EmailDeliveryService email;
    @Autowired ConfiguredOtpService auth;
    @Autowired OtpVerificationRepository records;
    @Autowired UserRepository users;
    @Autowired AuthLockRepository locks;
    @Autowired JwtService jwt;

    @BeforeEach void resetData() {
        records.deleteAll(); users.deleteAll(); reset(whatsapp,email);
        for(int i=0;i<64;i++) { AuthLock lock=new AuthLock(); lock.id=i; locks.save(lock); }
    }
    private String request(String number) {
        auth.requestRegistration(number);
        var captor=ArgumentCaptor.forClass(String.class);
        verify(whatsapp,atLeastOnce()).sendOtp(eq("+91"+number),captor.capture());
        return captor.getValue();
    }
    private String mobileProof(String number) {
        return auth.verifyRegistration(new OtpVerifyRequest(number,request(number))).registrationToken();
    }
    private String emailCode(String address) {
        var body=ArgumentCaptor.forClass(String.class);
        verify(email,atLeastOnce()).sendHtml(eq(address),anyString(),body.capture(),anyString(),isNull());
        var match=java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(body.getValue());
        assertTrue(match.find()); return match.group();
    }
    private String emailProof(String proof, String address) {
        auth.requestRegistrationEmail(new RegistrationEmailRequest(proof,address));
        return auth.verifyRegistrationEmail(new RegistrationEmailVerifyRequest(proof,address,emailCode(address))).registrationToken();
    }
    private User existing(String address) {
        User user=new User(); user.name="Viewer"; user.mobile="+919000000099"; user.email=address;
        return users.saveAndFlush(user);
    }
    @Test void registrationVerifiesBothContactsAndLoginNeverSendsWhatsApp() {
        String number="9000000001", address="viewer@example.test", proof=mobileProof(number);
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,()->jwt.validate(proof));
        assertEquals(0,users.count());
        assertThrows(ApiException.class,()->auth.register(new OtpRegistrationRequest(proof,"Viewer",address,number)));
        String emailProof=emailProof(proof,address);
        var request=new OtpRegistrationRequest(proof,"Viewer",address,number,emailProof);
        var result=auth.register(request);
        assertNotNull(result.accessToken());
        User user=users.findById(result.user().id()).orElseThrow();
        assertNotNull(user.emailVerifiedAt); assertNotNull(user.mobileVerifiedAt);
        assertThrows(ApiException.class,()->auth.register(request));
        reset(whatsapp,email);
        auth.request(" Viewer@Example.test ");
        var login=auth.verify(new OtpVerifyRequest(address,emailCode(address)));
        assertEquals(user.id,login.user().id()); assertFalse(login.registrationRequired());
        verifyNoInteractions(whatsapp);
        assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(address,emailCode(address))));
    }
    @Test void wrongAttemptsPersistAndCooldownIsEnforced() {
        String number="9000000002", code=request(number);
        assertThrows(ApiException.class,()->auth.verifyRegistration(new OtpVerifyRequest(number,code.equals("000000")?"000001":"000000")));
        assertEquals(1,records.findFirstByMobileAndChannelOrderByIdDesc("+919000000002","WHATSAPP").orElseThrow().attemptCount);
        assertEquals(429,assertThrows(ApiException.class,()->auth.requestRegistration(number)).status);
    }
    @Test void deliveryFailureRollsBackChallenge() {
        doThrow(new ApiException(503,"OTP_DELIVERY_UNAVAILABLE","Unavailable")).when(whatsapp).sendOtp(anyString(),anyString());
        assertThrows(ApiException.class,()->auth.requestRegistration("9000000003"));
        assertEquals(0,records.count()); verifyNoInteractions(email);
    }
    @Test void registrationProofsCannotBeMixedBetweenMobiles() {
        String first=mobileProof("9000000004"), second=mobileProof("9000000005"), address="a@example.test";
        String emailProof=emailProof(first,address);
        assertThrows(ApiException.class,()->auth.register(new OtpRegistrationRequest(second,"Viewer",address,"9000000005",emailProof)));
        assertNotNull(auth.register(new OtpRegistrationRequest(first,"Viewer",address,"9000000004",emailProof)).accessToken());
    }
    @Test void existingAndReservedMobilesCannotReceiveWhatsApp() {
        existing("existing@example.test");
        assertThrows(ApiException.class,()->auth.requestRegistration("9000000099"));
        assertThrows(ApiException.class,()->auth.requestRegistration("8881178888"));
        verifyNoInteractions(whatsapp,email);
    }
    @Test void unknownAndDisabledAccountsCannotLogin() {
        assertThrows(ApiException.class,()->auth.request("unknown@example.test"));
        User user=existing("disabled@example.test"); user.enabled=false; users.saveAndFlush(user);
        assertThrows(ApiException.class,()->auth.request(user.email));
        assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(user.email,"123456")));
        verifyNoInteractions(whatsapp,email);
    }
    @Test void existingUnverifiedEmailIsVerifiedByLoginWithoutChangingAccountId() {
        User user=existing("existing@example.test");
        auth.request(user.email);
        assertEquals(user.id,auth.verify(new OtpVerifyRequest(user.email,emailCode(user.email))).user().id());
        assertNotNull(users.findById(user.id).orElseThrow().emailVerifiedAt);
        verifyNoInteractions(whatsapp);
    }
    @Test void expiredMobileProofCannotRequestRegistrationEmail() {
        String proof=mobileProof("9000000007");
        var row=records.findFirstByMobileAndChannelOrderByIdDesc("+919000000007","WHATSAPP").orElseThrow();
        row.expiresAt=java.time.Instant.EPOCH; records.saveAndFlush(row);
        assertThrows(ApiException.class,()->auth.requestRegistrationEmail(new RegistrationEmailRequest(proof,"a@example.test")));
        verifyNoInteractions(email);
    }
    @Test void registrationEmailAttemptsPersistAndLoginCannotUseRegistrationCode() {
        String address="a@example.test", proof=mobileProof("9000000008");
        auth.requestRegistrationEmail(new RegistrationEmailRequest(proof,address));
        String code=emailCode(address), wrong=code.equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++) assertThrows(ApiException.class,()->auth.verifyRegistrationEmail(new RegistrationEmailVerifyRequest(proof,address,wrong)));
        assertEquals(429,assertThrows(ApiException.class,()->auth.verifyRegistrationEmail(new RegistrationEmailVerifyRequest(proof,address,code))).status);
        assertEquals(5,records.findFirstByMobileAndChannelOrderByIdDesc(address,"REGISTER_EMAIL").orElseThrow().attemptCount);
        assertEquals(429,assertThrows(ApiException.class,()->auth.requestRegistrationEmail(new RegistrationEmailRequest(proof,address))).status);
        // Even an account created separately cannot authenticate using a registration email challenge.
        User user=existing(address);
        assertThrows(ApiException.class,()->auth.verify(new OtpVerifyRequest(user.email,code)));
    }
    @Test void administratorLoginUsesEmailOnly() {
        User user=existing("admin@example.test"); user.role=Role.ADMIN; users.saveAndFlush(user);
        // This test context intentionally has no configured admin email, so unauthorized admin identities are rejected.
        assertThrows(ApiException.class,()->auth.request(user.email));
        verifyNoInteractions(whatsapp,email);
    }
}
