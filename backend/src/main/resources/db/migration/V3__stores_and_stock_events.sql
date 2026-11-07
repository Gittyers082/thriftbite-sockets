-- Step 3 of the v2 roadmap: stores as their own thing, and a history of stock changes.
--
-- Until now an OWNER account *was* the store: no name of its own, no address,
-- no way to add staff. Analytics also couldn't answer "how much did I list?"
-- or "when did it sell out?", because only the current quantity was kept.

-- 1. Stores ---------------------------------------------------------------

CREATE TABLE stores (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug           VARCHAR(80)  NOT NULL UNIQUE,      -- for URLs: /stores/sunrise-bakery
    name           VARCHAR(80)  NOT NULL,
    address        VARCHAR(200),
    landmark       VARCHAR(200),                      -- "green awning, two doors up from the jeepney stop"
    latitude       NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
    longitude      NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
    gcash_accepted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0
);

-- Who works at a store. One store per account keeps "which store is this request for?" simple.
CREATE TABLE store_members (
    store_id   UUID        NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'STAFF')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (store_id, user_id)
);

-- Every existing owner account becomes a store named after it. Usernames are unique, so slugs are too.
INSERT INTO stores (slug, name)
SELECT lower(username), username FROM users WHERE role = 'OWNER';

INSERT INTO store_members (store_id, user_id, role)
SELECT s.id, u.id, 'OWNER'
  FROM stores s JOIN users u ON lower(u.username) = s.slug
 WHERE u.role = 'OWNER';

-- 2. Items and reservations belong to a store -----------------------------

ALTER TABLE items ADD COLUMN store_id UUID REFERENCES stores (id);
UPDATE items i SET store_id = m.store_id FROM store_members m WHERE m.user_id = i.owner_id;
ALTER TABLE items ALTER COLUMN store_id SET NOT NULL;
CREATE INDEX items_store_idx ON items (store_id, status);
-- items.owner_id stays: it records who listed the food (an owner or, later, staff).

ALTER TABLE reservations ADD COLUMN store_id UUID REFERENCES stores (id);
UPDATE reservations r SET store_id = m.store_id FROM store_members m WHERE m.user_id = r.owner_id;
ALTER TABLE reservations ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE reservations DROP COLUMN owner_id;         -- the store replaces it (its index goes with it)
CREATE INDEX reservations_store_status_idx ON reservations (store_id, status, pickup_deadline);
CREATE INDEX reservations_store_completed_idx ON reservations (store_id, completed_at) WHERE status = 'COMPLETED';

-- When a reservation ended, for every way it can end (completed_at already exists).
ALTER TABLE reservations ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE reservations ADD COLUMN expired_at   TIMESTAMPTZ;

-- The usual price at the time of sale, so "discount given" stays right after the owner edits the item.
ALTER TABLE reservation_items ADD COLUMN original_unit_price NUMERIC(10,2);
UPDATE reservation_items ri SET original_unit_price = i.original_price FROM items i WHERE i.id = ri.item_id;
UPDATE reservation_items SET original_unit_price = unit_price WHERE original_unit_price < unit_price;
ALTER TABLE reservation_items ALTER COLUMN original_unit_price SET NOT NULL;
ALTER TABLE reservation_items ADD CONSTRAINT reservation_items_original_price_check
    CHECK (original_unit_price >= unit_price);

-- 3. Stock history ----------------------------------------------------------
-- One row per change to an item's stock, written in the same transaction as the change.
-- Sell-through ("sold 31 of 42"), leftovers and sell-out times are computed from this.

CREATE TABLE item_events (
    id             BIGSERIAL   PRIMARY KEY,
    item_id        UUID        NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    store_id       UUID        NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    type           VARCHAR(20) NOT NULL
                   CHECK (type IN ('LISTED', 'ADJUSTED', 'RESERVED', 'RELEASED', 'SOLD_OUT', 'EXPIRED', 'REMOVED')),
    qty_delta      INTEGER     NOT NULL,                       -- change in stock (+ or -)
    qty_after      INTEGER     NOT NULL CHECK (qty_after >= 0), -- stock right after the change
    -- Checkout records RESERVED before it inserts the reservation, so this check waits until commit.
    reservation_id UUID        REFERENCES reservations (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
    actor_id       UUID        REFERENCES users (id) ON DELETE SET NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX item_events_store_type_time_idx ON item_events (store_id, type, occurred_at);
CREATE INDEX item_events_item_time_idx ON item_events (item_id, occurred_at);

-- Existing items get a LISTED event with what they started with: what's left plus what was reserved.
INSERT INTO item_events (item_id, store_id, type, qty_delta, qty_after, actor_id, occurred_at)
SELECT i.id, i.store_id, 'LISTED', i.quantity + COALESCE(taken.qty, 0), i.quantity + COALESCE(taken.qty, 0),
       i.owner_id, i.created_at
  FROM items i
  LEFT JOIN (SELECT ri.item_id, SUM(ri.quantity) AS qty
               FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id
              WHERE r.status IN ('RESERVED', 'COMPLETED')
              GROUP BY ri.item_id) taken ON taken.item_id = i.id;
