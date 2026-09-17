-- V4: la vinculación de profesionales pasó a ser por email (contact_email), así que
-- el token de invitación dejó de usarse y se eliminan sus columnas.

ALTER TABLE staff DROP COLUMN invitation_token;
ALTER TABLE staff DROP COLUMN invitation_expires_at;
