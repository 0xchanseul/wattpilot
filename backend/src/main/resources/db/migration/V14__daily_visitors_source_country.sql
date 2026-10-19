-- Where a visitor came from and which country they connected from, recorded with the first visit of
-- the day. Both stay NULL for rows written before this migration and when the value is unknown.
-- source: a normalized label such as 'linkedin', 'reddit' or 'direct'; an unrecognised referrer is
--         stored as its host name. Never a full URL.
-- country_code: ISO 3166-1 alpha-2. No finer location is stored.
ALTER TABLE daily_visitors
    ADD COLUMN source VARCHAR(100),
    ADD COLUMN country_code CHAR(2);
