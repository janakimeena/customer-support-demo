-- The old support system's account table, the "DB" source of the Day 4 ingestion demo (dev data only).
-- In real life it would live in another system's database; here it shares ours to keep the setup to one
-- container. Note the legacy conventions the adapter has to translate: upper-case names, one-letter plan
-- codes (P = premium, S = standard), and a status column (A = active, C = closed; closed accounts are skipped).
-- Versioned 3.2 (not 4.x) so future schema migrations can still use V4 and up.

CREATE TABLE legacy_accounts (
    acct_no        VARCHAR(10)  PRIMARY KEY,
    acct_name      VARCHAR(120) NOT NULL,
    contact_email  VARCHAR(254),
    plan_code      CHAR(1)      NOT NULL,
    status         CHAR(1)      NOT NULL
);

INSERT INTO legacy_accounts (acct_no, acct_name, contact_email, plan_code, status) VALUES
    ('L-0001', 'CHEN WEI',          'chen.wei@example.com',  'S', 'A'),
    ('L-0002', 'EVE KIM',           'eve.kim@example.com',   'P', 'A'),
    ('L-0003', 'RIYA DAS',          'riya.das@example.com',  'P', 'A'),
    ('L-0004', 'SAM LEE',           'sam.lee@example.com',   'S', 'C'),
    ('L-0005', 'TOM BRANDT',        NULL,                    'S', 'A'),
    ('L-0006', 'UMA IYER',          'uma.iyer@example.com',  'X', 'A'),
    ('L-0007', 'MARY-JANE O''BRIEN', 'mj.obrien@example.com', 'S', 'A');
