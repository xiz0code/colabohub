package com.colaborapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;
import com.colaborapp.markets.domain.Market;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private final EmailNormalizer emailNormalizer = new EmailNormalizer();

    private Role adminRole;
    private Role collaboratorRole;
    private Role marketAdminRole;
    private OAuth2UserRequest userRequest;

    @BeforeEach
    void setUp() {
        adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setCode(RoleCode.ADMIN_SYSTEM);
        adminRole.setName("Administrador sistema");

        collaboratorRole = new Role();
        collaboratorRole.setId(2L);
        collaboratorRole.setCode(RoleCode.COLLABORATOR);
        collaboratorRole.setName("Colaborador");

        marketAdminRole = new Role();
        marketAdminRole.setId(3L);
        marketAdminRole.setCode(RoleCode.ADMIN_MARKET);
        marketAdminRole.setName("Administrador de Tienda");

        userRequest = new OAuth2UserRequest(
                ClientRegistration.withRegistrationId("google")
                        .clientId("client-id")
                        .clientSecret("client-secret")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                        .scope("openid", "profile", "email")
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://oauth2.googleapis.com/token")
                        .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                        .userNameAttributeName("sub")
                        .clientName("Google")
                        .build(),
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "token-value",
                        Instant.now(),
                        Instant.now().plusSeconds(300)));
    }

    @Test
    void shouldAutoCreateBootstrapAdminWhenUserDoesNotExist() {
        when(userRepository.findWithAccessByEmailIgnoreCase("xizocode@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        GoogleOAuth2UserService service = new GoogleOAuth2UserService(
                delegateReturning(oauthUser("xizocode@gmail.com", "Xizo Code", "google-sub-1")),
                userRepository,
                roleRepository,
                emailNormalizer,
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = (ColaborAppUserPrincipal) service.loadUser(userRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("xizocode@gmail.com");
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getAuthProvider()).isEqualTo("GOOGLE");
        assertThat(savedUser.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.ADMIN_SYSTEM);
        assertThat(principal.userId()).isEqualTo(10L);
        assertThat(principal.roles()).containsExactly("ADMIN_SYSTEM");
    }

    @Test
    void shouldAllowExistingActiveUser() {
        User existingUser = new User();
        existingUser.setId(20L);
        existingUser.setEmail("colab@colaborapp.cl");
        existingUser.setFullName("Colaborador Uno");
        existingUser.setAuthProvider("GOOGLE");
        existingUser.setActive(true);
        existingUser.getRoles().add(collaboratorRole);

        when(userRepository.findWithAccessByEmailIgnoreCase("colab@colaborapp.cl")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        GoogleOAuth2UserService service = new GoogleOAuth2UserService(
                delegateReturning(oauthUser("colab@colaborapp.cl", "Colaborador Uno", "google-sub-2")),
                userRepository,
                roleRepository,
                emailNormalizer,
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = (ColaborAppUserPrincipal) service.loadUser(userRequest);

        assertThat(existingUser.getLastLoginAt()).isNotNull();
        assertThat(existingUser.getProviderSubject()).isEqualTo("google-sub-2");
        assertThat(principal.email()).isEqualTo("colab@colaborapp.cl");
        assertThat(principal.roles()).containsExactly("COLLABORATOR");
    }

    @Test
    void shouldAllowProvisionedMarketAdminUser() {
        User existingUser = new User();
        existingUser.setId(23L);
        existingUser.setEmail("tienda@correo.cl");
        existingUser.setFullName("Admin Tienda");
        existingUser.setAuthProvider("GOOGLE");
        existingUser.setActive(true);
        existingUser.getRoles().add(marketAdminRole);

        Market market = new Market();
        market.setId(9L);
        existingUser.getMarkets().add(market);

        when(userRepository.findWithAccessByEmailIgnoreCase("tienda@correo.cl")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        GoogleOAuth2UserService service = new GoogleOAuth2UserService(
                delegateReturning(oauthUser("tienda@correo.cl", "Admin Tienda", "google-sub-market")),
                userRepository,
                roleRepository,
                emailNormalizer,
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = (ColaborAppUserPrincipal) service.loadUser(userRequest);

        assertThat(principal.roles()).containsExactly("ADMIN_MARKET");
        assertThat(principal.marketIds()).containsExactly(9L);
    }

    @Test
    void shouldGrantAdminSystemRoleToExistingBootstrapAdmin() {
        User existingUser = new User();
        existingUser.setId(22L);
        existingUser.setEmail("xizocode@gmail.com");
        existingUser.setFullName("Xizo Code");
        existingUser.setAuthProvider("GOOGLE");
        existingUser.setActive(true);

        when(userRepository.findWithAccessByEmailIgnoreCase("xizocode@gmail.com")).thenReturn(Optional.of(existingUser));
        when(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        GoogleOAuth2UserService service = new GoogleOAuth2UserService(
                delegateReturning(oauthUser("xizocode@gmail.com", "Xizo Code", "google-sub-bootstrap")),
                userRepository,
                roleRepository,
                emailNormalizer,
                "xizocode@gmail.com");

        ColaborAppUserPrincipal principal = (ColaborAppUserPrincipal) service.loadUser(userRequest);

        assertThat(existingUser.getRoles()).extracting(Role::getCode).contains(RoleCode.ADMIN_SYSTEM);
        assertThat(principal.roles()).containsExactly("ADMIN_SYSTEM");
    }

    @Test
    void shouldDenyNonExistingUserWhenEmailIsNotBootstrapAdmin() {
        when(userRepository.findWithAccessByEmailIgnoreCase("unknown@colaborapp.cl")).thenReturn(Optional.empty());

        GoogleOAuth2UserService service = new GoogleOAuth2UserService(
                delegateReturning(oauthUser("unknown@colaborapp.cl", "Unknown User", "google-sub-3")),
                userRepository,
                roleRepository,
                emailNormalizer,
                "xizocode@gmail.com");

        assertThatThrownBy(() -> service.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("No encontramos una cuenta asociada a este correo");
    }

    @Test
    void shouldDenyInactiveUser() {
        User inactiveUser = new User();
        inactiveUser.setId(21L);
        inactiveUser.setEmail("inactive@colaborapp.cl");
        inactiveUser.setFullName("Inactive User");
        inactiveUser.setAuthProvider("GOOGLE");
        inactiveUser.setActive(false);
        inactiveUser.getRoles().add(collaboratorRole);

        when(userRepository.findWithAccessByEmailIgnoreCase("inactive@colaborapp.cl")).thenReturn(Optional.of(inactiveUser));

        GoogleOAuth2UserService service = new GoogleOAuth2UserService(
                delegateReturning(oauthUser("inactive@colaborapp.cl", "Inactive User", "google-sub-4")),
                userRepository,
                roleRepository,
                emailNormalizer,
                "xizocode@gmail.com");

        assertThatThrownBy(() -> service.loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Tu cuenta está desactivada en ColaboHub");
    }

    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegateReturning(OAuth2User oauth2User) {
        return request -> oauth2User;
    }

    private OAuth2User oauthUser(String email, String name, String sub) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "email", email,
                        "name", name,
                        "sub", sub),
                "sub");
    }
}
