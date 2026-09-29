package example

import io.micronaut.context.BeanContext
import io.micronaut.data.annotation.Query
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * The generated ETag defaults to Oracle's SYS_ROW_ETAG function, so this module (which runs on H2)
 * only verifies the queries generated for the Oracle dialect repository.
 */
@MicronautTest(startApplication = false)
class ETagBookRepositorySpec extends Specification {

    @Inject
    BeanContext beanContext

    void 'test generated ETag queries'() {
        given:
        def definition = beanContext.getBeanDefinition(ETagBookRepository)
        def query = { String methodName ->
            definition.executableMethods
                    .find { it.methodName == methodName }
                    .annotationMetadata
                    .stringValue(Query)
                    .orElse(null)
        }

        expect: 'chapters is excluded from the ETag input with @ETagValue(exclude = true)'
        query('findById') == 'SELECT etag_book_."ID",SYS_ROW_ETAG(etag_book_.id, etag_book_.title, etag_book_.pages) AS etag,etag_book_."TITLE",etag_book_."PAGES",etag_book_."CHAPTERS" FROM "ETAG_BOOK" etag_book_ WHERE (etag_book_."ID" = ?)'
        query('update').contains('SYS_ROW_ETAG(')
    }
}
