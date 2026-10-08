package com.nanopiva.citero.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nanopiva.citero.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

/**
 * Escribe respuestas de error JSON con el mismo formato que {@code ErrorResponseDto}.
 * Se usa en filtros de seguridad y en el entry point / access denied handler, donde no
 * interviene el GlobalExceptionHandler, para que TODAS las respuestas de error tengan la
 * misma forma {timestamp, status, error, message, path, requestId, validationErrors}.
 *
 * La serialización la realiza Jackson, que escapa correctamente los valores (incluidos los
 * derivados del request como {@code path} o {@code requestId}). Nunca se concatenan strings
 * a mano, por lo que no hay riesgo de inyección (CWE-79).
 */
public final class JsonErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String error,
                             String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponseDto body = ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(nullToEmpty(error))
                .message(nullToEmpty(message))
                .path(nullToEmpty(path))
                .requestId(nullToEmpty(MDC.get(Logs.REQUEST_ID)))
                .validationErrors(null)
                .build();

        PrintWriter writer = response.getWriter();
        MAPPER.writeValue(writer, body);
        writer.flush();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
