CREATE TABLE IF NOT EXISTS incidents (
    id CHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    service VARCHAR(255) NOT NULL,
    environment VARCHAR(100) NOT NULL,
    severity VARCHAR(10),
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    triage_json JSON,
    evidence_json JSON,
    rca_json JSON,
    remediation_json JSON,
    audit_json JSON
);
