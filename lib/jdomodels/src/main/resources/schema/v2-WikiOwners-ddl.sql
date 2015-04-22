CREATE TABLE IF NOT EXISTS `V2_WIKI_OWNERS` (
	`OWNER_ID` bigint(20)  NOT NULL,
	`OWNER_OBJECT_TYPE` ENUM('ENTITY', 'EVALUATION', 'ACCESS_REQUIREMENT') NOT NULL,
	`ROOT_WIKI_ID` bigint(20) NOT NULL,
	PRIMARY KEY (`OWNER_ID`, `OWNER_OBJECT_TYPE`),
	UNIQUE INDEX (`ROOT_WIKI_ID`),
	CONSTRAINT `V2_WIKI_OWNER_FK` FOREIGN KEY (`ROOT_WIKI_ID`) REFERENCES `V2_WIKI_PAGE` (`ID`) ON DELETE CASCADE
)
