-- Sample data, loaded only when the dev profile adds classpath:db/dev-data to spring.flyway.locations.
-- Versioned 3.1 so it runs right after the schema it needs; keep future schema migrations at V4 and above.
-- Customers mirror the Day 2 seed data; tickets mirror the Day 1 CSV files.

INSERT INTO customers (name, email, tier, created_at, updated_at) VALUES
    ('Asha Rao',   'asha.rao@example.com',   'PREMIUM',  TIMESTAMP WITH TIME ZONE '2026-09-01 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-01 09:00:00+00'),
    ('Ben Carter', 'ben.carter@example.com', 'STANDARD', TIMESTAMP WITH TIME ZONE '2026-09-02 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-02 09:00:00+00'),
    ('Chen Wei',   'chen.wei@example.com',   'STANDARD', TIMESTAMP WITH TIME ZONE '2026-09-03 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-03 09:00:00+00'),
    ('Dana Lopez', 'dana.lopez@example.com', 'PREMIUM',  TIMESTAMP WITH TIME ZONE '2026-09-04 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-04 09:00:00+00'),
    ('Eve Kim',    'eve.kim@example.com',    'STANDARD', TIMESTAMP WITH TIME ZONE '2026-09-05 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-09-05 09:00:00+00');

-- Look customers up by email instead of hard-coding generated ids.
INSERT INTO tickets (customer_id, subject, status, priority, created_at, updated_at)
SELECT c.id, t.subject, t.status, t.priority, t.created_at, t.created_at
FROM (VALUES
        ('asha.rao@example.com',   'Refund status, please',   'OPEN',    'HIGH',   TIMESTAMP WITH TIME ZONE '2026-09-30 08:15:00+00'),
        ('ben.carter@example.com', 'Package arrived damaged', 'OPEN',    'HIGH',   TIMESTAMP WITH TIME ZONE '2026-09-30 08:22:00+00'),
        ('chen.wei@example.com',   'Update billing address',  'PENDING', 'NORMAL', TIMESTAMP WITH TIME ZONE '2026-09-30 08:41:00+00'),
        ('dana.lopez@example.com', 'Missing receipt',         'OPEN',    'LOW',    TIMESTAMP WITH TIME ZONE '2026-09-30 08:45:00+00'),
        ('asha.rao@example.com',   'Cannot sign in',          'OPEN',    'NORMAL', TIMESTAMP WITH TIME ZONE '2026-09-30 09:02:00+00'),
        ('ben.carter@example.com', 'Return label request',    'PENDING', 'NORMAL', TIMESTAMP WITH TIME ZONE '2026-09-30 09:11:00+00'),
        ('asha.rao@example.com',   'Duplicate charge',        'CLOSED',  'HIGH',   TIMESTAMP WITH TIME ZONE '2026-09-30 09:28:00+00')
     ) AS t (email, subject, status, priority, created_at)
JOIN customers c ON c.email = t.email
ORDER BY t.created_at;

-- Every ticket starts with one history row (created -> its current status).
INSERT INTO ticket_status_changes (ticket_id, from_status, to_status, changed_at)
SELECT id, NULL, status, created_at FROM tickets;
