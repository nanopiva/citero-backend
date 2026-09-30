package com.nanopiva.citero.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.nanopiva.citero.exception.BadRequestException;
import com.nanopiva.citero.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifica la validación de MIME y tamaño de {@link CloudinaryStorageService} y el
 * camino de subida exitoso. El cliente de Cloudinary se reemplaza con {@code @MockitoBean}.
 */
class CloudinaryStorageServiceTest extends IntegrationTest {

    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;

    @MockitoBean
    private Cloudinary cloudinary;

    @Autowired
    private CloudinaryStorageService storageService;

    private MultipartFile file(String contentType, int size) {
        return new MockMultipartFile("file", "archivo", contentType, new byte[size]);
    }

    private MultipartFile fileWithBytes(String contentType, byte[] bytes) {
        return new MockMultipartFile("file", "archivo", contentType, bytes);
    }

    private static byte[] pngBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        return bytes;
    }

    private static byte[] webpBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'E';
        bytes[10] = 'B';
        bytes[11] = 'P';
        return bytes;
    }

    private static byte[] jpegBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    @Test
    void rechazaArchivoVacio() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(file("image/png", 0), "logos", ImageType.LOGO));

        assertEquals("El archivo está vacío.", ex.getMessage());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rechazaFormatoNoPermitido() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(file("application/pdf", 10), "logos", ImageType.LOGO));

        assertEquals("Formato no permitido. Usá JPG, PNG o WEBP.", ex.getMessage());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rechazaContentTypeNulo() {
        MultipartFile sinContentType = new MockMultipartFile("file", "archivo", null, new byte[10]);

        assertThrows(BadRequestException.class,
                () -> storageService.upload(sinContentType, "logos", ImageType.LOGO));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rechazaFirmaInvalidaAunqueElContentTypeDigaImagen() {
        byte[] noEsImagen = new byte[16];

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(fileWithBytes("image/png", noEsImagen), "logos", ImageType.LOGO));

        assertEquals("El archivo no es una imagen válida. Usá JPG, PNG o WEBP.", ex.getMessage());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rechazaFirmaQueNoCoincideConElContentType() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(fileWithBytes("image/png", jpegBytes(16)), "logos", ImageType.LOGO));

        assertEquals("El archivo no es una imagen válida. Usá JPG, PNG o WEBP.", ex.getMessage());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void rechazaArchivoMayorA2MB() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(file("image/jpeg", MAX_FILE_SIZE + 1), "logos", ImageType.LOGO));

        assertEquals("La imagen supera el máximo permitido de 2 MB.", ex.getMessage());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void aceptaContentTypeEnMayusculasYSube() throws Exception {
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap()))
                .thenReturn(Map.of("secure_url", "https://cdn.test/logo.png"));

        String url = storageService.upload(fileWithBytes("IMAGE/PNG", pngBytes(16)), "logos", ImageType.LOGO);

        assertEquals("https://cdn.test/logo.png", url);
        verify(uploader, times(1)).upload(any(), anyMap());
    }

    @Test
    void lanzaBadRequestCuandoCloudinaryNoDevuelveUrl() throws Exception {
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(fileWithBytes("image/webp", webpBytes(16)), "logos", ImageType.COVER));

        assertEquals("No se pudo obtener la URL de la imagen subida.", ex.getMessage());
    }
}
