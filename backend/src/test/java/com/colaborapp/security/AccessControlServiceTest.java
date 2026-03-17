package com.colaborapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.colaborapp.markets.domain.Market;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.users.domain.RoleCode;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private StoreRepository storeRepository;

    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlService(authenticatedUserService, storeRepository);
    }

    @Test
    void adminSystemCanAccessAnyMarketAndStore() {
        ColaborAppUserPrincipal principal = principal(List.of("ADMIN_SYSTEM"), List.of(), List.of());

        assertThat(accessControlService.canAccessMarket(principal, 99L)).isTrue();
        assertThat(accessControlService.canAccessStore(principal, 88L)).isTrue();
    }

    @Test
    void adminMarketCanAccessAssignedMarketButNotAnother() {
        ColaborAppUserPrincipal principal = principal(List.of("ADMIN_MARKET"), List.of(10L), List.of());

        assertThat(accessControlService.canAccessMarket(principal, 10L)).isTrue();
        assertThat(accessControlService.canAccessMarket(principal, 11L)).isFalse();
    }

    @Test
    void collaboratorCanAccessAssignedMarketButNotAnother() {
        ColaborAppUserPrincipal principal = principal(List.of("COLLABORATOR"), List.of(20L), List.of());

        assertThat(accessControlService.canAccessMarket(principal, 20L)).isTrue();
        assertThat(accessControlService.canAccessMarket(principal, 21L)).isFalse();
    }

    @Test
    void storeUserCanAccessAssignedStoreButNotAnother() {
        ColaborAppUserPrincipal principal = principal(List.of("STORE_USER"), List.of(), List.of(30L));

        assertThat(accessControlService.canAccessStore(principal, 30L)).isTrue();
        assertThat(accessControlService.canAccessStore(principal, 31L)).isFalse();
    }

    @Test
    void requireMarketAccessThrowsWhenCurrentUserCannotAccessMarket() {
        ColaborAppUserPrincipal principal = principal(List.of("ADMIN_MARKET"), List.of(10L), List.of());
        when(authenticatedUserService.requireCurrentPrincipal()).thenReturn(principal);

        assertThatThrownBy(() -> accessControlService.requireMarketAccess(11L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission to perform this action.");
    }

    @Test
    void collaboratorCanAccessStoreInsideAssignedMarket() {
        ColaborAppUserPrincipal principal = principal(List.of("COLLABORATOR"), List.of(50L), List.of());
        when(storeRepository.findById(40L)).thenReturn(java.util.Optional.of(store(40L, 50L)));

        assertThat(accessControlService.canAccessStore(principal, 40L)).isTrue();
    }

    @Test
    void adminMarketCanManageUsersButNotMarkets() {
        ColaborAppUserPrincipal principal = principal(List.of("ADMIN_MARKET"), List.of(10L), List.of());
        when(authenticatedUserService.requireCurrentPrincipal()).thenReturn(principal);

        assertThat(accessControlService.canManageUsers()).isTrue();
        assertThat(accessControlService.canManageMarkets()).isFalse();
    }

    private ColaborAppUserPrincipal principal(List<String> roles, List<Long> marketIds, List<Long> storeIds) {
        return new ColaborAppUserPrincipal(1L, "user@colaborapp.cl", "User", roles, marketIds, storeIds, java.util.Map.of());
    }

    private Store store(Long storeId, Long marketId) {
        Market market = new Market();
        market.setId(marketId);

        Store store = new Store();
        store.setId(storeId);
        store.setMarket(market);
        return store;
    }
}
