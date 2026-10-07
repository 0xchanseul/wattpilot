-- Visitor feedback submitted from the in-app feedback dialog. The email is a snapshot of the sender's
-- account email, not a foreign key: demo accounts are deleted automatically and their feedback must outlive them.
CREATE TABLE feedback (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_message_length_valid CHECK (char_length(message) BETWEEN 1 AND 2000)
);
