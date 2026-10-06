package com.nanopiva.citero.util;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Escribe respuestas de error JSON con el mismo formato que {@code ErrorResponseDto}.
 * Se usa en filtros de seguridad y en el entry point / access denied handler, donde no
 * interviene el GlobalExceptionHandler, para que TODAS las respuestas de error tengan la
 * misma forma {timestamp, status, error, message, path, validationErrors}.
 */
public final class JsonErrorWriter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String error,
                             String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String requestId = MDC.get(Logs.REQUEST_ID);
        response.getWriter().write("{"
                + "\"timestamp\":\"" + TIMESTAMP.format(LocalDateTime.now()) + "\","
                + "\"status\":" + status + ","
                + "\"error\":\"" + escape(error) + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\"" + escape(path) + "\","
                + "\"requestId\":\"" + escape(requestId) + "\","
                + "\"validationErrors\":null"
                + "}");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
