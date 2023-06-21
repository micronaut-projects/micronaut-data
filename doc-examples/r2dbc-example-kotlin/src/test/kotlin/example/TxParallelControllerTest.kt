package example

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInstance


@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxParallelControllerTest : AbstractTest(false) {

    @Inject
    @field:Client("/")
    private lateinit var client: HttpClient

    @Inject
    private lateinit var repo: DemoRepository


    @BeforeEach
    fun init() {
        repo.deleteAll().block()
        repo.save(DemoEntity(1, "Test")).block()
    }

    @RepeatedTest(10)
    fun `should allow parallel db invocations`() {
        val response = client.toBlocking().exchange<String>("/testParallelDbInvocation")
        assertEquals(200, response.status.code)
    }
}
