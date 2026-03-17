package com.colaborapp.security;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

@Service
public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GOOGLE_PROVIDER = "GOOGLE";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailNormalizer emailNormalizer;
    private final String bootstrapAdminEmail;

    @Autowired
    public GoogleOAuth2UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailNormalizer emailNormalizer,
            @Value("${app.auth.bootstrap-admin-email}") String bootstrapAdminEmail) {
        this(new DefaultOAuth2UserService(), userRepository, roleRepository, emailNormalizer, bootstrapAdminEmail);
    }

    GoogleOAuth2UserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailNormalizer emailNormalizer,
            String bootstrapAdminEmail) {
        this.delegate = delegate;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailNormalizer = emailNormalizer;
        this.bootstrapAdminEmail = emailNormalizer.normalize(bootstrapAdminEmail);
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String email = emailNormalizer.normalize(oauth2User.getAttribute("email"));
        String fullName = oauth2User.getAttribute("name");
        String subject = oauth2User.getAttribute("sub");

        if (email == null || email.isBlank()) {
            throw accessDenied("No pudimos leer tu correo desde Google. Intenta nuevamente con la cuenta correcta.");
        }

        User user = userRepository.findWithAccessByEmailIgnoreCase(email)
                .map(existing -> updateExistingUser(existing, fullName, subject, isBootstrapAdmin(email)))
                .orElseGet(() -> createBootstrapAdminIfAllowed(email, fullName, subject));

        user.setLastLoginAt(Instant.now());
        User savedUser = userRepository.save(user);
        return toPrincipal(savedUser, oauth2User);
    }

    private User updateExistingUser(User user, String fullName, String subject, boolean bootstrapAdmin) {
        if (!bootstrapAdmin && !user.isActive()) {
            throw accessDenied("Tu cuenta está desactivada en ColaboHub. Solicita ayuda al administrador de tu tienda.");
        }

        user.setEmail(emailNormalizer.normalize(user.getEmail()));
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        user.setAuthProvider(GOOGLE_PROVIDER);
        user.setProviderSubject(subject);

        if (bootstrapAdmin) {
            user.setActive(true);
            user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)
                    .orElseThrow(() -> new ResourceNotFoundException("No pudimos completar la configuración del administrador principal.")));
        }

        return user;
    }

    private User createBootstrapAdminIfAllowed(String email, String fullName, String subject) {
        if (!isBootstrapAdmin(email)) {
            throw accessDenied("No encontramos una cuenta asociada a este correo. Si tu tienda fue creada recientemente, solicita acceso al administrador.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName == null || fullName.isBlank() ? email : fullName.trim());
        user.setAuthProvider(GOOGLE_PROVIDER);
        user.setProviderSubject(subject);
        user.setActive(true);
        user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)
                .orElseThrow(() -> new ResourceNotFoundException("No pudimos completar la configuración del administrador principal.")));
        return user;
    }

    private ColaborAppUserPrincipal toPrincipal(User user, OAuth2User oauth2User) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .sorted()
                .toList();
        List<Long> marketIds = user.getMarkets().stream()
                .map(market -> market.getId())
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Long> storeIds = user.getStores().stream()
                .map(store -> store.getId())
                .sorted(Comparator.naturalOrder())
                .toList();

        return new ColaborAppUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                marketIds,
                storeIds,
                oauth2User.getAttributes());
    }

    private boolean isBootstrapAdmin(String email) {
        return bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank() && bootstrapAdminEmail.equals(email);
    }

    private String normalizeEmail(String email) {
        return emailNormalizer.normalize(email);
    }

    private OAuth2AuthenticationException accessDenied(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied", message, null), message);
    }
}
