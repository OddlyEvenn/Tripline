-- =====================================================================
--  TRIPLINE DATABASE INITIALIZATION SCRIPT
--  Run once on a fresh NeonDB database.
-- =====================================================================

-- Enable PostGIS (Required for Station location data)
CREATE EXTENSION IF NOT EXISTS postgis;
