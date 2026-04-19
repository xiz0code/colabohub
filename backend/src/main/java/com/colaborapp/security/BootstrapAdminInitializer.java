package com.colaborapp.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailNormalizer emailNormalizer;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminName;

    public BootstrapAdminInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailNormalizer emailNormalizer,
            @Value("${app.auth.bootstrap-admin-email:}") String bootstrapAdminEmail,
            @Value("${app.auth.bootstrap-admin-name:}") String bootstrapAdminName) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailNormalizer = emailNormalizer;
        this.bootstrapAdminEmail = emailNormalizer.normalize(bootstrapAdminEmail);
        this.bootstrapAdminName = normalizeName(bootstrapAdminName);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()) {
            return;
        }

        User user = userRepository.findWithAccessByEmailIgnoreCase(bootstrapAdminEmail)
                .orElseGet(this::createBootstrapAdmin);

        boolean changed = false;
        if (!user.isActive()) {
            user.setActive(true);
            changed = true;
        }

        String desiredName = resolveBootstrapAdminName();
        if (!desiredName.equals(user.getFullName())) {
            user.setFullName(desiredName);
            changed = true;
        }

        boolean hasAdminRole = user.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.ADMIN_SYSTEM);
        if (!hasAdminRole) {
            user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)
                    .orElseThrow(() -> new ResourceNotFoundException("No pudimos completar la configuracion del administrador principal.")));
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
            log.info("Bootstrap admin ensured for {}", bootstrapAdminEmail);
        }
    }

    private User createBootstrapAdmin() {
        User user = new User();
        user.setEmail(bootstrapAdminEmail);
        user.setFullName(resolveBootstrapAdminName());
        user.setAuthProvider("GOOGLE");
        user.setActive(true);
        user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN_SYSTEM)
                .orElseThrow(() -> new ResourceNotFoundException("No pudimos completar la configuracion del administrador principal.")));
        return userRepository.save(user);
    }

    private String resolveBootstrapAdminName() {
        return bootstrapAdminName == null || bootstrapAdminName.isBlank() ? bootstrapAdminEmail : bootstrapAdminName;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
