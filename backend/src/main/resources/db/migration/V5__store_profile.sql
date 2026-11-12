-- What a store tells buyers about itself, and its defaults for new listings.
ALTER TABLE stores
    ADD COLUMN barangay             VARCHAR(80),
    -- Local (Manila) times a new listing's pickup window starts with. The owner can still change them per listing.
    ADD COLUMN default_pickup_start TIME,
    ADD COLUMN default_pickup_end   TIME,
    ADD CONSTRAINT stores_default_pickup_order
        CHECK (default_pickup_start IS NULL OR default_pickup_end IS NULL OR default_pickup_start < default_pickup_end);

-- Opening hours, one row per day the store is open. No row for a day means closed that day.
CREATE TABLE store_hours (
    store_id UUID       NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    weekday  VARCHAR(9) NOT NULL
        CHECK (weekday IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    opens    TIME       NOT NULL,
    closes   TIME       NOT NULL,
    PRIMARY KEY (store_id, weekday),
    CONSTRAINT store_hours_order CHECK (opens < closes)
);
