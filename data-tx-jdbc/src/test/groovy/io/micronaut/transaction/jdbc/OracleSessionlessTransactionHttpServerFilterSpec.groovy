package io.micronaut.transaction.jdbc

import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionContext
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionHttpConfiguration
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionHttpServerFilter
import spock.lang.Specification

class OracleSessionlessTransactionHttpServerFilterSpec extends Specification {

    def "reads transaction id from the configured request header"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration)
        def gtrid = [1, 2, 3, 4] as byte[]
        def value = new OracleSessionlessTransactionContext(gtrid).encode()
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, value)

        when:
        filter.readTransactionId(request, context)

        then:
        def element = OracleSessionlessTransactionContext.find(context.context).orElseThrow()
        Arrays.equals(gtrid, element.gtrid())
    }

    def "request header replaces stale transaction id context"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration)
        def stale = new OracleSessionlessTransactionContext([9, 9, 9] as byte[])
        def incoming = new OracleSessionlessTransactionContext([1, 2, 3] as byte[])
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        context.add(stale)
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, incoming.encode())

        when:
        filter.readTransactionId(request, context)

        then:
        def elements = context.context.findAll(OracleSessionlessTransactionContext).toList()
        elements.size() == 1
        Arrays.equals(incoming.gtrid(), elements[0].gtrid())
    }

    def "writes transaction id to the configured response header"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration)
        def gtrid = [10, 20, 30] as byte[]
        def element = new OracleSessionlessTransactionContext(gtrid)
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def response = HttpResponse.ok()
        context.add(element)

        when:
        filter.writeTransactionId(response, context)

        then:
        response.headers.get(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME) == element.encode()
    }

    def "does not write a response header when no transaction id remains in context"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration)
        def element = new OracleSessionlessTransactionContext([10, 20, 30] as byte[])
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def response = HttpResponse.ok()
        context.add(element)
        context.remove(element)

        when:
        filter.writeTransactionId(response, context)

        then:
        !response.headers.contains(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME)
    }

    def "uses a custom configured header name"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        configuration.headerName = "X-Oracle-Sessionless-Tx"
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration)
        def element = new OracleSessionlessTransactionContext([1, 1, 2, 3, 5] as byte[])
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/").header("X-Oracle-Sessionless-Tx", element.encode())
        def response = HttpResponse.ok()

        when:
        filter.readTransactionId(request, context)
        filter.writeTransactionId(response, context)

        then:
        response.headers.get("X-Oracle-Sessionless-Tx") == element.encode()
        !response.headers.contains(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME)
    }

    def "rejects malformed transaction id header values"() {
        given:
        def configuration = new OracleSessionlessTransactionHttpConfiguration()
        def filter = new OracleSessionlessTransactionHttpServerFilter(configuration)
        def context = MutablePropagatedContext.of(PropagatedContext.empty())
        def request = HttpRequest.GET("/")
            .header(OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME, "*")

        when:
        filter.readTransactionId(request, context)

        then:
        def e = thrown(HttpStatusException)
        e.status == HttpStatus.BAD_REQUEST
    }
}
