CREATE TABLE IF NOT EXISTS `JSON_SCHEMA_DEPENDENCY` (
  `VERSION_NUM` BIGINT NOT NULL,
  `DEPENDS_ON_SCHEMA_ID` BIGINT NOT NULL,
  `DEPENDS_ON_SEM_VER` VARCHAR(250) CHARACTER SET ascii COLLATE ascii_general_ci DEFAULT NULL,
  UNIQUE (`VERSION_NUM`, `DEPENDS_ON_SCHEMA_ID`, `DEPENDS_ON_SEM_VER`),
  CONSTRAINT FOREIGN KEY (`VERSION_NUM`) REFERENCES `JSON_SCHEMA_VERSION` (`VERSION_NUM`) ON DELETE CASCADE,
  CONSTRAINT FOREIGN KEY (`DEPENDS_ON_SCHEMA_ID`, `DEPENDS_ON_SEM_VER`) REFERENCES `JSON_SCHEMA_VERSION` (`SCHEMA_ID`, `SEMANTIC_VERSION`) ON DELETE RESTRICT
)
