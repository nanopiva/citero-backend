package com.nanopiva.citero.exception;

import com.nanopiva.citero.dto.ErrorResponseDto;
import com.nanopiva.citero.support.IntegrationTest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el mapeo de excepciones a códigos HTTP y la forma del {@link ErrorResponseDto}.
 *
 * <p>Los mapeos que no dependen de un endpoint real se prueban invocando directamente
 * los métodos del {@link GlobalExceptionHandler}. El caso de validación de Jakarta se
 * prueba de punta a punta con MockMvc contra un endpoint público.</p>
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest requestTo(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    private void assertError(ResponseEntity<ErrorResponseDto> response,
                             int status, String error, String message, String path) {
        assertEquals(status, response.getStatusCode().value(), "El status HTTP debe coincidir");
        ErrorResponseDto body = response.getBody();
        assertNotNull(body, "El cuerpo de error no debe ser nulo");
        assertEquals(status, body.getStatus(), "El status del cuerpo debe coincidir");
        assertEquals(error, body.getError(), "La razón HTTP debe coincidir");
        assertEquals(message, body.getMessage(), "El mensaje debe coincidir");
        assertEquals(path, body.getPath(), "El path debe coincidir");
        assertNotNull(body.getTimestamp(), "El timestamp debe estar presente");
    }

    @Test
    void resourceNotFoundMapsTo404() {
        assertError(
                handler.handleResourceNotFound(
                        new ResourceNotFoundException("Recurso no encontrado"), requestTo("/api/businesses/x")),
                404, "Not Found", "Recurso no encontrado", "/api/businesses/x");
    }

    @Test
    void duplicateResourceMapsTo409() {
        assertError(
                handler.handleDuplicateResource(
                        new DuplicateResourceException("El recurso ya existe"), requestTo("/api/businesses")),
                409, "Conflict", "El recurso ya existe", "/api/businesses");
    }

    @Test
    void badRequestMapsTo400() {
        assertError(
                handler.handleBadRequest(
                        new BadRequestException("Datos inválidos"), requestTo("/api/appointments")),
                400, "Bad Request", "Datos inválidos", "/api/appointments");
    }

    @Test
    void unauthorizedMapsTo401() {
        assertError(
                handler.handleUnauthorized(
                        new UnauthorizedException("No autorizado"), requestTo("/api/auth/refresh")),
                401, "Unauthorized", "No autorizado", "/api/auth/refresh");
    }

    @Test
    void businessRuleMapsTo422() {
        assertError(
                handler.handleBusinessRule(
                        new BusinessRuleException("La apertura debe ser anterior al cierre"),
                        requestTo("/api/businesses/1/config")),
                422, "Unprocessable Entity",
                "La apertura debe ser anterior al cierre", "/api/businesses/1/config");
    }

    @Test
    void maxUploadSizeMapsTo413() {
        assertError(
                handler.handleMaxUploadSize(
                        new MaxUploadSizeExceededException(10L), requestTo("/api/businesses/1/logo")),
                413, "Payload Too Large",
                "El archivo supera el tamaño máximo permitido.", "/api/businesses/1/logo");
    }

    @Test
    void unexpectedExceptionMapsTo500() {
        assertError(
                handler.handleGenericException(
                        new RuntimeException("boom"), requestTo("/api/desconocido")),
                500, "Internal Server Error",
                "Ocurrió un error inesperado en el servidor. Intente nuevamente más tarde.",
                "/api/desconocido");
    }

    @Test
    void validationErrorOnRegisterReturns400WithFieldMessages() throws Exception {
        String body = "{\"email\":\"no-es-email\",\"password\":\"123\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Error de validación en los datos enviados."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    void typeMismatchMapsTo400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "no-es-fecha", LocalDate.class, "date", null, new IllegalArgumentException("bad"));

        assertError(
                handler.handleTypeMismatch(ex, requestTo("/api/availability")),
                400, "Bad Request", "El valor del parámetro 'date' no es válido.", "/api/availability");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json valido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido o está mal formado."))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void refreshWithoutCookieReturns401ErrorBody() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("No hay una sesión activa."))
                .andExpect(jsonPath("$.path").value("/api/auth/refresh"));
    }
}
