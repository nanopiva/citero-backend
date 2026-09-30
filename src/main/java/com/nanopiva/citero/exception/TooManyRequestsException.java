package com.nanopiva.citero.exception;

/** Límite de tasa superado; se mapea a HTTP 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
