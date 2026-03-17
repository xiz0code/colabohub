package com.colaborapp.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.security.EmailNormalizer;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MarketAdminProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private MarketAdminProvisioningService service;
    private Role adminMarketRole;

    @BeforeEach
    void setUp() {
        service = new MarketAdminProvisioningService(userRepository, roleRepository, new EmailNormalizer());
        adminMarketRole = new Role();
        adminMarketRole.setId(1L);
        adminMarketRole.setCode(RoleCode.ADMIN_MARKET);
        adminMarketRole.setName("Administrador de Tienda");
    }

    @Test
    void shouldProvisionAdminMarketUserLinkedToCreatedMarket() {
        Market market = new Market();
        market.setId(15L);
        market.setName("Tienda Centro");
        market.setEmail("tienda.centro@correo.cl");
        market.setPhone("+56911111111");
        market.setContactName("Camila");
        market.setDescription("Tienda principal");

        when(userRepository.findByEmailIgnoreCase("tienda.centro@correo.cl")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.ADMIN_MARKET)).thenReturn(Optional.of(adminMarketRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User provisioned = service.provisionForMarket(market);

        assertThat(provisioned.getEmail()).isEqualTo("tienda.centro@correo.cl");
        assertThat(provisioned.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.ADMIN_MARKET);
        assertThat(provisioned.getMarkets()).extracting(Market::getId).containsExactly(15L);
        assertThat(provisioned.isActive()).isTrue();
    }

    @Test
    void shouldRejectDuplicateEmail() {
        Market market = new Market();
        market.setId(15L);
        market.setName("Tienda Centro");
        market.setEmail("tienda.centro@correo.cl");

        User existing = new User();
        existing.setId(20L);
        existing.setEmail("tienda.centro@correo.cl");

        when(userRepository.findByEmailIgnoreCase("tienda.centro@correo.cl")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.provisionForMarket(market))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Este correo ya está asociado a un usuario del sistema.");
    }
}
