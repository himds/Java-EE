CREATE TABLE IF NOT EXISTS communes (
    code_insee VARCHAR(10) PRIMARY KEY,
    nom_commune VARCHAR(255) NOT NULL,
    latitude DOUBLE,
    longitude DOUBLE,
    departement VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS prelevements (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code_insee VARCHAR(10) NOT NULL,
    dateprel DATE,
    conclusionprel VARCHAR(255),
    plvconformitebacterio CHAR(1),
    plvconformitechimique CHAR(1),
    plvconformitereferencebact CHAR(1),
    plvconformitereferencechim CHAR(1),
    CONSTRAINT fk_prelevements_communes
      FOREIGN KEY (code_insee) REFERENCES communes(code_insee)
      ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS resultats_analyses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    prelevement_id INT NOT NULL,
    parametre VARCHAR(255),
    valeur_mesuree DOUBLE,
    limite_legale DOUBLE,
    CONSTRAINT fk_resultats_prelevements
      FOREIGN KEY (prelevement_id) REFERENCES prelevements(id)
      ON DELETE CASCADE
);

CREATE INDEX idx_name ON communes(nom_commune);
CREATE INDEX idx_prelevements_commune_date ON prelevements(code_insee, dateprel);
