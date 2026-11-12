-- Following a store, and the alerts that come from it.

CREATE TABLE store_follows (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    store_id   UUID        NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    alerts_on  BOOLEAN     NOT NULL DEFAULT TRUE,     -- following without alerts keeps the store on your list, quietly
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, store_id)
);
CREATE INDEX store_follows_store_idx ON store_follows (store_id);

-- A buyer's alerts. The text is written when the thing happens, so an alert still reads
-- right after the store renames itself or the listing is gone.
CREATE TABLE alerts (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind           VARCHAR(20)  NOT NULL CHECK (kind IN ('DEAL', 'PICKUP', 'PICKED_UP', 'MISSED')),
    store_id       UUID         REFERENCES stores (id) ON DELETE CASCADE,
    reservation_id UUID         REFERENCES reservations (id) ON DELETE CASCADE,
    day            DATE,                               -- DEAL only: the day the store posted (Manila)
    title          VARCHAR(200) NOT NULL,
    body           VARCHAR(500) NOT NULL,
    link           VARCHAR(200) NOT NULL,              -- a page in the app, e.g. /stores/sunrise-bakery
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at        TIMESTAMPTZ
);
CREATE INDEX alerts_user_idx ON alerts (user_id, created_at DESC);
-- A store posting five listings in an afternoon is one alert that grows, not five alerts.
CREATE UNIQUE INDEX alerts_one_deal_alert_per_store_per_day ON alerts (user_id, store_id, day) WHERE kind = 'DEAL';
