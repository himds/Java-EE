-- =============================================================================
-- 1️⃣ COMMUNES — 城市/地区表
-- 主键: inseecommuneprinc (code_insee)
-- =============================================================================
CREATE TABLE IF NOT EXISTS communes (
    code_insee   VARCHAR(10)  PRIMARY KEY COMMENT '城市ID (INSEE)',
    nom_commune  VARCHAR(255) NOT NULL   COMMENT '城市名称',
    departement  VARCHAR(10)             COMMENT '部门编号 (cddept)'
    );

CREATE INDEX idx_communes_nom ON communes(nom_commune);
CREATE INDEX idx_communes_departement ON communes(departement);


-- =============================================================================
-- 2️⃣ PRÉLÈVEMENTS — 水样检测记录
-- 关系: commune (1) —— (N) prélèvement
-- 主键: id ; 业务键: referenceprel (检测编号，唯一)
-- =============================================================================
CREATE TABLE IF NOT EXISTS prelevements (
                                            id               INT          AUTO_INCREMENT PRIMARY KEY,
                                            code_insee       VARCHAR(10)  NOT NULL COMMENT '城市ID → communes.code_insee',
    referenceprel    VARCHAR(30)   NOT NULL COMMENT '检测编号',
    dateprel         DATE                  COMMENT '检测日期',
    heureprel        VARCHAR(10)          COMMENT '检测时间',
    conclusionprel   VARCHAR(255)         COMMENT '检测结论',
    plvconformitebacterio      VARCHAR(5)  COMMENT 'C/N bacterio',
    plvconformitechimique       VARCHAR(5)  COMMENT 'C/N chimique',
    plvconformitereferencebact VARCHAR(5)  COMMENT 'C/N ref bacterio',
    plvconformitereferencechim VARCHAR(5)  COMMENT 'C/N ref chimique',
    CONSTRAINT fk_prelevements_communes
    FOREIGN KEY (code_insee) REFERENCES communes(code_insee) ON DELETE CASCADE,
    CONSTRAINT uq_prelevements_referenceprel UNIQUE (referenceprel)
    );

CREATE INDEX idx_prelevements_commune_date ON prelevements(code_insee, dateprel);
CREATE INDEX idx_prelevements_reference ON prelevements(referenceprel);


-- =============================================================================
-- 3️⃣ PARAMÈTRES (resultats_analyses) — 每次检测的指标
-- 关系: prélèvement (1) —— (N) paramètres
-- =============================================================================
CREATE TABLE IF NOT EXISTS resultats_analyses (
                                                  id              INT           AUTO_INCREMENT PRIMARY KEY,
                                                  prelevement_id  INT           NOT NULL COMMENT '检测ID → prelevements.id',
                                                  parametre       VARCHAR(255)          COMMENT '指标名称 (libmajparametre)',
    valeur_mesuree  DOUBLE                COMMENT '测量值 (valtraduite)',
    limite_legale   DOUBLE                COMMENT '标准值 (limitequal)',
    CONSTRAINT fk_resultats_prelevements
    FOREIGN KEY (prelevement_id) REFERENCES prelevements(id) ON DELETE CASCADE
    );

CREATE INDEX idx_resultats_prelevement ON resultats_analyses(prelevement_id);