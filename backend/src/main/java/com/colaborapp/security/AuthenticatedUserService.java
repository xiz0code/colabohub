package com.colaborapp.security;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailNormalizer emailNormalizer;
    private final String bootstrapAdminEmail;

    public AuthenticatedUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailNormalizer emailNormalizer,
            @Value("${app.auth.bootstrap-admin-email:}") String bootstrapAdminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailNormalizer = emailNormalizer;
        this.bootstrapAdminEmail = emailNormalizer.normalize(bootstrapAdminEmail);
    }

    public ColaborAppUserPrincipal requireCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ResourceNotFoundException("No encontramos una cuenta asociada a este correo. Si tu tienda fue creada recientemente, solicita acceso al administrador.");
        }
        if (authentication.getPrincipal() instanceof ColaborAppUserPrincipal principal) {
            return principal;
        }

        String email = resolveAuthenticatedEmail(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos una cuenta asociada a este correo. Si tu tienda fue creada recientemente, solicita acceso al administrador."));

        User user = userRepository.findWithAccessByEmailIgnoreCase(email)
                .orElseGet(() -> createBootstrapAdminIfAllowed(email));

        return toPrincipal(user);
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        ColaborAppUserPrincipal principal = requireCurrentPrincipal();
        return userRepository.findWithAccessById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos una cuenta asociada a este correo. Si tu tienda fue creada recientemente, solicita acceso al administrador."));
    }

    @Transactional(readOnly = true)
    public CurrentAuthenticatedUser getCurrentUserSnapshot() {
        ColaborAppUserPrincipal principal = requireCurrentPrincipal();
        return userRepository.findWithAccessById(principal.userId())
                .map(this::toSnapshot)
                .orElseGet(() -> snapshotFromPrincipal(principal));
    }

    public record CurrentAuthenticatedUser(
            User user,
            List<String> roles,
            List<Long> marketIds,
            List<Long> storeIds,
            List<String> marketNames) {
    }

    private CurrentAuthenticatedUser toSnapshot(User user) {
        List<String> roles = resolveRoleNames(
                user.getEmail(),
                user.getRoles().stream().map(role -> role.getCode().name()).toList());
        List<Long> marketIds = user.getMarkets().stream()
                .map(market -> market.getId())
                .sorted()
                .toList();
        List<Long> storeIds = user.getStores().stream()
                .map(store -> store.getId())
                .sorted()
                .toList();
        List<String> marketNames = user.getMarkets().stream()
                .map(market -> market.getName())
                .sorted()
                .toList();
        return new CurrentAuthenticatedUser(user, roles, marketIds, storeIds, marketNames);
    }

    private CurrentAuthenticatedUser snapshotFromPrincipal(ColaborAppUserPrincipal principal) {
        User user = new User();
        user.setId(principal.userId());
        user.setEmail(principal.email());
        user.setFullName(principal.fullName());
        user.setActive(true);

        List<String> roles = resolveRoleNames(principal.email(), principal.roles());
        List<Role> roleEntities = roles.stream()
                .map(this::toRole)
                .toList();
        user.getRoles().addAll(roleEntities);

        List<Long> marketIds = principal.marketIds() == null ? List.of() : List.copyOf(principal.marketIds());
        List<Long> storeIds = principal.storeIds() == null ? List.of() : List.copyOf(principal.storeIds());
        List<String> marketNames = List.of();

        return new CurrentAuthenticatedUser(user, roles, marketIds, storeIds, marketNames);
    }

    private Role toRole(String roleName) {
        Role role = new Role();
        role.setCode(RoleCode.valueOf(roleName));
        role.setName(roleName);
        return role;
    }

    private Optional<String> resolveAuthenticatedEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2User oauth2User) {
            String email = emailNormalizer.normalize(oauth2User.getAttribute("email"));
            if (email != null && !email.isBlank()) {
                return Optional.of(email);
            }
        }

        String name = emailNormalizer.normalize(authentication.getName());
        if (name != null && !name.isBlank()) {
            return Optional.of(name);
        }

        return Optional.empty();
    }

    private User createBootstrapAdminIfAllowed(String email) {
        if (!isBootstrapAdmin(email)) {
            throw new ResourceNotFoundException("No encontramos una cuenta asociada a este correo. Si tu tienda fue creada recientemente, solicita acceso al administrador.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setAuthProvider("GOOGLE");
        user.setActive(true);
        user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)
                .orElseThrow(() -> new ResourceNotFoundException("No pudimos completar la configuración de acceso del administrador principal.")));
        return userRepository.save(user);
    }

    private boolean isBootstrapAdmin(String email) {
        return bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank() && bootstrapAdminEmail.equals(email);
    }

    private ColaborAppUserPrincipal toPrincipal(User user) {
        List<String> roles = resolveRoleNames(
                user.getEmail(),
                user.getRoles().stream().map(role -> role.getCode().name()).toList());
        List<Long> marketIds = user.getMarkets().stream()
                .map(market -> market.getId())
                .sorted()
                .toList();
        List<Long> storeIds = user.getStores().stream()
                .map(store -> store.getId())
                .sorted()
                .toList();

        return new ColaborAppUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                marketIds,
                storeIds,
                java.util.Map.of("email", user.getEmail()));
    }

    private List<String> resolveRoleNames(String email, List<String> roles) {
        Stream<String> baseRoles = roles == null ? Stream.empty() : roles.stream();
        Stream<String> bootstrapRole = isBootstrapAdmin(email) ? Stream.of(RoleCode.ADMIN_SYSTEM.name()) : Stream.empty();
        return Stream.concat(baseRoles, bootstrapRole)
                .distinct()
                .sorted()
                .toList();
    }
}
