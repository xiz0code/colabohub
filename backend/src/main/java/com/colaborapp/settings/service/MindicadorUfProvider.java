package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Component
@Primary
@Order(10)
public class MindicadorUfProvider implements UfProvider {

    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClient;

    public MindicadorUfProvider(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl("https://mindicador.cl")
                .build();
    }

    @Override
    public String providerName() {
        return "mindicador.cl";
    }

    @Override
    public Optional<BigDecimal> fetchLatestUfValue() {
        return fetch("/api/uf");
    }

    @Override
    public Optional<BigDecimal> fetchUfValue(LocalDate date) {
        return fetch("/api/uf/" + API_DATE_FORMAT.format(date));
    }

    private Optional<BigDecimal> fetch(String uri) {
        UfApiResponse response = restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(UfApiResponse.class);

        if (response == null || response.serie() == null || response.serie().isEmpty() || response.serie().getFirst().valor() == null) {
            return Optional.empty();
        }

        return Optional.of(Objects.requireNonNull(response.serie().getFirst().valor()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UfApiResponse(java.util.List<UfSerieItem> serie) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UfSerieItem(BigDecimal valor) {
    }
}
