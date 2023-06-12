package example

import io.micronaut.data.tck.test.TxTest
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxTestX : TxTest() {
}
