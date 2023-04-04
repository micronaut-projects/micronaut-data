CREATE TABLE "USR"
(	"ID" NUMBER(*,0) NOT NULL ENABLE,
     "NAME" VARCHAR2(20 BYTE) NOT NULL ENABLE,
     "YM" INTERVAL YEAR (2) TO MONTH,
     "DS" INTERVAL DAY (2) TO SECOND (6),
     "BD" BINARY_DOUBLE,
     "FDATE" DATE,
     "MEMO" BLOB,
    /* "TSTZ" TIMESTAMP (6) WITH TIME ZONE,*/
     "TS" TIMESTAMP (6),
     CONSTRAINT "USR_PK" PRIMARY KEY ("ID")
);

CREATE OR REPLACE FORCE NONEDITIONABLE JSON RELATIONAL DUALITY VIEW "USR_VIEW"  AS
SELECT JSON{'name'  : u.name,
            'usrId': u.id,
            'ym': u.ym,
            'ds': u.ds,
            'bd': u.bd,
            'date': u.fdate,
            'ts': u.ts,
            /*'tstz': u.tstz,*/
            'memo': u.memo
            } FROM usr u WITH INSERT UPDATE DELETE;
