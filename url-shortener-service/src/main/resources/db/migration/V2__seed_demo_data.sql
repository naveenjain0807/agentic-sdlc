-- =====================================================================
-- V2 :: Seed data
--
-- Flyway runs this once. MERGE ... KEY (short_code) makes it idempotent
-- even if replayed manually: existing rows are updated in place instead
-- of raising a unique-constraint violation.
--
-- url_hash is SHA-256(original_url) and must match the value the
-- application computes, otherwise de-duplication would create a second
-- row for the same URL.
-- =====================================================================

MERGE INTO short_url (short_code, original_url, url_hash, created_by, created_at, updated_at, active, total_clicks)
KEY (short_code)
VALUES (
    'welcome',
    'https://spring.io/projects/spring-boot',
    '696acd66ac0aaf0728d4dbbda743ccba93b53f7236002d1eb8411a7ab39feda9',
    'seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    0
);

MERGE INTO short_url (short_code, original_url, url_hash, created_by, created_at, updated_at, active, total_clicks)
KEY (short_code)
VALUES (
    'docs',
    'https://docs.spring.io/spring-boot/index.html',
    'f023c00837702716f1a66a982c51c1b846959d4020c910fedc5f265ee795575e',
    'seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    0
);

MERGE INTO short_url (short_code, original_url, url_hash, created_by, created_at, updated_at, active, total_clicks)
KEY (short_code)
VALUES (
    'h2db',
    'https://www.h2database.com/html/main.html',
    'dd26b63c1e23d90caa8d602210f3e0eb3f33a829df3fa99cbd643131e4fec59b',
    'seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    0
);
