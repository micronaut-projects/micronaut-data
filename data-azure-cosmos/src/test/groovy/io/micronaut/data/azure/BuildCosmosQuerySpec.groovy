package io.micronaut.data.azure

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildCosmosQuerySpec extends AbstractTypeElementSpec {

    void "test cosmos repo"() {
        given:
        def repository = buildRepository('test.CosmosBookRepository', """
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.Book;
import java.util.Optional;
@CosmosRepository
interface CosmosBookRepository extends GenericRepository<Book, String> {
    Optional<Book> findById(String id);
}
"""
        )

        when:
        String q = getQuery(repository.getRequiredMethod("findById", String))
        then:
        q == "SELECT book_.id,book_.authorId,book_.title,book_.totalPages,book_.publisherId,book_.lastUpdated,book_.created FROM book book_ WHERE (book_.id = @p1)"
    }

    static String getQuery(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotation(Query).stringValue().get()
    }

    BeanDefinition<?> buildRepository(String name, String source) {
        def pkg = NameUtils.getPackageName(name)
        return buildBeanDefinition(name + BeanDefinitionVisitor.PROXY_SUFFIX, """
package $pkg;

import io.micronaut.data.model.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.*;
import java.util.*;

$source
""")

    }


}
