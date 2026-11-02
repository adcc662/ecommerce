-- Incremented to revoke every token issued to a user ("log out everywhere").
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
