package com.colaborapp.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.security.EmailNormalizer;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.RoleRepository;
import com.colaborapp.users.repository.UserRepository;
import com.colaborapp.users.web.dto.UserRequest;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private AccessControlService accessControlService;

    private final EmailNormalizer emailNormalizer = new EmailNormalizer();

    private UserService userService;

    private Tenant tenant;
    private Market market;
    private Store store;
    private Role storeUserRole;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                roleRepository,
                marketRepository,
                storeRepository,
                currentTenantProvider,
                emailNormalizer,
                accessControlService);

        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(7L);
        market.setName("Tienda Norte");

        store = new Store();
        store.setId(15L);
        store.setMarket(market);

        storeUserRole = new Role();
        storeUserRole.setCode(RoleCode.STORE_USER);
        storeUserRole.setName("STORE_USER");
    }

    @Test
    void adminMarketCanManageCollaboratorsOnlyInAllowedScope() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(false);
        when(accessControlService.hasRole(RoleCode.ADMIN_MARKET)).thenReturn(true);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(7L));
        when(accessControlService.currentStoreIds()).thenReturn(List.of());
        when(userRepository.findByEmailIgnoreCase("colab@correo.cl")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.STORE_USER)).thenReturn(Optional.of(storeUserRole));
        when(marketRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(market));
        when(storeRepository.findByIdAndTenantId(15L, 1L)).thenReturn(Optional.of(store));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return user;
        });

        var response = userService.createUser(new UserRequest(
                "colab@correo.cl",
                "Colaborador Uno",
                "+56999999999",
                "Jefe Local",
                "Apoyo en ventas",
                new BigDecimal("120000.00"),
                LocalDate.of(2026, 3, 1),
                "Stand 12",
                false,
                RoleCode.STORE_USER,
                List.of(7L),
                List.of(15L),
                true));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.roles()).containsExactly("STORE_USER");
        assertThat(response.marketIds()).containsExactly(7L);
        assertThat(response.monthlyRent()).isEqualByComparingTo("120000.00");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(response.standNumber()).isEqualTo("Stand 12");
    }

    @Test
    void adminMarketCannotCreateTiendaAdmins() {
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(false);
        when(accessControlService.hasRole(RoleCode.ADMIN_MARKET)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(new UserRequest(
                "admin.tienda@correo.cl",
                "Admin Tienda",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                RoleCode.ADMIN_MARKET,
                List.of(7L),
                List.of(),
                true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }

    @Test
    void adminMarketCannotManageCollaboratorOutsideAllowedScope() {
        when(accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)).thenReturn(false);
        when(accessControlService.hasRole(RoleCode.ADMIN_MARKET)).thenReturn(true);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(7L));
        when(userRepository.findByEmailIgnoreCase("otro@correo.cl")).thenReturn(Optional.empty());
        when(roleRepository.findByCode(RoleCode.STORE_USER)).thenReturn(Optional.of(storeUserRole));

        assertThatThrownBy(() -> userService.createUser(new UserRequest(
                "otro@correo.cl",
                "Otro Colaborador",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                RoleCode.STORE_USER,
                List.of(8L),
                List.of(),
                true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }
}
