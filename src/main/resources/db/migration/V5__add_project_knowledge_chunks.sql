CREATE TABLE project_knowledge_chunks (
    id CHAR(36) PRIMARY KEY,
    document_id CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    chunk_index INT NOT NULL,
    content LONGTEXT NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_knowledge_chunks_document
        FOREIGN KEY (document_id) REFERENCES project_knowledge_documents (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chunks_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uq_knowledge_chunks_document_index
        UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_knowledge_chunks_project
    ON project_knowledge_chunks (project_id, document_id);
