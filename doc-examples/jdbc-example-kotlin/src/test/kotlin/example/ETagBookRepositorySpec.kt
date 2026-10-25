package example

import io.micronaut.context.BeanContext
import io.micronaut.data.annotation.Query
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The generated ETag defaults to Oracle's SYS_ROW_ETAG function, so this module (which runs on H2)
 * only verifies the queries generated for the Oracle dialect repository.
 */
@MicronautTest(startApplication = false)
class ETagBookRepositorySpec {

    @Inject
    lateinit var beanContext: BeanContext

    @Test
    fun testGeneratedETagQueries() {
        val definition = beanContext.getBeanDefinition(ETagBookRepository::class.java)
        fun query(methodName: String) = definition.executableMethods
            .first { it.methodName == methodName }
            .annotationMetadata
            .stringValue(Query::class.java)
            .orElse(null)
        val findById = query("findById")
        val update = query("update")

        // chapters is excluded from the ETag input with @ETagValue(exclude = true)
        assertEquals(
            "SELECT etag_book_.\"ID\",SYS_ROW_ETAG(etag_book_.id, etag_book_.title, etag_book_.pages) AS etag,etag_book_.\"TITLE\",etag_book_.\"PAGES\",etag_book_.\"CHAPTERS\" FROM \"ETAG_BOOK\" etag_book_ WHERE (etag_book_.\"ID\" = ?)",
            findById
        )
        assertTrue(update.contains("SYS_ROW_ETAG("), update)
    }
}
