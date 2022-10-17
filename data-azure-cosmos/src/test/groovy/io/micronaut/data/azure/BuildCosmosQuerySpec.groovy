package io.micronaut.data.azure

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.data.azure.entities.Address
import io.micronaut.data.azure.entities.Family
import io.micronaut.data.document.tck.entities.SettlementPk
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

    @Query("SELECT * FROM c WHERE c.title = :title")
    List<Book> findByTitle(String title);

    boolean existsById(String id);

    long count();

    long countById(String id);
}
"""
        )

        when:
        def findByIdQuery = getQuery(repository.getRequiredMethod("findById", String))
        def findByTitleMethod = repository.getRequiredMethod("findByTitle", String)
        def findByTitleQuery = getQuery(findByTitleMethod)
        def findByTitleRawQuery = findByTitleMethod.stringValue(Query.class, "rawQuery").orElse(null)
        def existsByIdQuery = getQuery(repository.getRequiredMethod("existsById", String))
        def countQuery = getQuery(repository.getRequiredMethod("count"))
        def countByIdQuery = getQuery(repository.getRequiredMethod("countById", String))
        then:
        findByIdQuery == "SELECT DISTINCT VALUE book_ FROM book book_ WHERE (book_.id = @p1)"
        findByTitleQuery == "SELECT * FROM c WHERE c.title = :title"
        findByTitleRawQuery == "SELECT * FROM c WHERE c.title = @p1"
        existsByIdQuery == "SELECT true FROM book book_ WHERE (book_.id = @p1)"
        countQuery == "SELECT VALUE COUNT(1) FROM book book_"
        countByIdQuery == "SELECT VALUE COUNT(1) FROM book book_ WHERE (book_.id = @p1)"
    }

    void "test object properties and arrays"() {
        given:
        def repository = buildRepository('test.FamilyRepository', """
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.azure.entities.Family;
import io.micronaut.data.azure.entities.Child;
import java.util.Optional;

@CosmosRepository
interface FamilyRepository extends GenericRepository<Family, String> {

    Optional<Family> findById(String id);

    @Join(value = "children", alias = "c")
    List<Family> findByAddressStateOrderByChildrenFirstName(String state);

    @Join(value = "children")
    List<Family> findByChildrenFirstName(String firstName);

    @Join(value = "children")
    @Join(value = "children.pets")
    List<Family> findByChildrenPetsGivenNameOrderByChildrenPetsType(String type);

    @Join(value = "children")
    public abstract List<Child> findChildrenByChildrenPetsGivenName(String givenName);

}
"""
        )

        when:
        def findByIdQuery = getQuery(repository.getRequiredMethod("findById", String))
        def findByAddressStateQuery = getQuery(repository.getRequiredMethod("findByAddressStateOrderByChildrenFirstName", String))
        def findByChildrenFirstNameQuery = getQuery(repository.getRequiredMethod("findByChildrenFirstName", String))
        def findByChildrenPetsGivenNameOrderByChildrenPetsTypeQuery = getQuery(repository.getRequiredMethod("findByChildrenPetsGivenNameOrderByChildrenPetsType", String))
        def findChildrenByChildrenPetsGivenNameQuery = getQuery(repository.getRequiredMethod("findChildrenByChildrenPetsGivenName", String))
        then:
        findByIdQuery == "SELECT DISTINCT VALUE family_ FROM family family_ WHERE (family_.id = @p1)"
        findByAddressStateQuery == "SELECT DISTINCT VALUE family_ FROM family family_ JOIN c IN family_.children WHERE (family_.address.state = @p1) ORDER BY c.firstName ASC"
        findByChildrenFirstNameQuery == "SELECT DISTINCT VALUE family_ FROM family family_ JOIN family_children_ IN family_.children WHERE (family_children_.firstName = @p1)"
        findByChildrenPetsGivenNameOrderByChildrenPetsTypeQuery == "SELECT DISTINCT VALUE family_ FROM family family_ JOIN family_children_ IN family_.children JOIN family_children_pets_ IN family_children_.pets WHERE (family_children_pets_.givenName = @p1) ORDER BY family_children_pets_.type ASC"
        findChildrenByChildrenPetsGivenNameQuery == "SELECT family_children_.firstName,family_children_.gender,family_children_.grade FROM family family_ JOIN family_children_ IN family_.children JOIN family_children_pets_ IN family_children_.pets WHERE (family_children_pets_.givenName = @p1)"
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
        deleteByIdQuery == "SELECT *  FROM family  family_ WHERE (family_.id = @p1)"
        deleteByIdsQuery == "SELECT *  FROM family  family_ WHERE (family_.id IN (@p1))"
        deleteAllQuery == "SELECT *  FROM family  family_"
        !deleteQueryMethod.getAnnotation(Query)
    }

    void "test build update query"() {
        given:
        def repository = buildRepository('test.FamilyRepository', """
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.azure.entities.Family;
import io.micronaut.data.azure.entities.Address;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import java.util.Optional;
import java.util.List;
@CosmosRepository
interface FamilyRepository extends GenericRepository<Family, String> {
    long updateRegistered(@Parameter("id") @Id String id, @Parameter("registered") boolean registered);

