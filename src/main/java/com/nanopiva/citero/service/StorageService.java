package com.nanopiva.citero.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstracción de almacenamiento de archivos. Permite cambiar de proveedor
 * (Cloudinary, S3, disco local, etc.) sin tocar los servicios de negocio.
 */
public interface StorageService {

    /**
     * Sube una imagen y devuelve su URL pública (https).
     *
     * @param file   archivo a subir
     * @param folder carpeta destino dentro del proveedor
     * @param type   tipo de imagen (define el tamaño máximo)
     * @return URL pública de la imagen subida
     */
    String upload(MultipartFile file, String folder, ImageType type);
}
