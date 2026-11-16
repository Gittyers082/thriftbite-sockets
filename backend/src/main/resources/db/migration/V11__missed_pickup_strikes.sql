-- Missing pickups too often pauses reserving (see PickupStrikes). An admin can excuse a
-- miss that wasn't the buyer's fault, for example when the store was closed.
ALTER TABLE reservations ADD COLUMN miss_excused BOOLEAN NOT NULL DEFAULT false;
