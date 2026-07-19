ALTER TABLE project_members DROP CONSTRAINT project_members_pkey;
ALTER TABLE project_members ADD COLUMN member_id INT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE project_members ADD COLUMN can_manage_tasks BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE project_members ADD PRIMARY KEY (member_id);
ALTER TABLE project_members ADD CONSTRAINT uq_project_member UNIQUE (project_id, user_id);

ALTER TABLE task ADD COLUMN project_id INT;

INSERT INTO project (nom, description, start_date, end_date, owner_id)
SELECT 'Tâches diverses', 'Projet créé automatiquement pour vos tâches existantes', CURRENT_DATE, CURRENT_DATE, u.user_id
FROM (SELECT DISTINCT user_id FROM task WHERE project_id IS NULL) u;

UPDATE task t
SET project_id = p.project_id
FROM project p
WHERE t.project_id IS NULL
  AND p.owner_id = t.user_id
  AND p.nom = 'Tâches diverses'
  AND p.description = 'Projet créé automatiquement pour vos tâches existantes';

ALTER TABLE task ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE task ADD CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project(project_id) ON DELETE CASCADE;
