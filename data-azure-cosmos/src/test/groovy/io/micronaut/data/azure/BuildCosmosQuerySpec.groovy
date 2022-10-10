package io.micronaut.data.azure

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.data.azure.entities.Family
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

    void "test object properties and arrays"() {
        given:
        def repository = buildRepository('test.FamilyRepository', """
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.azure.entities.Family;
import java.util.Optional;
@CosmosRepository
interface FamilyRepository extends GenericRepository<Family, String> {

    Optional<Family> findById(String id);
}
"""
        )

        when:
        def queryById = getQuery(repository.getRequiredMethod("findById", String))
        then:
        queryById == "SELECT family_.id,family_.lastName,family_.address,family_.children,family_.registered FROM family family_ WHERE (family_.id = @p1)"
    }

    void "test build delete query"() {
        given:
        def repository = buildRepository('test.FamilyRepository', """
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.azure.entities.Family;
import java.util.Optional;
import java.util.List;
@CosmosRepository
interface FamilyRepository extends GenericRepository<Family, String> {

    void deleteById(String id);

    void deleteByIds(List<String> ids);

    void deleteAll();

    void delete(Family family);
}
"""
        )

        when:
        def deleteByIdQuery = getQuery(repository.getRequiredMethod("deleteById", String))
        def deleteByIdsQuery = getQuery(repository.getRequiredMethod("deleteByIds", List<String>))
        def deleteAllQuery = getQuery(repository.getRequiredMethod("deleteAll"))
        def deleteQueryMethod = repository.getRequiredMethod("delete", Family)
        then:
        deleteByIdQuery == " FROM family  family_ WHERE (family_.id = @p1)"
        deleteByIdsQuery == " FROM family  family_ WHERE (family_.id IN (@p1))"
        deleteAllQuery == " FROM family  family_"
        !deleteQueryMethod.getAnnotation(Query)
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
