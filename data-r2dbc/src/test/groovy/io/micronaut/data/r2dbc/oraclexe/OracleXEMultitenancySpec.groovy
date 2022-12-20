package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.r2dbc.AbstractR2dbcMultitenancySpec
import io.micronaut.data.r2dbc.CleanupTestResourcesDatabaseTestPropertyProvider
import spock.lang.IgnoreIf

@IgnoreIf({ !jvm.isJava11Compatible() })
class OracleXEMultitenancySpec extends AbstractR2dbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

    @Override
    boolean supportsSchemaMultitenancy() {
        return false
    }

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: OracleXEBookRepository.name]
    }

    @Override
    Map<String, String> getDataSourceProperties() {
        return [
                'db-type'        : 'oracle',
                'schema-generate': 'CREATE_DROP',
                'dialect'        : 'ORACLE'
        ]
    }
}
