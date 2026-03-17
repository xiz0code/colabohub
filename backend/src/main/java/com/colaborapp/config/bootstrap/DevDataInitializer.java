package com.colaborapp.config.bootstrap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.sales.domain.UfDailyValue;
import com.colaborapp.sales.repository.UfDailyValueRepository;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.stores.repository.StoreRepository;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.tenant.domain.TenantStatus;
import com.colaborapp.tenant.repository.TenantRepository;

@Configuration
@Profile("dev")
public class DevDataInitializer {

    @Bean
    ApplicationRunner developmentSeedData(
            TenantRepository tenantRepository,
            MarketRepository marketRepository,
            StoreRepository storeRepository,
            UfDailyValueRepository ufDailyValueRepository) {
        return args -> {
            Tenant tenant = tenantRepository.findFirstByOrderByIdAsc()
                    .orElseGet(() -> {
                        Tenant created = new Tenant();
                        created.setCode("main");
                        created.setName("ColaboHub Principal");
                        created.setStatus(TenantStatus.ACTIVE);
                        return tenantRepository.save(created);
                    });

            Market market = marketRepository.findByTenantIdOrderByNameAsc(tenant.getId()).stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Market created = new Market();
                        created.setTenant(tenant);
                        created.setName("Mercado Principal");
                        created.setEmail("mercado.principal@local.dev");
                        created.setCity("Santiago");
                        created.setCurrency("CLP");
                        created.setUfEnabled(true);
                        return marketRepository.save(created);
                    });

            Store store = storeRepository.findByTenantIdOrderByNameAsc(tenant.getId()).stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Store createdStore = new Store();
                        createdStore.setTenant(tenant);
                        createdStore.setMarket(market);
                        createdStore.setCode("MAIN-STORE");
                        createdStore.setName("Tienda Principal");
                        createdStore.setType(StoreType.PRIMARY);
                        createdStore.setStatus(StoreStatus.ACTIVE);
                        return storeRepository.save(createdStore);
                    });

            LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));
            ufDailyValueRepository.findByTenantIdAndEffectiveDate(tenant.getId(), today)
                    .orElseGet(() -> {
                        UfDailyValue ufDailyValue = new UfDailyValue();
                        ufDailyValue.setTenant(tenant);
                        ufDailyValue.setEffectiveDate(today);
                        ufDailyValue.setUfValue(new BigDecimal("39000.0000"));
                        ufDailyValue.setSource("DEV_SEED");
                        return ufDailyValueRepository.save(ufDailyValue);
                    });
        };
    }
}
