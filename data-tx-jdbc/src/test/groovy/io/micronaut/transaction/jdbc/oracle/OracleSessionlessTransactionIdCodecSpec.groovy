package io.micronaut.transaction.jdbc.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import spock.lang.Specification

class OracleSessionlessTransactionIdCodecSpec extends Specification {

    def "default codec is used when no custom codec bean exists"() {
        when:
        def context = ApplicationContext.run()

        then:
        context.getBean(OracleSessionlessTransactionIdCodec) instanceof DefaultOracleSessionlessTransactionIdCodec

        cleanup:
        context.close()
    }

    def "custom codec replaces the default codec bean"() {
        when:
        def context = ApplicationContext.run("spec.name": "OracleSessionlessTransactionIdCodecSpec")

        then:
        context.getBeansOfType(OracleSessionlessTransactionIdCodec).size() == 1
        context.getBean(OracleSessionlessTransactionIdCodec) instanceof CustomTransactionIdCodec

        cleanup:
        context.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "OracleSessionlessTransactionIdCodecSpec")
    static final class CustomTransactionIdCodec implements OracleSessionlessTransactionIdCodec {

        @Override
        String encode(byte[] gtrid) {
            "custom"
        }

        @Override
        byte[] decode(String encodedTransactionId) {
            [1] as byte[]
        }
    }
}
