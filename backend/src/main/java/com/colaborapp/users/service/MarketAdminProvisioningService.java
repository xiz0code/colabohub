package com.colaborapp.users.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.security.EmailNormalizer;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarketAdminProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailNormalizer emailNormalizer;

    @Transactional
    public User provisionForMarket(Market market) {
        String normalizedEmail = emailNormalizer.normalize(market.getEmail());
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(existing -> {
            throw new BusinessException("Este correo ya está asociado a un usuario del sistema.");
        });

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFullName(resolveFullName(market));
        user.setPhone(market.getPhone());
        user.setContactName(market.getContactName());
        user.setDescription(market.getDescription());
        user.setAuthProvider("GOOGLE");
        user.setActive(true);
        user.getRoles().add(roleRepository.findByCode(RoleCode.ADMIN_MARKET)
                .orElseThrow(() -> new ResourceNotFoundException("No pudimos completar la configuración de acceso para esta tienda.")));
        user.getMarkets().add(market);
        return userRepository.save(user);
    }

    private String resolveFullName(Market market) {
        if (market.getContactName() != null && !market.getContactName().isBlank()) {
            return market.getContactName().trim();
        }
        return market.getName();
    }
}
