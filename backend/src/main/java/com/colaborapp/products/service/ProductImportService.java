package com.colaborapp.products.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
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
    private static final String EXPECTED_HEADER_WITH_END_DATE =
            EXPECTED_HEADER + ",promocion_fin";
    private static final String EXPECTED_HEADER_WITH_GROUP =
            "nombre,precio,stock,descripcion,colaborador_email,grupo_promocional,promocion_tipo,promocion_valor";
    private static final String EXPECTED_HEADER_WITH_GROUP_AND_END_DATE =
            EXPECTED_HEADER_WITH_GROUP + ",promocion_fin";
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public ProductImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Selecciona un archivo CSV para comenzar la carga masiva.");
        }

        List<ProductImportErrorResponse> errors = new ArrayList<>();
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(decodeCsv(file)))) {
            String header = reader.readLine();
            validateHeader(header);
            char delimiter = resolveDelimiter(header);
            boolean headerIncludesPromotionGroup = includesPromotionGroup(header);

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> values;
                try {
                    values = parseCsvLine(line, delimiter);
                } catch (BusinessException exception) {
                    errors.add(new ProductImportErrorResponse(rowNumber, line, exception.getMessage()));
                    continue;
                }

                if (values.size() != 7 && values.size() != 8 && values.size() != 9) {
                    errors.add(new ProductImportErrorResponse(
                            rowNumber,
                            line,
                            "La fila debe tener las columnas de la plantilla de productos."));
                    continue;
                }

                ProductImportRow row = new ProductImportRow(
                        values.get(0),
                        values.get(1),
                        values.get(2),
                        values.get(3),
                        values.get(4),
                        headerIncludesPromotionGroup ? values.get(5) : "",
                        headerIncludesPromotionGroup ? values.get(6) : values.get(5),
                        headerIncludesPromotionGroup ? values.get(7) : values.get(6),
                        headerIncludesPromotionGroup
                                ? values.size() == 9 ? values.get(8) : ""
                                : values.size() == 8 ? values.get(7) : "");

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
        header = stripUtf8Bom(header);
        String normalizedHeader = normalizeHeader(header);
        if (normalizedHeader == null
                || (!EXPECTED_HEADER.equalsIgnoreCase(normalizedHeader)
                && !EXPECTED_HEADER_WITH_END_DATE.equalsIgnoreCase(normalizedHeader)
                && !EXPECTED_HEADER_WITH_GROUP.equalsIgnoreCase(normalizedHeader)
                && !EXPECTED_HEADER_WITH_GROUP_AND_END_DATE.equalsIgnoreCase(normalizedHeader))) {
            throw new BusinessException("La plantilla no coincide. Usa las columnas nombre,precio,stock,descripcion,colaborador_email,grupo_promocional,promocion_tipo,promocion_valor,promocion_fin.");
        }
    }

    private boolean includesPromotionGroup(String header) {
        String normalizedHeader = normalizeHeader(header);
        return EXPECTED_HEADER_WITH_GROUP.equalsIgnoreCase(normalizedHeader)
                || EXPECTED_HEADER_WITH_GROUP_AND_END_DATE.equalsIgnoreCase(normalizedHeader);
    }

    private char resolveDelimiter(String header) {
        String normalizedHeader = stripUtf8Bom(header);
        return normalizedHeader != null && normalizedHeader.contains(";") && !normalizedHeader.contains(",") ? ';' : ',';
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return null;
        }
        return stripUtf8Bom(header).strip().replace(';', ',');
    }

    String decodeCsv(MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        try {
            return decodeStrict(content, StandardCharsets.UTF_8);
        } catch (CharacterCodingException exception) {
            return decodeStrict(content, WINDOWS_1252);
        }
    }

    private String decodeStrict(byte[] content, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decoded = decoder.decode(ByteBuffer.wrap(content));
        return decoded.toString();
    }

    private String stripUtf8Bom(String value) {
        if (value == null || value.isEmpty() || value.charAt(0) != '\uFEFF') {
            return value;
        }
        return value.substring(1);
    }

    private ProductCreateRequest buildRequest(ProductImportRow row) {
        String name = requiredValue(row.name(), "Ingresa el nombre del producto.");
        BigDecimal price = parsePrice(row.price());
        Integer stock = parseStock(row.stock());
        User collaborator = resolveCollaborator(row.collaboratorEmail());
        ensureProductDoesNotExistForCollaborator(name, collaborator);
        ProductPromotionRequest promotion = parsePromotion(row.promotionType(), row.promotionValue(), row.promotionEndDate());

        return new ProductCreateRequest(
                null,
                collaborator.getId(),
                name,
                buildSku(name),
                null,
                blankToNull(row.promotionGroupName()),
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

    private void ensureProductDoesNotExistForCollaborator(String name, User collaborator) {
        boolean exists = productRepository.existsByOwnerUserIdAndNameIgnoreCaseAndStatus(
                collaborator.getId(),
                name.trim(),
                ProductStatus.ACTIVE);
        if (exists) {
            throw new BusinessException("Producto ya existe para esta Tienda. No se duplico.");
        }
    }

    private ProductPromotionRequest parsePromotion(String typeValue, String value, String endDateValue) {
        String normalizedType = blankToNull(typeValue);
        if (normalizedType == null) {
            return null;
        }

        PromotionType type = parsePromotionType(normalizedType);
        LocalDate endDate = parsePromotionEndDate(endDateValue);

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
            return new ProductPromotionRequest(type, quantity, promotionalPrice, null, null, null, endDate);
        }

        String normalizedValue = requiredValue(value, "Completa el porcentaje de la promocion.");
        boolean appliesToCash = false;
        boolean appliesToDebit = false;
        if (type == PromotionType.PAYMENT_METHOD_DISCOUNT) {
            String[] parts = normalizedValue.split(":", 2);
            if (parts.length == 2) {
                normalizedValue = parts[0];
                String paymentMethods = normalizeToken(parts[1]);
                appliesToCash = paymentMethods.contains("EFECTIVO") || paymentMethods.contains("CASH");
                appliesToDebit = paymentMethods.contains("DEBITO") || paymentMethods.contains("DEBIT");
            } else {
                appliesToCash = true;
                appliesToDebit = true;
            }
        }
        normalizedValue = normalizedValue.replace("%", "").trim();
        BigDecimal percentage = parsePositiveDecimal(normalizedValue, "El porcentaje de promocion no es valido.");
        return new ProductPromotionRequest(type, null, null, percentage, appliesToCash, appliesToDebit, endDate);
    }

    private LocalDate parsePromotionEndDate(String rawValue) {
        String normalizedValue = blankToNull(rawValue);
        if (normalizedValue == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalizedValue.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException("La fecha de termino de la promocion debe usar el formato AAAA-MM-DD.");
        }
    }

    private PromotionType parsePromotionType(String rawType) {
        String normalizedType = normalizeToken(rawType);
        return switch (normalizedType) {
            case "QUANTITY_BLOCK", "CANTIDAD", "PROMOCION_POR_CANTIDAD", "POR_CANTIDAD" -> PromotionType.QUANTITY_BLOCK;
            case "PERCENTAGE_DISCOUNT", "PORCENTAJE", "DESCUENTO_PORCENTUAL", "DESCUENTO" -> PromotionType.PERCENTAGE_DISCOUNT;
            case "PAYMENT_METHOD_DISCOUNT", "MEDIO_PAGO", "MEDIOS_DE_PAGO", "DESCUENTO_MEDIO_PAGO" -> PromotionType.PAYMENT_METHOD_DISCOUNT;
            default -> throw new BusinessException("La promocion debe ser CANTIDAD, PORCENTAJE o MEDIO_PAGO.");
        };
    }

    private String normalizeToken(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("(^_+|_+$)", "");
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

    List<String> parseCsvLine(String line) {
        return parseCsvLine(line, ',');
    }

    List<String> parseCsvLine(String line, char delimiter) {
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

            if (currentChar == delimiter && !insideQuotes) {
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
            String promotionGroupName,
            String promotionType,
            String promotionValue,
            String promotionEndDate) {
    }
}
