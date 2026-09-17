-- V3: invitación de staff con token. El link del email incluye el token y vincula
-- el perfil del profesional a la cuenta que se registra.

ALTER TABLE staff ADD COLUMN invitation_token VARCHAR(64);
ALTER TABLE staff ADD COLUMN invitation_expires_at TIMESTAMP;
