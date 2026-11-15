-- Photos of food and of stores, and buyers' problem reports.

-- A photo is stored already shrunk, in two sizes (see PhotoProcessor). The file that was
-- uploaded is never kept, so nothing hidden inside it (location, camera details) is kept either.
CREATE TABLE photos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id    UUID        NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    uploaded_by UUID        REFERENCES users (id) ON DELETE SET NULL,
    sha256      CHAR(64)    NOT NULL,                -- of the uploaded file: the same file twice is stored once
    full_jpeg   BYTEA       NOT NULL,                -- longest side 960 px
    thumb_jpeg  BYTEA       NOT NULL,                -- longest side 480 px, for lists
    width       INT         NOT NULL,
    height      INT         NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (store_id, sha256)
);

-- A listing or a store points at its photo. Deleting the photo just leaves it without one.
ALTER TABLE items  ADD COLUMN photo_id UUID REFERENCES photos (id) ON DELETE SET NULL;
ALTER TABLE stores ADD COLUMN photo_id UUID REFERENCES photos (id) ON DELETE SET NULL;
CREATE INDEX items_photo_idx ON items (photo_id) WHERE photo_id IS NOT NULL;

-- "Report a problem": about one of the buyer's own pickups, or about a store.
CREATE TABLE reports (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    store_id       UUID          NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    reservation_id UUID          REFERENCES reservations (id) ON DELETE CASCADE,   -- null: about the store in general
    kind           VARCHAR(20)   NOT NULL CHECK (kind IN
                       ('STORE_CLOSED', 'NO_FOOD', 'NOT_AS_LISTED', 'UNSAFE_FOOD', 'OVERCHARGED', 'OTHER')),
    details        VARCHAR(1000),
    status         VARCHAR(10)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    reply          VARCHAR(500),                       -- what the admin told the buyer when closing it
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ
);
CREATE INDEX reports_reporter_idx ON reports (reporter_id, created_at DESC);
CREATE INDEX reports_open_idx ON reports (created_at) WHERE status = 'OPEN';
-- One report per pickup, and one open report at a time per buyer about a store in general.
CREATE UNIQUE INDEX reports_one_per_reservation ON reports (reservation_id) WHERE reservation_id IS NOT NULL;
CREATE UNIQUE INDEX reports_one_open_per_store ON reports (reporter_id, store_id)
    WHERE reservation_id IS NULL AND status = 'OPEN';
