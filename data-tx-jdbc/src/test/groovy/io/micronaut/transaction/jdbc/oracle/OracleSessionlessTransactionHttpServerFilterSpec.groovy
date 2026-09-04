package io.micronaut.transaction.jdbc.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.exceptions.HttpStatusException
import spock.lang.Specification

class OracleSessionlessTransactionHttpServerFilterSpec extends Specification {

    def "reads transaction id from the configured request header"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, codec)
        def gtrid = [1, 2, 3, 4] as byte[]
        def value = codec.encode(gtrid)
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, value)

        when:
        filter.readTransactionId(request, context)

        then:
        def state = OracleSessionlessTransactionState.find(context.context).orElseThrow()
        Arrays.equals(gtrid, state.gtrid.orElseThrow())
    }

    def "request filter rejects existing transaction state"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, codec)
        def stale = new OracleSessionlessTransactionState()
        stale.setGtrid([9, 9, 9] as byte[])
        def incoming = [1, 2, 3] as byte[]
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        context.add(stale)
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, codec.encode(incoming))

        when:
        filter.readTransactionId(request, context)

        then:
        def e = thrown(HttpStatusException)
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        def states = context.context.findAll(OracleSessionlessTransactionState).toList()
        states.size() == 1
        Arrays.equals([9, 9, 9] as byte[], states[0].gtrid.orElseThrow())
    }

    def "request without header installs empty transaction state"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, new DefaultOracleSessionlessTransactionIdCodec())
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/")

        when:
        filter.readTransactionId(request, context)

        then:
        def state = OracleSessionlessTransactionState.find(context.context).orElseThrow()
        state.gtrid.isEmpty()
    }

    def "writes transaction id to the configured response header"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, codec)
        def gtrid = [10, 20, 30] as byte[]
        def encodedGtrid = codec.encode(gtrid)
        def state = new OracleSessionlessTransactionState()
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def response = HttpResponse.ok()
        state.setGtrid(gtrid)
        context.add(state)

        when:
        filter.writeTransactionId(response, context, null)

        then:
        response.headers.get(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME) == encodedGtrid
        OracleSessionlessTransactionState.find(context.context).isEmpty()
    }

    def "writes transaction id when the response contains a downstream failure"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, codec)
        def gtrid = [10, 20, 30] as byte[]
        def state = new OracleSessionlessTransactionState()
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def response = HttpResponse.serverError()
        state.setGtrid(gtrid)
        context.add(state)

        when:
        filter.writeTransactionId(response, context, new IllegalStateException("response failed"))

        then:
        response.headers.get(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME) == codec.encode(gtrid)
        OracleSessionlessTransactionState.find(context.context).isEmpty()
    }

    def "does not write a response header and removes state when no transaction id remains in context"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, new DefaultOracleSessionlessTransactionIdCodec())
        def state = new OracleSessionlessTransactionState()
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def response = HttpResponse.ok()
        context.add(state)

        when:
        filter.writeTransactionId(response, context, null)

        then:
        !response.headers.contains(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME)
        OracleSessionlessTransactionState.find(context.context).isEmpty()
    }

    def "uses a custom configured header name"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        configuration.headerName = "X-Oracle-Sessionless-Tx"
        def codec = new DefaultOracleSessionlessTransactionIdCodec()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, codec)
        def encodedGtrid = codec.encode([1, 1, 2, 3, 5] as byte[])
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/").header("X-Oracle-Sessionless-Tx", encodedGtrid)
        def response = HttpResponse.ok()

        when:
        filter.readTransactionId(request, context)
        filter.writeTransactionId(response, context, null)

        then:
        response.headers.get("X-Oracle-Sessionless-Tx") == encodedGtrid
        !response.headers.contains(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME)
        OracleSessionlessTransactionState.find(context.context).isEmpty()
    }

    def "rejects malformed transaction id header values"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, new DefaultOracleSessionlessTransactionIdCodec())
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, "*")

        when:
        filter.readTransactionId(request, context)

        then:
        def e = thrown(HttpStatusException)
        e.status == HttpStatus.BAD_REQUEST
    }

    def "uses the configured transaction id codec"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def codec = new ReversingTransactionIdCodec()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration, codec)
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, "4,3,2,1")
        def response = HttpResponse.ok()

        when:
        filter.readTransactionId(request, context)
        def state = OracleSessionlessTransactionState.find(context.context).orElseThrow()
        filter.writeTransactionId(response, context, null)

        then:
        Arrays.equals([1, 2, 3, 4] as byte[], state.gtrid.orElseThrow())
        response.headers.get(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME) == "4,3,2,1"
        OracleSessionlessTransactionState.find(context.context).isEmpty()
    }

    def "http filter is enabled by the propagation enabled property"() {
        when:
        def context = ApplicationContext.run([
            "micronaut.data.oracle.sessionless.http.propagation-enabled": true
        ])

        then:
        context.containsBean(OracleSessionlessTransactionHttpServerFilter)
        context.getBeanDefinition(OracleSessionlessTransactionHttpServerFilter)
            .getRequiredMethod("writeTransactionId", MutableHttpResponse, MutablePropagatedContext, Throwable)
            .arguments[2]
            .nullable

        cleanup:
        context.close()
    }

    private static final class ReversingTransactionIdCodec implements OracleSessionlessTransactionIdCodec {

        @Override
        String encode(byte[] gtrid) {
            gtrid.reverse().join(",")
        }

        @Override
        byte[] decode(String encodedTransactionId) {
            encodedTransactionId.split(",")*.toInteger().reverse() as byte[]
        }
    }
}
