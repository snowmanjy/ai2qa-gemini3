CREATE TABLE skill (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    instructions TEXT NOT NULL,
    patterns JSONB DEFAULT '[]',
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    source_url VARCHAR(2048),
    source_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_skill_status ON skill(status);
CREATE INDEX idx_skill_category ON skill(category);
CREATE INDEX idx_skill_name ON skill(name);
