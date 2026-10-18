-- One row per distinct visitor per Europe/Oslo day. visitor_hash is a keyed hash of the day, client IP
-- and User-Agent, never the raw address, and it differs from day to day, so rows cannot be linked
-- across days. The total visit count is the number of rows.
CREATE TABLE daily_visitors (
    visit_date DATE NOT NULL,
    visitor_hash CHAR(64) NOT NULL,
    PRIMARY KEY (visit_date, visitor_hash)
);
