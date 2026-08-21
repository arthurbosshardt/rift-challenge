-- Every challenge currently runs on the EUW Riot platform only (see riftchallenge.riot.platform).
-- This column surfaces that in the API/UI; it is fixed at creation and never updated.
ALTER TABLE challenge
    ADD COLUMN region VARCHAR(16) NOT NULL DEFAULT 'EUW';
