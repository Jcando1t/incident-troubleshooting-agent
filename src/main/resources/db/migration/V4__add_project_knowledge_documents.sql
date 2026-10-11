CREATE TABLE project_knowledge_documents (
    id CHAR(36) PRIMARY KEY,
    project_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_knowledge_documents_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX idx_knowledge_documents_project_updated_at
    ON project_knowledge_documents (project_id, updated_at);
