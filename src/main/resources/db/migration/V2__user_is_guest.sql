-- V2: marca las cuentas creadas automáticamente al reservar como invitado.
-- Una cuenta de invitado no tiene contraseña real y su email puede reclamarse al registrarse.

ALTER TABLE users ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;
