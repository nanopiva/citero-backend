package com.nanopiva.citero.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.nanopiva.citero.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024; // 2 MB

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder:citero}")
    private String baseFolder;

    @Override
    public String upload(MultipartFile file, String folder, ImageType type) {
        validate(file);

        String publicId = UUID.randomUUID().toString();
        String fullFolder = baseFolder + "/" + folder;

        try {
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", fullFolder,
                    "public_id", publicId,
                    "resource_type", "image",
                    "overwrite", false,
                    "transformation", new Transformation()
                            .crop("limit")
                            .width(type.getMaxWidth())
                            .height(type.getMaxHeight())
            );

            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);
            Object url = result.get("secure_url");

            if (url == null) {
                throw new BadRequestException("No se pudo obtener la URL de la imagen subida.");
            }
            return url.toString();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo enviado.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error al subir la imagen a Cloudinary.", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío.");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Formato no permitido. Usá JPG, PNG o WEBP.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("La imagen supera el máximo permitido de 2 MB.");
        }
    }
}
