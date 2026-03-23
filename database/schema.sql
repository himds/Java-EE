-- =============================================================================
-- 1️⃣ COMMUNES — table des communes
-- Clé primaire : code_insee
-- =============================================================================
CREATE TABLE IF NOT EXISTS communes (
    code_insee   VARCHAR(10)  PRIMARY KEY COMMENT 'Code INSEE',
    nom_commune  VARCHAR(255) NOT NULL   COMMENT 'Nom de la commune',
    departement  VARCHAR(10)             COMMENT 'Code département (cddept)'
    );

CREATE INDEX idx_communes_nom ON communes(nom_commune);
CREATE INDEX idx_communes_departement ON communes(departement);


-- =============================================================================
-- 2️⃣ PRÉLÈVEMENTS — prélèvements d'eau
-- Relation : commune (1) —— (N) prélèvement
-- Clé primaire : id ; clé métier : referenceprel (unique)
-- =============================================================================
CREATE TABLE IF NOT EXISTS prelevements (
                                            id               INT          AUTO_INCREMENT PRIMARY KEY,
                                            code_insee       VARCHAR(10)  NOT NULL COMMENT 'Code INSEE → communes.code_insee',
    referenceprel    VARCHAR(30)   NOT NULL COMMENT 'Référence du prélèvement',
    dateprel         DATE                  COMMENT 'Date du prélèvement',
    heureprel        VARCHAR(10)          COMMENT 'Heure du prélèvement',
    conclusionprel   VARCHAR(255)         COMMENT 'Conclusion du prélèvement',
    plvconformitebacterio      VARCHAR(5)  COMMENT 'C/N bactériologie',
    plvconformitechimique       VARCHAR(5)  COMMENT 'C/N chimique',
    plvconformitereferencebact VARCHAR(5)  COMMENT 'C/N référence bactériologie',
    plvconformitereferencechim VARCHAR(5)  COMMENT 'C/N référence chimique',
    CONSTRAINT fk_prelevements_communes
    FOREIGN KEY (code_insee) REFERENCES communes(code_insee) ON DELETE CASCADE,
    CONSTRAINT uq_prelevements_referenceprel UNIQUE (referenceprel)
    );

CREATE INDEX idx_prelevements_commune_date ON prelevements(code_insee, dateprel);
CREATE INDEX idx_prelevements_reference ON prelevements(referenceprel);


-- =============================================================================
-- 3️⃣ RÉSULTATS D'ANALYSE (resultats_analyses) — paramètres par prélèvement
-- Relation : prélèvement (1) —— (N) paramètres
-- =============================================================================
CREATE TABLE IF NOT EXISTS resultats_analyses (
                                                  id              INT           AUTO_INCREMENT PRIMARY KEY,
                                                  prelevement_id  INT           NOT NULL COMMENT 'ID prélèvement → prelevements.id',
                                                  parametre       VARCHAR(255)          COMMENT 'Nom du paramètre (libmajparametre)',
    valeur_mesuree  DOUBLE                COMMENT 'Valeur mesurée (valtraduite)',
    limite_legale   DOUBLE                COMMENT 'Limite légale (limitequal)',
    CONSTRAINT fk_resultats_prelevements
    FOREIGN KEY (prelevement_id) REFERENCES prelevements(id) ON DELETE CASCADE
    );

CREATE INDEX idx_resultats_prelevement ON resultats_analyses(prelevement_id);