CREATE TABLE persona_definition (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    system_prompt TEXT NOT NULL,
    skills JSONB DEFAULT '[]',
    source VARCHAR(50) NOT NULL DEFAULT 'BUILTIN',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_persona_name ON persona_definition(name);
CREATE INDEX idx_persona_active ON persona_definition(active);
