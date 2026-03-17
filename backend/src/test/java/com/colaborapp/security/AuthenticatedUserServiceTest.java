package com.colaborapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFallbackToPrincipalSnapshotWhenDatabaseUserIsMissing() {
        ColaborAppUserPrincipal principal = new ColaborAppUserPrincipal(
                99L,
                "xizocode@gmail.com",
                "Xizo Code",
                List.of("ADMIN_SYSTEM"),
                List.of(10L),
                List.of(20L),
                Map.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));

        when(userRepository.findWithAccessById(99L)).thenReturn(Optional.empty());

        AuthenticatedUserService service = new AuthenticatedUserService(
                userRepository,
                roleRepository,
                new EmailNormalizer(),
                "xizocode@gmail.com");
        AuthenticatedUserService.CurrentAuthenticatedUser snapshot = service.getCurrentUserSnapshot();

        assertThat(snapshot.user().getId()).isEqualTo(99L);
        assertThat(snapshot.user().getEmail()).isEqualTo("xizocode@gmail.com");
        assertThat(snapshot.roles()).containsExactly("ADMIN_SYSTEM");
        assertThat(snapshot.marketIds()).containsExactly(10L);
        assertThat(snapshot.storeIds()).containsExactly(20L);
    }

    @Test
    void shouldResolveCurrentPrincipalFromAuthenticatedEmail() {
        User user = new User();
        user.setId(7L);
        user.setEmail("xizocode@gmail.com");
        user.setFullName("Xizo Code");
        user.setActive(true);

        Role role = new Role();
        role.setCode(RoleCode.ADMIN_SYSTEM);
        role.setName("ADMIN_SYSTEM");
        user.getRoles().add(role);

        var oauthUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "xizocode@gmail.com", "name", "Xizo Code"),
                "email");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oauthUser, "n/a", oauthUser.getAuthorities()));

        when(userRepository.findWithAccessByEmailIgnoreCase("xizocode@gmail.com")).thenReturn(Optional.of(user));

        AuthenticatedUserService service = new AuthenticatedUserService(
                userRepository,
                roleRepository,
                new EmailNormalizer(),
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = service.requireCurrentPrincipal();

        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.email()).isEqualTo("xizocode@gmail.com");
        assertThat(principal.roles()).containsExactly("ADMIN_SYSTEM");
    }

    @Test
    void shouldGrantBootstrapAdminRoleToExistingBootstrapUserWithoutPersistedRole() {
        User user = new User();
        user.setId(8L);
        user.setEmail("xizocode@gmail.com");
        user.setFullName("Xizo Code");
        user.setActive(true);

        var oauthUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "xizocode@gmail.com", "name", "Xizo Code"),
                "email");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oauthUser, "n/a", oauthUser.getAuthorities()));

        when(userRepository.findWithAccessByEmailIgnoreCase("xizocode@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.findWithAccessById(8L)).thenReturn(Optional.of(user));

        AuthenticatedUserService service = new AuthenticatedUserService(
                userRepository,
                roleRepository,
                new EmailNormalizer(),
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = service.requireCurrentPrincipal();
        AuthenticatedUserService.CurrentAuthenticatedUser snapshot = service.getCurrentUserSnapshot();

        assertThat(principal.roles()).containsExactly("ADMIN_SYSTEM");
        assertThat(snapshot.roles()).containsExactly("ADMIN_SYSTEM");
    }

    @Test
    void shouldAutoProvisionBootstrapAdminFromAuthenticatedEmail() {
        Role role = new Role();
        role.setCode(RoleCode.ADMIN_SYSTEM);
        role.setName("ADMIN_SYSTEM");

        var oauthUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "xizocode@gmail.com", "name", "Xizo Code"),
                "email");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(oauthUser, "n/a", oauthUser.getAuthorities()));

        when(userRepository.findWithAccessByEmailIgnoreCase("xizocode@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)).thenReturn(Optional.of(role));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        AuthenticatedUserService service = new AuthenticatedUserService(
                userRepository,
                roleRepository,
                new EmailNormalizer(),
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = service.requireCurrentPrincipal();

        assertThat(principal.userId()).isEqualTo(11L);
        assertThat(principal.email()).isEqualTo("xizocode@gmail.com");
        assertThat(principal.roles()).containsExactly("ADMIN_SYSTEM");
    }
}
