-- Feedback is stored anonymously: the sender's email snapshot is dropped, and existing rows lose it.
ALTER TABLE feedback DROP COLUMN email;
