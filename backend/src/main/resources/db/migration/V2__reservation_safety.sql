-- Safety rules for reservations (step 1 of the v2 roadmap).

-- Optimistic locking: if the expiry job and a buyer's cancel (or an owner's
-- handover) touch the same reservation at once, the second write fails
-- instead of putting the food back on sale twice.
ALTER TABLE reservations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Claim-code guessing: after 5 wrong codes, code entry for this reservation
-- pauses for 15 minutes. 5 tries out of 1,000,000 codes makes guessing hopeless.
ALTER TABLE reservations ADD COLUMN failed_code_attempts INTEGER NOT NULL DEFAULT 0
    CHECK (failed_code_attempts >= 0);
ALTER TABLE reservations ADD COLUMN code_locked_until TIMESTAMPTZ;

-- Checkout counts a buyer's active reservations; this keeps that count cheap.
CREATE INDEX reservations_buyer_status_idx ON reservations (buyer_id, status);
