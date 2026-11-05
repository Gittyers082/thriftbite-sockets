-- ThriftBite v2: initial schema
-- Money is NUMERIC (never floating point), times are TIMESTAMPTZ (stored in UTC),
-- and every table uses UUID keys so usernames can change without touching other tables.

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,                 -- BCrypt hash, never the raw password
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'OWNER', 'CONSUMER')),
    approved      BOOLEAN      NOT NULL DEFAULT FALSE,   -- only owners need admin approval
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE items (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID          NOT NULL REFERENCES users (id),
    name            VARCHAR(120)  NOT NULL,
    description     VARCHAR(500),
    price           NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    original_price  NUMERIC(10,2) NOT NULL,
    quantity        INTEGER       NOT NULL CHECK (quantity >= 0),
    expires_at      TIMESTAMPTZ   NOT NULL,
    pickup_location VARCHAR(200)  NOT NULL,
    pickup_start    TIMESTAMPTZ   NOT NULL,
    pickup_end      TIMESTAMPTZ   NOT NULL,
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('AVAILABLE', 'SOLD_OUT', 'EXPIRED', 'REMOVED')),
    version         BIGINT        NOT NULL DEFAULT 0,    -- optimistic locking for owner edits
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT items_discount_check CHECK (price <= original_price),
    CONSTRAINT items_pickup_window_check CHECK (pickup_start < pickup_end)
);

CREATE INDEX items_browse_idx ON items (status, expires_at);
CREATE INDEX items_owner_idx  ON items (owner_id);

CREATE TABLE cart_items (
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    item_id  UUID        NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    quantity INTEGER     NOT NULL CHECK (quantity > 0),
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_id)
);

-- One reservation = one buyer picking up from one owner.
-- Checkout splits a cart into one reservation per owner.
CREATE TABLE reservations (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_id        UUID          NOT NULL REFERENCES users (id),
    owner_id        UUID          NOT NULL REFERENCES users (id),
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('RESERVED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    claim_code      VARCHAR(6)    NOT NULL,              -- shown by the buyer, checked by the owner at pickup
    pickup_deadline TIMESTAMPTZ   NOT NULL,
    total           NUMERIC(10,2) NOT NULL CHECK (total >= 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX reservations_buyer_idx ON reservations (buyer_id, created_at DESC);
CREATE INDEX reservations_owner_idx ON reservations (owner_id, status);

-- Name and price are copied at checkout, so later edits to an item
-- don't change what the buyer already paid for.
CREATE TABLE reservation_items (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID          NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    item_id        UUID          NOT NULL REFERENCES items (id),
    item_name      VARCHAR(120)  NOT NULL,
    unit_price     NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
    quantity       INTEGER       NOT NULL CHECK (quantity > 0)
);

CREATE INDEX reservation_items_reservation_idx ON reservation_items (reservation_id);
