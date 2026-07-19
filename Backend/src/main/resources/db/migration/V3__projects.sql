CREATE TABLE project (
    project_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    owner_id INT NOT NULL,
    CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE TABLE project_members (
    project_id INT NOT NULL,
    user_id INT NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES project(project_id) ON DELETE CASCADE,
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
