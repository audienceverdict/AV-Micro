package com.slokam.av.service;

import com.slokam.av.entity.User;
import com.slokam.av.entity.Role;
import com.slokam.av.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoAccountTest {
    private final UserRepository users=mock(UserRepository.class);
    private MockEnvironment environment(String... profiles) {
        MockEnvironment env=new MockEnvironment(); env.setActiveProfiles(profiles); return env;
    }
    @Test void disabledModeNeverProvisionsOrOverridesCodes() {
        var demo=new DemoAccount(users,environment("prod"),false,"demo@example.test","123456");
        demo.run(null);
        assertFalse(demo.matches("demo@example.test","EMAIL")); verifyNoInteractions(users);
    }
    @Test void productionAndUnknownProfilesAndInvalidSettingsFailClosed() {
        for (String[] profiles:new String[][]{{"prod"},{"dev","production"},{"staging"},{}})
            assertThrows(IllegalArgumentException.class,()->new DemoAccount(users,environment(profiles),true,"demo@example.test","123456"));
        assertThrows(IllegalArgumentException.class,()->new DemoAccount(users,environment("test"),true,"invalid","123456"));
        assertThrows(IllegalArgumentException.class,()->new DemoAccount(users,environment("test"),true,"demo@example.test","1234"));
    }
    @Test void existingOrdinaryAccountIsPreservedAndPrivilegedAccountRejected() {
        User user=new User(); user.email="demo@example.test";
        when(users.findByEmailIgnoreCase(user.email)).thenReturn(Optional.of(user));
        var demo=new DemoAccount(users,environment("test"),true," Demo@Example.Test ","123456");
        demo.run(null); verify(users,never()).saveAndFlush(any());
        assertTrue(demo.matches(user.email,"EMAIL"));
        assertFalse(demo.matches("other@example.test","EMAIL"));
        assertFalse(demo.matches(user.email,"DELETE_ACCOUNT"));
        user.role=Role.ADMIN;
        assertThrows(IllegalStateException.class,()->demo.run(null));
    }
}
