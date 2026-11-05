package com.slokam.av.service;

import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.entity.Role;
import com.slokam.av.entity.User;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.util.MobileNormalizer;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionAdminProvisionerTest {
    @Test void linksConfiguredEmailToExistingMobileAdminWithoutCreatingAnotherAccount() {
        var users=mock(UserRepository.class);
        User admin=new User(); admin.role=Role.ADMIN; admin.mobile="+919000000001";
        when(users.findByMobile(admin.mobile)).thenReturn(Optional.of(admin));
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.empty());
        var provisioner=new ProductionAdminProvisioner(users,new OtpChannelConfig("WHATSAPP"),
            "admin@example.test",admin.mobile,new MobileNormalizer("91",10));
        provisioner.run(null);
        assertEquals("admin@example.test",admin.email);
        verify(users).saveAndFlush(same(admin));
        verify(users,never()).save(any());
    }
    @Test void conflictingConfiguredContactsFailWithoutChangingAccounts() {
        var users=mock(UserRepository.class);
        User mobileAdmin=new User(); mobileAdmin.role=Role.ADMIN; mobileAdmin.mobile="+919000000001";
        User emailAdmin=new User(); emailAdmin.role=Role.ADMIN; emailAdmin.mobile="+919000000002";
        when(users.findByMobile(mobileAdmin.mobile)).thenReturn(Optional.of(mobileAdmin));
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(emailAdmin));
        var provisioner=new ProductionAdminProvisioner(users,new OtpChannelConfig("WHATSAPP"),
            "admin@example.test",mobileAdmin.mobile,new MobileNormalizer("91",10));
        assertThrows(IllegalStateException.class,()->provisioner.run(null));
        verify(users,never()).saveAndFlush(any()); verify(users,never()).save(any());
    }
}
