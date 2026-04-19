package com.colaborapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private final EmailNormalizer emailNormalizer = new EmailNormalizer();

    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setCode(RoleCode.ADMIN_SYSTEM);
        adminRole.setName("Administrador sistema");
    }

    @Test
    void shouldCreateBootstrapAdminOnStartupWhenMissing() throws Exception {
        when(userRepository.findWithAccessByEmailIgnoreCase("efreirerojas@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                userRepository,
                roleRepository,
                emailNormalizer,
                "efreirerojas@gmail.com",
                "Eric Freire");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("efreirerojas@gmail.com");
        assertThat(saved.getFullName()).isEqualTo("Eric Freire");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getRoles()).extracting(Role::getCode).contains(RoleCode.ADMIN_SYSTEM);
    }

    @Test
    void shouldPromoteExistingBootstrapAdminIfNeeded() throws Exception {
        User user = new User();
        user.setEmail("efreirerojas@gmail.com");
        user.setFullName("Efrei");
        user.setAuthProvider("GOOGLE");
        user.setActive(false);

        when(userRepository.findWithAccessByEmailIgnoreCase("efreirerojas@gmail.com")).thenReturn(Optional.of(user));
        when(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                userRepository,
                roleRepository,
                emailNormalizer,
                "efreirerojas@gmail.com",
                "Eric Freire");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertThat(user.isActive()).isTrue();
        assertThat(user.getFullName()).isEqualTo("Eric Freire");
        assertThat(user.getRoles()).extracting(Role::getCode).contains(RoleCode.ADMIN_SYSTEM);
        verify(userRepository).save(user);
    }

    @Test
    void shouldDoNothingWhenBootstrapEmailIsBlank() throws Exception {
        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                userRepository,
                roleRepository,
                emailNormalizer,
                "",
                "");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).findWithAccessByEmailIgnoreCase(any());
    }
}
