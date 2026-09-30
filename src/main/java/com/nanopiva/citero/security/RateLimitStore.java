package com.nanopiva.citero.security;

import java.time.Duration;

/**
 * Almacén de contadores del rate limiting. La implementación en memoria sirve para una sola
 * instancia; con varias hay que usar un store compartido (p. ej. Redis) para que sea global.
 */
public interface RateLimitStore {

    boolean tryConsume(String key, int limit, Duration window);
}
