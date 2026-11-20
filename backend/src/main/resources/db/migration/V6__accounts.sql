-- Accounts: recovery codes, logging out other devices, and deleting an account.

ALTER TABLE users
    -- Goes up when the password changes. A login token carries the version it was issued
    -- under, and a token from an older version is refused: that's "log out everywhere else".
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0,
    -- Set when a buyer deletes their account. The row stays, scrubbed of the username, so
    -- stores' past reservations still add up.
    ADD COLUMN deleted_at    TIMESTAMPTZ;

-- One-time codes for getting back in without a password. ThriftBite has no email or phone
-- number to send a reset link to, so these are the only way.
CREATE TABLE recovery_codes (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  CHAR(64)    NOT NULL,          -- SHA-256 of the code, never the code itself
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at    TIMESTAMPTZ                    -- each code works once
);
CREATE INDEX recovery_codes_user_idx ON recovery_codes (user_id);
