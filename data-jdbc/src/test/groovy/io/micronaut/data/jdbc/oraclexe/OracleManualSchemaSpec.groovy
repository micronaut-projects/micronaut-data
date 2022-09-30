package io.micronaut.data.jdbc.oraclexe

import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.PatientRepository

class OracleManualSchemaSpec extends AbstractManualSchemaSpec {

    @Override
    Dialect dialect() {
        Dialect.ORACLE
    }

    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(OracleXEPatientRepository)
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE SEQUENCE \"PATIENT_SEQ\" MINVALUE 1 START WITH 1 NOCACHE NOCYCLE",
            "CREATE TABLE \"PATIENT\" (\"NAME\" VARCHAR(255), \"ID\" NUMBER(19) NOT NULL PRIMARY KEY, \"HISTORY\" VARCHAR(1000))")
    }
}
