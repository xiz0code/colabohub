package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Component
@Order(20)
public class FindicUfProvider implements UfProvider {

    private final RestClient restClient;

    public FindicUfProvider(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl("https://findic.cl")
                .build();
    }

    @Override
    public String providerName() {
        return "findic.cl";
    }

    @Override
    public Optional<BigDecimal> fetchLatestUfValue() {
        FindicUfResponse response = restClient.get()
                .uri("/api/uf")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(FindicUfResponse.class);

        if (response == null || response.serie() == null || response.serie().isEmpty() || response.serie().getFirst().valor() == null) {
            return Optional.empty();
        }

        return Optional.of(Objects.requireNonNull(response.serie().getFirst().valor()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FindicUfResponse(java.util.List<FindicUfSerieItem> serie) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FindicUfSerieItem(BigDecimal valor) {
    }
}
