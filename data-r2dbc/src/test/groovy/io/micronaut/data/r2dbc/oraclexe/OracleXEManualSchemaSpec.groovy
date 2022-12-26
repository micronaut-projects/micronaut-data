package io.micronaut.data.r2dbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.AbstractManualSchemaSpec
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PatientRepository
import spock.lang.IgnoreIf

@IgnoreIf({ jvm.isJava8() })
class OracleXEManualSchemaSpec extends AbstractManualSchemaSpec implements OracleXETestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(OracleXEPatientRepository)
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE SEQUENCE \"PATIENT_SEQ\" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE",
                "CREATE TABLE \"PATIENT\" (\"NAME\" VARCHAR(255), \"ID\" NUMBER(19) NOT NULL PRIMARY KEY, \"HISTORY\" VARCHAR(1000), \"DOCTOR_NOTES\" VARCHAR(255))")
    }

    @Override
    List<String> dropStatements() {
        return Arrays.asList("DROP TABLE \"PATIENT\" PURGE", "DROP SEQUENCE \"PATIENT_SEQ\"")
    }

    @Override
    String insertStatement() {
        return "INSERT INTO \"PATIENT\" (\"NAME\", \"HISTORY\", \"DOCTOR_NOTES\", \"ID\") VALUES (?, ?, ?, \"PATIENT_SEQ\".nextval)";
    }
}
