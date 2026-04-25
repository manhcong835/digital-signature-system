-- PAdES support.
-- signatures: snapshot of the cert used at signing time (signing_cert_pem must
-- be independent of the cert currently on the key, so historical signatures
-- remain verifiable even if the key cert is reissued), path to the embedded
-- PAdES PDF, and the visible widget position.
-- documents: pointer to the most recent PAdES PDF, used as the input for the
-- next signature (PAdES requires incremental updates to preserve prior signers).
ALTER TABLE signatures
    ADD COLUMN signing_cert_pem TEXT NULL,
    ADD COLUMN pades_pdf_path VARCHAR(500) NULL,
    ADD COLUMN signature_page INT NULL,
    ADD COLUMN sig_rect_x DOUBLE NULL,
    ADD COLUMN sig_rect_y DOUBLE NULL,
    ADD COLUMN sig_rect_w DOUBLE NULL,
    ADD COLUMN sig_rect_h DOUBLE NULL;

ALTER TABLE documents
    ADD COLUMN current_pades_pdf_path VARCHAR(500) NULL;
