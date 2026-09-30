package com.nanopiva.citero.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void tomaElValorDelProxyDeConfianzaConUnSalto() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertEquals("203.0.113.5",
                resolver.resolve(request("10.0.0.1", "203.0.113.5")));
    }

    @Test
    void ignoraValorFalsificadoAlInicioDeLaCadena() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        // El proxy de confianza agregó la IP real al final; el left-most es spoof del cliente.
        assertEquals("203.0.113.5",
                resolver.resolve(request("10.0.0.1", "1.2.3.4, 203.0.113.5")));
    }

    @Test
    void tomaElValorCorrectoConDosProxiesDeConfianza() {
        ClientIpResolver resolver = new ClientIpResolver(2);

        assertEquals("203.0.113.5",
                resolver.resolve(request("10.0.0.1", "1.2.3.4, 203.0.113.5, 10.0.0.9")));
    }

    @Test
    void sinHeaderUsaRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertEquals("192.0.2.10", resolver.resolve(request("192.0.2.10", null)));
    }

    @Test
    void conCeroProxiesIgnoraElHeader() {
        ClientIpResolver resolver = new ClientIpResolver(0);

        assertEquals("192.0.2.10",
                resolver.resolve(request("192.0.2.10", "1.2.3.4")));
    }

    @Test
    void conMenosSaltosQueLosEsperadosUsaElUnicoValorDisponible() {
        ClientIpResolver resolver = new ClientIpResolver(3);

        assertEquals("203.0.113.5",
                resolver.resolve(request("10.0.0.1", "203.0.113.5")));
    }
}
