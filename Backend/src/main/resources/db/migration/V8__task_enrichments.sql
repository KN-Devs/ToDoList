ALTER TABLE task ADD COLUMN due_date DATE;

CREATE TABLE task_comments (
    comment_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id INT NOT NULL,
    author_id INT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_comment_task FOREIGN KEY (task_id) REFERENCES task(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Le contenu du fichier est stocké directement en base (bytea) plutôt que sur
-- le disque du service : le système de fichiers d'un service Render gratuit
-- est éphémère et ne survit pas à un redéploiement, contrairement à Postgres.
CREATE TABLE task_attachments (
    attachment_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id INT NOT NULL,
    uploaded_by_id INT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attachment_task FOREIGN KEY (task_id) REFERENCES task(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploaded_by_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_task_comments_task ON task_comments(task_id);
CREATE INDEX idx_task_attachments_task ON task_attachments(task_id);
