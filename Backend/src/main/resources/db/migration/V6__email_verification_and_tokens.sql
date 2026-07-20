-- Les comptes déjà existants sont considérés comme vérifiés (aucun email de
-- confirmation ne peut leur être renvoyé rétroactivement) ; seules les
-- nouvelles inscriptions passeront par email_verified = false.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE verification_tokens (
    token_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    user_id INT NOT NULL,
    project_id INT,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_token_project FOREIGN KEY (project_id) REFERENCES project(project_id) ON DELETE CASCADE
);

CREATE INDEX idx_verification_tokens_token ON verification_tokens(token);
