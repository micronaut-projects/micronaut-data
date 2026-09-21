package io.micronaut.transaction.jdbc.oracle

import io.micronaut.transaction.exceptions.TransactionUsageException
import spock.lang.Specification

class OracleSessionlessTransactionPropagationOperationsSpec extends Specification {

    def "with propagation exposes a mutable sessionless transaction id state"() {
        given:
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def operations = new DefaultOracleSessionlessTransactionPropagationOperations(codec)
        def transactionId = codec.encode([1, 2, 3] as byte[])

        when:
        def captured = operations.withPropagation({
            assert operations.currentTransactionId().isEmpty()
            operations.setTransactionId(transactionId)
            def current = operations.currentTransactionId().orElseThrow()
            operations.clearTransactionId()
            assert operations.currentTransactionId().isEmpty()
            current
        })

        then:
        captured == transactionId
        operations.currentTransactionId().isEmpty()
    }

    def "with propagation can import an encoded sessionless transaction id"() {
        given:
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def operations = new DefaultOracleSessionlessTransactionPropagationOperations(codec)
        def transactionId = codec.encode([4, 5, 6] as byte[])

        expect:
        operations.withPropagation(transactionId, {
            operations.currentTransactionId().orElseThrow()
        }) == transactionId
    }

    def "nested propagation scopes restore the previous sessionless transaction state"() {
        given:
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def operations = new DefaultOracleSessionlessTransactionPropagationOperations(codec)
        def outerTransactionId = codec.encode([1, 1, 2] as byte[])
        def innerTransactionId = codec.encode([3, 5, 8] as byte[])

        when:
        def seenInner = operations.withPropagation(outerTransactionId, {
            assert operations.currentTransactionId().orElseThrow() == outerTransactionId
            def nested = operations.withPropagation(innerTransactionId, {
                operations.currentTransactionId().orElseThrow()
            })
            assert operations.currentTransactionId().orElseThrow() == outerTransactionId
            nested
        })

        then:
        seenInner == innerTransactionId
    }

    def "set transaction id requires an active propagation scope"() {
        given:
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def operations = new DefaultOracleSessionlessTransactionPropagationOperations(codec)
        def transactionId = codec.encode([9] as byte[])

        when:
        operations.setTransactionId(transactionId)

        then:
        thrown(TransactionUsageException)
    }

    def "propagation operations use the configured transaction id codec"() {
        given:
        def operations = new DefaultOracleSessionlessTransactionPropagationOperations(new HexTransactionIdCodec())

        expect:
        operations.withPropagation("01020a", {
            operations.currentTransactionId().orElseThrow()
        }) == "01020a"
    }

    private static final class HexTransactionIdCodec implements OracleSessionlessTransactionIdCodec {

        @Override
        String encode(byte[] gtrid) {
            gtrid.collect { String.format("%02x", it & 0xff) }.join()
        }

        @Override
        byte[] decode(String encodedTransactionId) {
            (0..<encodedTransactionId.length()).step(2).collect {
                Integer.parseInt(encodedTransactionId.substring(it, it + 2), 16) as byte
            } as byte[]
        }
    }
}
