package com.nanopiva.citero.exception;

import com.nanopiva.citero.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Atrapa ResourceNotFoundException → HTTP 404
     * Se lanza cuando se busca una entidad que no existe.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Atrapa DuplicateResourceException → HTTP 409 Conflict
     * Se lanza cuando se intenta crear un recurso que ya existe (email duplicado, slug en uso, etc.)
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Atrapa BadRequestException → HTTP 400
     * Se lanza cuando los datos de la petición son inválidos o la operación no tiene sentido.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Atrapa UnauthorizedException → HTTP 401
     * Se lanza cuando la sesión/credenciales no son válidas (p. ej. refresh token inválido o revocado).
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponseDto> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Atrapa ForbiddenException → HTTP 403
     * Se lanza cuando el usuario está autenticado pero no tiene permiso sobre el recurso.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDto> handleForbidden(
            ForbiddenException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Atrapa ConflictException → HTTP 409
     * Se lanza cuando la operación no se puede completar por el estado actual del recurso
     * (p. ej. eliminar un servicio que tiene turnos asociados).
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleConflict(
            ConflictException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Atrapa errores de validación de Jakarta Validation (@NotBlank, @Email, etc.)
     * → HTTP 400
     *
     * Esto ocurre automáticamente cuando un DTO con @Valid falla la validación
     * antes de llegar al Controller.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // Extraer todos los mensajes de error de los campos inválidos
        List<String> validationErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

        ErrorResponseDto error = ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Error de validación en los datos enviados.")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Atrapa HttpMessageNotReadableException → HTTP 400
     * Se lanza cuando el cuerpo de la petición no se puede parsear (JSON malformado, tipo incompatible, etc.).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud es inválido o está mal formado.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Atrapa HandlerMethodValidationException → HTTP 400
     * Se lanza cuando fallan constraints a nivel de método (p. ej. validación de elementos
     * de un {@code List<@Valid ...>} con @Validated).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.BAD_REQUEST,
                "Error de validación en los datos enviados.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Atrapa MethodArgumentTypeMismatchException → HTTP 400
     * Se lanza cuando un path variable o query param no se puede convertir al tipo esperado
     * (p. ej. un estado o una fecha con formato inválido).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.BAD_REQUEST,
                "El valor del parámetro '" + ex.getName() + "' no es válido.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Atrapa MissingServletRequestParameterException → HTTP 400
     * Se lanza cuando falta un query param obligatorio.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.BAD_REQUEST,
                "Falta el parámetro requerido '" + ex.getParameterName() + "'.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Atrapa MaxUploadSizeExceededException → HTTP 413
     * Se lanza cuando un archivo subido supera el límite configurado en multipart.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Atrapa cualquier otra excepción no controlada → HTTP 500
     * Es el "fallback" para errores inesperados.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado en el servidor. Intente nuevamente más tarde.",
                request.getRequestURI()
        );


        log.error("Error inesperado en " + request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Atrapa BusinessRuleException → HTTP 422 Unprocessable Entity
     * Se lanza cuando los datos son sintácticamente correctos, pero violan una regla de negocio específica
     * (ej. "La hora de apertura debe ser anterior a la de cierre", "No tienes permiso para esta acción").
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = buildError(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Construye el ErrorResponseDto con los datos comunes.
     */
    private ErrorResponseDto buildError(HttpStatus status, String message, String path) {
        return ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
    }
}