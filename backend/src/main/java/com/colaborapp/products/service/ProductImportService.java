package com.colaborapp.products.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.products.web.dto.ProductCreateRequest;
import com.colaborapp.products.web.dto.ProductImportErrorResponse;
import com.colaborapp.products.web.dto.ProductImportResponse;
import com.colaborapp.products.web.dto.ProductPromotionRequest;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final String EXPECTED_HEADER =
            "nombre,precio,stock,descripcion,colaborador_email,promocion_tipo,promocion_valor";

    private final ProductService productService;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public ProductImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Selecciona un archivo CSV para comenzar la carga masiva.");
        }

        List<ProductImportErrorResponse> errors = new ArrayList<>();
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            validateHeader(header);

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> values;
                try {
                    values = parseCsvLine(line);
                } catch (BusinessException exception) {
                    errors.add(new ProductImportErrorResponse(rowNumber, line, exception.getMessage()));
                    continue;
                }

                if (values.size() != 7) {
                    errors.add(new ProductImportErrorResponse(
                            rowNumber,
                            line,
                            "La fila debe tener exactamente 7 columnas siguiendo la plantilla."));
                    continue;
                }

                ProductImportRow row = new ProductImportRow(
                        values.get(0),
                        values.get(1),
                        values.get(2),
                        values.get(3),
                        values.get(4),
                        values.get(5),
                        values.get(6));

                try {
                    ProductCreateRequest request = buildRequest(row);
                    Boolean created = transactionTemplate.execute(status -> {
                        productService.createProduct(request);
                        return Boolean.TRUE;
                    });
                    if (Boolean.TRUE.equals(created)) {
                        successCount++;
                    }
                } catch (Exception exception) {
                    errors.add(new ProductImportErrorResponse(
                            rowNumber,
                            line,
                            normalizeMessage(exception)));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("No pudimos leer el archivo CSV. Revisa el formato e intenta nuevamente.");
        }

        return new ProductImportResponse(successCount, errors.size(), List.copyOf(errors));
    }

    private void validateHeader(String header) {
        if (header == null || !EXPECTED_HEADER.equalsIgnoreCase(header.strip())) {
            throw new BusinessException("La plantilla no coincide. Usa las columnas nombre,precio,stock,descripcion,colaborador_email,promocion_tipo,promocion_valor.");
        }
    }

    private ProductCreateRequest buildRequest(ProductImportRow row) {
        String name = requiredValue(row.name(), "Ingresa el nombre del producto.");
        BigDecimal price = parsePrice(row.price());
        Integer stock = parseStock(row.stock());
        User collaborator = resolveCollaborator(row.collaboratorEmail());
        ProductPromotionRequest promotion = parsePromotion(row.promotionType(), row.promotionValue());

        return new ProductCreateRequest(
                null,
                collaborator.getId(),
                name,
                buildSku(name),
                blankToNull(row.description()),
                price,
                null,
                stock,
                promotion);
    }

    private User resolveCollaborator(String email) {
        String normalizedEmail = requiredValue(email, "Cada fila debe indicar el correo del colaborador responsable.")
                .trim()
                .toLowerCase(Locale.ROOT);

        User collaborator = userRepository.findWithAccessByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BusinessException("No encontramos al colaborador indicado por correo."));

        boolean isCollaborator = collaborator.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.STORE_USER);
        if (!isCollaborator) {
            throw new BusinessException("El correo indicado debe pertenecer a un Colaborador.");
        }

        return collaborator;
    }

    private ProductPromotionRequest parsePromotion(String typeValue, String value) {
        String normalizedType = blankToNull(typeValue);
        if (normalizedType == null) {
            return null;
        }

        PromotionType type;
        try {
            type = PromotionType.valueOf(normalizedType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("La promocion debe ser QUANTITY_BLOCK o PERCENTAGE_DISCOUNT.");
        }

        if (type == PromotionType.QUANTITY_BLOCK) {
            String normalizedValue = requiredValue(value, "Completa el valor de la promocion por cantidad.");
            String[] parts = normalizedValue.toLowerCase(Locale.ROOT).split("x", 2);
            if (parts.length != 2) {
                throw new BusinessException("La promocion por cantidad debe usar el formato 3x4000.");
            }
            Integer quantity = parseInteger(parts[0].trim(), "La cantidad promocional no es valida.");
            if (quantity < 2) {
                throw new BusinessException("La cantidad promocional debe ser 2 o mayor.");
            }
            BigDecimal promotionalPrice = parsePrice(parts[1].trim());
            return new ProductPromotionRequest(type, quantity, promotionalPrice, null);
        }

        String normalizedValue = requiredValue(value, "Completa el porcentaje de la promocion.");
        normalizedValue = normalizedValue.replace("%", "").trim();
        BigDecimal percentage = parsePositiveDecimal(normalizedValue, "El porcentaje de promocion no es valido.");
        return new ProductPromotionRequest(type, null, null, percentage);
    }

    private BigDecimal parsePrice(String rawValue) {
        BigDecimal value = parsePositiveDecimal(rawValue, "El precio debe ser un numero mayor a 0.");
        return value.setScale(2);
    }

    private Integer parseStock(String rawValue) {
        Integer value = parseInteger(rawValue, "El stock debe ser un numero entero.");
        if (value < 0) {
            throw new BusinessException("El stock debe ser 0 o mayor.");
        }
        return value;
    }

    private BigDecimal parsePositiveDecimal(String rawValue, String message) {
        try {
            BigDecimal value = new BigDecimal(requiredValue(rawValue, message).replace(",", ".").trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(message);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new BusinessException(message);
        }
    }

    private Integer parseInteger(String rawValue, String message) {
        try {
            return Integer.valueOf(requiredValue(rawValue, message).trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(message);
        }
    }

    private String requiredValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeMessage(Exception exception) {
        Throwable candidate = exception;
        while (candidate.getCause() != null && !(candidate instanceof BusinessException)) {
            candidate = candidate.getCause();
        }
        return Optional.ofNullable(candidate.getMessage()).orElse("No pudimos importar esta fila.");
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                boolean escapedQuote = insideQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"';
                if (escapedQuote) {
                    current.append('"');
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !insideQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        if (insideQuotes) {
            throw new BusinessException("La fila contiene comillas sin cerrar.");
        }

        values.add(current.toString().trim());
        return values;
    }

    private String buildSku(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        String base = normalized.isBlank() ? "PRODUCTO" : normalized;
        return (base.length() > 40 ? base.substring(0, 40) : base) + "-" + System.nanoTime();
    }

    private record ProductImportRow(
            String name,
            String price,
            String stock,
            String description,
            String collaboratorEmail,
            String promotionType,
            String promotionValue) {
    }
}