    void updateAddress(@Parameter("id") @Id String id, @Parameter("address") Address address);

    void updateByAddressState(String state, boolean registered, Date registeredDate);
}
"""
        )

        when:
        def updateRegisteredMethod = repository.getRequiredMethod("updateRegistered", String, boolean)
        def updateRegisteredQuery = getQuery(updateRegisteredMethod)
        def updateRegisteredQueryUpdate = updateRegisteredMethod.stringValue(Query.class, "update").orElse(null)
        def updateAddressMethod = repository.getRequiredMethod("updateAddress", String, Address)
        def updateAddressQuery = getQuery(updateAddressMethod)
        def updateAddressQueryUpdate = updateAddressMethod.stringValue(Query.class, "update").orElse(null)

        def updateByAddressStateMethod = repository.getRequiredMethod("updateByAddressState", String, boolean, Date)
        def updateByAddressStateQuery = getQuery(updateByAddressStateMethod)
        def updateByAddressStateUpdate = updateByAddressStateMethod.stringValue(Query.class, "update").orElse(null)
        then:
        updateRegisteredQuery == "SELECT * FROM family family_ WHERE (family_.id = @p2)"
        updateRegisteredQueryUpdate == "registered"

        updateAddressQueryUpdate == "address"
        updateAddressQuery == "SELECT * FROM family family_ WHERE (family_.id = @p2)"

        updateByAddressStateUpdate == "registered,registeredDate"
        updateByAddressStateQuery == "SELECT * FROM family family_ WHERE (family_.address.state = @p3)"
    }

    void "test cosmos settlement embedded pk repo"() {
        given:
        def repository = buildRepository('test.CosmosSettlementRepository', """
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.document.tck.entities.Settlement;
import io.micronaut.data.document.tck.entities.SettlementPk;
import io.micronaut.data.repository.GenericRepository;
import java.util.Optional;
@CosmosRepository
interface CosmosSettlementRepository extends GenericRepository<Settlement, SettlementPk> {
    Optional<Settlement> queryById(SettlementPk settlementPk);
}
"""
        )
        when:
        String queryByIdQuery = getQuery(repository.getRequiredMethod("queryById", SettlementPk))
        then:
        queryByIdQuery == "SELECT DISTINCT VALUE settlement_ FROM comp_settlement settlement_ WHERE (settlement_.code = @p1 AND settlement_.code_id = @p2 AND settlement_.id.county.id.id = @p3 AND settlement_.id.county.id.state.id = @p4)"
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

    static String getQuery(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotation(Query).stringValue().get()
    }
}
