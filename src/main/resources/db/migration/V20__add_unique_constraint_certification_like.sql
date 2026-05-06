ALTER TABLE certification_like
    ADD CONSTRAINT UQ_cert_like UNIQUE (certification_id, user_id);
