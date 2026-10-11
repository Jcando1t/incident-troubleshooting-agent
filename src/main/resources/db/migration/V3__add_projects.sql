CREATE TABLE projects (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

INSERT INTO projects (id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Legacy Project', UTC_TIMESTAMP(6));

ALTER TABLE incidents
    ADD COLUMN project_id CHAR(36) NULL;

UPDATE incidents
SET project_id = '00000000-0000-0000-0000-000000000001'
WHERE project_id IS NULL;

ALTER TABLE incidents
    MODIFY COLUMN project_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_incidents_project
        FOREIGN KEY (project_id) REFERENCES projects (id);

CREATE INDEX idx_incidents_project_created_at
    ON incidents (project_id, created_at);
