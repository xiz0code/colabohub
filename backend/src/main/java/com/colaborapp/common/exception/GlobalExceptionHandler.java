package com.colaborapp.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import com.colaborapp.common.alert.CriticalErrorAlertService;

import jakarta.persistence.OptimisticLockException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectProvider<CriticalErrorAlertService> criticalErrorAlertServiceProvider;

    public GlobalExceptionHandler(ObjectProvider<CriticalErrorAlertService> criticalErrorAlertServiceProvider) {
        this.criticalErrorAlertServiceProvider = criticalErrorAlertServiceProvider;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, WebRequest request) {
        notifyIfNeeded(HttpStatus.NOT_FOUND, request, exception);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "No encontrado", exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception, WebRequest request) {
        notifyIfNeeded(HttpStatus.BAD_REQUEST, request, exception);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Solicitud invalida", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, WebRequest request) {
        notifyIfNeeded(HttpStatus.BAD_REQUEST, request, exception);
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.ofValidation(
                        HttpStatus.BAD_REQUEST.value(),
                        "Campos invalidos",
                        "Revisa los datos ingresados e intentalo nuevamente.",
                        errors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception, WebRequest request) {
        notifyIfNeeded(HttpStatus.CONFLICT, request, exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "Conflicto de datos",
                        "No pudimos guardar los cambios porque entran en conflicto con informacion existente."));
    }

    @ExceptionHandler({ ObjectOptimisticLockingFailureException.class, OptimisticLockException.class })
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(Exception exception, WebRequest request) {
        notifyIfNeeded(HttpStatus.CONFLICT, request, toException(exception));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        HttpStatus.CONFLICT.value(),
                        "Actualizacion simultanea",
                        "Este registro cambio mientras lo estabas editando. Vuelve a intentarlo."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        "Acceso denegado",
                        resolveAccessDeniedMessage(exception)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, WebRequest request) {
        notifyIfNeeded(HttpStatus.INTERNAL_SERVER_ERROR, request, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error interno",
                        "Ocurrio un problema inesperado. Intenta nuevamente en unos minutos."));
    }

    private String resolveAccessDeniedMessage(AccessDeniedException exception) {
        if (exception.getMessage() == null
                || exception.getMessage().isBlank()
                || "You do not have permission to perform this action.".equals(exception.getMessage())) {
            return "No tienes permiso para realizar esta accion.";
        }
        return exception.getMessage();
    }

    private void notifyIfNeeded(HttpStatus status, WebRequest request, Exception exception) {
        CriticalErrorAlertService alertService = criticalErrorAlertServiceProvider.getIfAvailable();
        if (alertService != null) {
            alertService.notifyIfNeeded(status, resolveEndpoint(request), exception);
        }
    }

    private String resolveEndpoint(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "desconocido";
    }

    private Exception toException(Exception exception) {
        return exception instanceof Exception runtimeException ? runtimeException : new RuntimeException(exception);
    }
}
