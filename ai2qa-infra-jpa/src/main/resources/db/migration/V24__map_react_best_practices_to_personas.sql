-- Map react-best-practices skill to all 4 personas
-- PERFORMANCE_HAWK gets it as priority 1 (primary skill)
-- Other personas get it appended at lowest priority

-- PERFORMANCE_HAWK: react-best-practices (priority 1), webapp-testing (priority 2)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000004","skillName":"react-best-practices","priority":1},{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":2}]',
    updated_at = NOW()
WHERE name = 'PERFORMANCE_HAWK';

-- STANDARD: webapp-testing (priority 1), react-best-practices (priority 2)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":1},{"skillId":"b0000000-0000-0000-0000-000000000004","skillName":"react-best-practices","priority":2}]',
    updated_at = NOW()
WHERE name = 'STANDARD';

-- CHAOS: web-fuzzing (priority 1), webapp-testing (priority 2), react-best-practices (priority 3)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000002","skillName":"web-fuzzing","priority":1},{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":2},{"skillId":"b0000000-0000-0000-0000-000000000004","skillName":"react-best-practices","priority":3}]',
    updated_at = NOW()
WHERE name = 'CHAOS';

-- HACKER: security-scanning (priority 1), web-fuzzing (priority 2), webapp-testing (priority 3), react-best-practices (priority 4)
UPDATE persona_definition
SET skills = '[{"skillId":"b0000000-0000-0000-0000-000000000003","skillName":"security-scanning","priority":1},{"skillId":"b0000000-0000-0000-0000-000000000002","skillName":"web-fuzzing","priority":2},{"skillId":"b0000000-0000-0000-0000-000000000001","skillName":"webapp-testing","priority":3},{"skillId":"b0000000-0000-0000-0000-000000000004","skillName":"react-best-practices","priority":4}]',
    updated_at = NOW()
WHERE name = 'HACKER';
