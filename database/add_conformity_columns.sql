-- Add conformity columns for color algorithm (run only if prelevements was created without these columns)
-- MySQL: run one by one; ignore error if column already exists.
ALTER TABLE prelevements ADD COLUMN plvconformitebacterio VARCHAR(5) COMMENT 'C/N bacterio';
ALTER TABLE prelevements ADD COLUMN plvconformitechimique VARCHAR(5) COMMENT 'C/N chimique';
ALTER TABLE prelevements ADD COLUMN plvconformitereferencebact VARCHAR(5) COMMENT 'C/N ref bacterio';
ALTER TABLE prelevements ADD COLUMN plvconformitereferencechim VARCHAR(5) COMMENT 'C/N ref chimique';
