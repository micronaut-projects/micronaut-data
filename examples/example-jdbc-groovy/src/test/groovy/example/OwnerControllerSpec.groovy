package example

import example.domain.Owner
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class OwnerControllerSpec extends Specification {
    @Inject
    @Client("/owners")
    RxHttpClient client

    void "test initial owners"() {

        when:"initial owners are retrieved"
        List<Owner> results = client.retrieve(HttpRequest.GET("/"), Argument.listOf(Owner.class))
                                    .blockingFirst()

        then:"there are 2"
        results.size() == 2
    }
}
