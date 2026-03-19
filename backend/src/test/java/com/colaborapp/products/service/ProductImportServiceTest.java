package com.colaborapp.products.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.products.web.dto.ProductCreateRequest;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.users.domain.Role;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductImportServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ProductImportService productImportService;

    private User collaborator;

    @BeforeEach
    void setUp() {
        collaborator = new User();
        collaborator.setId(15L);
        collaborator.setEmail("camila@example.com");
        collaborator.setFullName("Camila");
        Role collaboratorRole = new Role();
        collaboratorRole.setCode(RoleCode.STORE_USER);
        collaborator.getRoles().add(collaboratorRole);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void shouldImportValidRowsAndCollectPerRowErrors() {
        when(userRepository.findWithAccessByEmailIgnoreCase("camila@example.com")).thenReturn(Optional.of(collaborator));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "productos.csv",
                "text/csv",
                ("""
                        nombre,precio,stock,descripcion,colaborador_email,promocion_tipo,promocion_valor
                        Sticker BTS,2000,6,Pack brillante,camila@example.com,,
                        Llavero TXT,0,3,Precio invalido,camila@example.com,,
                        Album Seventeen,3500,2,Promo,camila@example.com,QUANTITY_BLOCK,3x9000
                        """
                ).getBytes(StandardCharsets.UTF_8));

        var response = productImportService.importCsv(file);

        ArgumentCaptor<ProductCreateRequest> requestCaptor = ArgumentCaptor.forClass(ProductCreateRequest.class);
        verify(productService, org.mockito.Mockito.times(2)).createProduct(requestCaptor.capture());

        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.errorCount()).isEqualTo(1);
        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.message()).isEqualTo("El precio debe ser un numero mayor a 0.");
        });

        assertThat(requestCaptor.getAllValues().getFirst().ownerUserId()).isEqualTo(15L);
        assertThat(requestCaptor.getAllValues().getFirst().salePrice()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(requestCaptor.getAllValues().getLast().promotion()).isNotNull();
        assertThat(requestCaptor.getAllValues().getLast().promotion().type()).isEqualTo(PromotionType.QUANTITY_BLOCK);
    }

    @Test
    void shouldRejectInvalidHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "productos.csv",
                "text/csv",
                "nombre,precio\nSticker BTS,2000".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> productImportService.importCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La plantilla no coincide. Usa las columnas nombre,precio,stock,descripcion,colaborador_email,promocion_tipo,promocion_valor.");
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "productos.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> productImportService.importCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Selecciona un archivo CSV para comenzar la carga masiva.");
    }
}
