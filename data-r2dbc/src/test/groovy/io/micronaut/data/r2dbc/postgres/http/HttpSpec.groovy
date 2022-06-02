package io.micronaut.data.r2dbc.postgres.http

import io.micronaut.data.r2dbc.postgres.PostgresTestPropertyProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.Test
import spock.lang.Specification

@MicronautTest(transactional = false)
class SampleHttpSpec extends Specification implements PostgresTestPropertyProvider {

    @Inject
    @Client("/")
    HttpClient client

    @Test
    void testWriteRead() {
        when:
            SampleEntity se = new SampleEntity(null, "data")
            HttpResponse<?> postResponse = client.toBlocking().exchange(HttpRequest.POST("/samples", se))

        then:
            postResponse.getStatus().getCode() == 201
            def locationHeader = postResponse.getHeaders().get("Location")
            locationHeader
            String[] urlParts = locationHeader.split("/")
            urlParts[urlParts.length - 1] == "1"

        when:
            HttpResponse<SampleEntity> getResponse = client.toBlocking()
                    .exchange(HttpRequest.GET("/samples/" + urlParts[urlParts.length - 1]), SampleEntity.class)
        then:
            getResponse.getStatus().getCode() == 200
            getResponse.getBody().isPresent()
    }

}
