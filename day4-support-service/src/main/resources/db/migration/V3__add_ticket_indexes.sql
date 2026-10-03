-- A separate migration on purpose: schemas evolve, and Flyway applies only what a database hasn't seen yet.
--
-- PostgreSQL does NOT create indexes for foreign-key columns automatically. Without them every
-- "tickets of customer X" query and every customer delete (FK check) scans the whole tickets table.

-- Serves: WHERE customer_id = ?                    (leftmost column, also used by the FK check)
--         WHERE customer_id = ? AND status = ?     (a customer's open tickets)
CREATE INDEX ix_tickets_customer_status ON tickets (customer_id, status);

-- Serves the support queue: WHERE status = ? ORDER BY created_at
CREATE INDEX ix_tickets_status_created ON tickets (status, created_at);

-- Serves: history of one ticket, in order
CREATE INDEX ix_status_changes_ticket ON ticket_status_changes (ticket_id, changed_at);
