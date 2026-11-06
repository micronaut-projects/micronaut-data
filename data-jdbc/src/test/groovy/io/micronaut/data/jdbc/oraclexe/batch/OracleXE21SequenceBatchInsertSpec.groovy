package io.micronaut.data.jdbc.oraclexe.batch

import io.micronaut.data.jdbc.oraclexe.OracleXE21TestPropertyProvider

/**
 * The batch inserts against Oracle XE 21: the driver, not the server, returns the generated keys of a batch.
 */
class OracleXE21SequenceBatchInsertSpec extends OracleSequenceBatchInsertSpec implements OracleXE21TestPropertyProvider {
}
