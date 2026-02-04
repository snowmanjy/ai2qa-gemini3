-- Map curated skills to the 3 core personas
-- Skills are referenced by deterministic UUIDs from V21

-- STANDARD: webapp-testing (priority 1)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":1}]',
    updated_at = NOW()
WHERE name = 'STANDARD';

-- CHAOS: web-fuzzing (priority 1), webapp-testing (priority 2)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000002","skillName":"web-fuzzing","priority":1},{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":2}]',
    updated_at = NOW()
WHERE name = 'CHAOS';

-- HACKER: security-scanning (priority 1), web-fuzzing (priority 2), webapp-testing (priority 3)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000003","skillName":"security-scanning","priority":1},{"skillId":"b0000000-0000-0000-0000-000000000002","skillName":"web-fuzzing","priority":2},{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":3}]',
    updated_at = NOW()
WHERE name = 'HACKER';
