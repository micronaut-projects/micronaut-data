package io.micronaut.data.runtime.criteria

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Join
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.model.entities.Book
import io.micronaut.data.model.jpa.criteria.*
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.tck.tests.AbstractCriteriaSpec
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Subquery
import jakarta.persistence.metamodel.*
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class CriteriaSpec extends AbstractCriteriaSpec {

    PersistentEntityCriteriaBuilder criteriaBuilder

    PersistentEntityCriteriaQuery criteriaQuery

    PersistentEntityCriteriaDelete criteriaDelete

    PersistentEntityCriteriaUpdate criteriaUpdate

    void setup() {
        Map<Class, RuntimePersistentEntity> map = new HashMap<>();
        criteriaBuilder = new RuntimeCriteriaBuilder(new RuntimeEntityRegistry() {
            @Override
            EntityEventListener<Object> getEntityEventListener() {
                throw new IllegalStateException()
            }

            @Override
            Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
                throw new IllegalStateException()
            }

            @Override
             <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
                return map.computeIfAbsent(type, RuntimePersistentEntity::new)
            }

            @Override
             <T> RuntimePersistentEntity<T> newEntity(Class<T> type) {
                throw new IllegalStateException()
            }

            @Override
            ApplicationContext getApplicationContext() {
                throw new IllegalStateException()
            }
        })
        criteriaQuery = criteriaBuilder.createQuery()
        criteriaDelete = criteriaBuilder.createCriteriaDelete(Test)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(Test)
    }

    @Override
    PersistentEntityRoot createRoot(Subquery query) {
        return query.from(Test)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(Test)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(Test)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(Test)
    }

    void "test subquery with referenced outer"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def subquery = criteriaQuery.subquery(Long)
            def subqueryBookRoot = subquery.from(Book)
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.join("author").get("id"), bookRoot.join("author").get("id")))
            criteriaQuery.where(
                    bookRoot.<Long>get("id").in(subquery)
            )
            criteriaQuery.orderBy(criteriaBuilder.asc(bookRoot.get("title")))
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT book_."id",book_."author_id",book_."title",book_."pages",book_."publisher_id" FROM "book" book_ WHERE (book_."id" IN (SELECT book_book_."id" FROM "book" book_book_ INNER JOIN "author" book_book_author_ ON book_book_."author_id"=book_book_author_."id" WHERE (book_book_author_."id" = book_book_author_."id"))) ORDER BY book_."title" ASC'''
    }

    void "test subquery IN with JOIN"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def subquery = criteriaQuery.subquery(Long)
            def subqueryBookRoot = subquery.from(Book)
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("id"), 123))
            bookRoot.join("author", Join.Type.FETCH)
            criteriaQuery.where(
                    bookRoot.<Long>get("id").in(subquery)
            )
            criteriaQuery.orderBy(criteriaBuilder.asc(bookRoot.get("title")))
            String query = getSqlQuery(criteriaQuery)

        expect:
            // language=SQL
            query == '''SELECT book_."id",book_."author_id",book_."title",book_."pages",book_."publisher_id",book_author_."name" AS author_name FROM "book" book_ INNER JOIN "author" book_author_ ON book_."author_id"=book_author_."id" WHERE (book_."id" IN (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" = ?))) ORDER BY book_."title" ASC'''
    }

    void "test subquery IN"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def subquery = criteriaQuery.subquery(Long)
            def subqueryBookRoot = subquery.from(Book)
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("id"), 123))
            criteriaQuery.where(
                    bookRoot.<Long>get("id").in(subquery)
            )
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT book_."id",book_."author_id",book_."title",book_."pages",book_."publisher_id" FROM "book" book_ WHERE (book_."id" IN (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" = ?)))'''
    }

    void "test query entity type subquery and parameters"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def entityType = new SimpleEntityType<>(Book)
            def bookRoot = criteriaQuery.from(Book)
            def parameter = criteriaBuilder.parameter(Long, "id")
            criteriaQuery.where(criteriaBuilder.equal(bookRoot.get("id"), parameter))
            def subquery = criteriaQuery.subquery(entityType)
            def subqueryBookRoot = subquery.from(Book)
            def nestedParameter = criteriaBuilder.parameter(Long, "nestedId")
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("id"), nestedParameter))

        expect:
            criteriaQuery.getParameters() == [parameter] as Set
            subquery.getParameters() == [nestedParameter] as Set
            subquery.getContainingQuery().is(criteriaQuery)
    }

    void "test subquery root correlation metadata"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def subquery = criteriaQuery.subquery(Long)

        when:
            def correlatedRoot = subquery.correlate(bookRoot)
            subquery.select(correlatedRoot.get("id"))

        then:
            subquery.getRoots() == [correlatedRoot] as Set
            correlatedRoot.isCorrelated()
            correlatedRoot.getCorrelationParent().is(bookRoot)
            subquery.getCorrelatedJoins().isEmpty()
            subquery.getContainingQuery().is(criteriaQuery)
    }

    void "test subquery join correlation metadata"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def authorJoin = bookRoot.join("author")
            def subquery = criteriaQuery.subquery(Long)

        when:
            def correlatedJoin = subquery.correlate(authorJoin)

        then:
            correlatedJoin.is(authorJoin)
            correlatedJoin.isCorrelated()
            correlatedJoin.getCorrelationParent().is(bookRoot)
            subquery.getCorrelatedJoins() == [authorJoin] as Set
    }

    void "test subquery list correlation metadata"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)
            def listJoin = root.joinList("others")
            def listSubquery = criteriaQuery.subquery(Long)

        when:
            def correlatedList = listSubquery.correlate(listJoin)

        then:
            correlatedList.is(listJoin)
            correlatedList.isCorrelated()
            correlatedList.getCorrelationParent().is(root)
            listSubquery.getCorrelatedJoins() == [listJoin] as Set
    }

    void "test joinCollection on list backed association is still unsupported"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)

        when:
            root.joinCollection("others")

        then:
            def ex = thrown(IllegalStateException)
            ex.message == 'Join is not a Collection!'
    }

    void "test joinPlural on list backed association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)

        when:
            def pluralJoin = root.joinPlural("others")
            criteriaQuery.where(criteriaBuilder.isNotNull(pluralJoin.get("id")))

        then:
            pluralJoin != null
            getSqlQuery(criteriaQuery) == 'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ INNER JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id" WHERE (test_others_."id" IS NOT NULL)'
    }

    void "test joinPlural metamodel overload on list backed association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)
            def pluralAttribute = new SimplePluralAttribute<Test, List<OtherEntity>, OtherEntity>("others")

        when:
            def pluralJoin = root.joinPlural(pluralAttribute, JoinType.LEFT)
            criteriaQuery.where(criteriaBuilder.isNotNull(pluralJoin.get("id")))

        then:
            pluralJoin != null
            getSqlQuery(criteriaQuery) == 'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget",test_others_."id" AS others_id,test_others_."name" AS others_name,test_others_."enabled2" AS others_enabled2,test_others_."enabled" AS others_enabled,test_others_."age" AS others_age,test_others_."amount" AS others_amount,test_others_."budget" AS others_budget,test_others_."test_id" AS others_test_id,test_others_."simple_id" AS others_simple_id FROM "test" test_ LEFT JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id" WHERE (test_others_."id" IS NOT NULL)'
    }

    void "test joinPlural micronaut join type overload on list backed association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)

        when:
            def pluralJoin = root.joinPlural("others", io.micronaut.data.annotation.Join.Type.LEFT_FETCH)
            criteriaQuery.where(criteriaBuilder.isNotNull(pluralJoin.get("id")))

        then:
            pluralJoin != null
            getSqlQuery(criteriaQuery) == 'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget",test_others_."id" AS others_id,test_others_."name" AS others_name,test_others_."enabled2" AS others_enabled2,test_others_."enabled" AS others_enabled,test_others_."age" AS others_age,test_others_."amount" AS others_amount,test_others_."budget" AS others_budget,test_others_."test_id" AS others_test_id,test_others_."simple_id" AS others_simple_id FROM "test" test_ LEFT JOIN "other_entity" test_others_ ON test_."id"=test_others_."test_id" WHERE (test_others_."id" IS NOT NULL)'
    }

    void "test runtime map metamodel attribute resolution"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(MapOwnerEntity)
            def root = criteriaQuery.from(MapOwnerEntity)

        when:
            def model = root.getModel()
            def mapAttribute = model.getDeclaredMap("attributes")

        then:
            mapAttribute != null
            mapAttribute.collectionType == PluralAttribute.CollectionType.MAP
            mapAttribute.name == 'attributes'
    }

    void "test joinMapPath on runtime map association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(MapOwnerEntity)
            def root = criteriaQuery.from(MapOwnerEntity)

        when:
            def mapPath = root.joinMapPath('attributes')
            criteriaQuery.where(criteriaBuilder.isNotNull(mapPath.get('id')))

        then:
            mapPath != null
            getSqlQuery(criteriaQuery) == 'SELECT map_owner_entity_."id" FROM "map_owner_entity" map_owner_entity_ INNER JOIN "map_owner_entity_map_value_entity" map_owner_entity_attributes_map_owner_entity_map_value_entity_ ON map_owner_entity_."id"=map_owner_entity_attributes_map_owner_entity_map_value_entity_."map_owner_entity_id"  INNER JOIN "map_value_entity" map_owner_entity_attributes_ ON map_owner_entity_attributes_map_owner_entity_map_value_entity_."map_value_entity_id"=map_owner_entity_attributes_."id" WHERE (map_owner_entity_attributes_."id" IS NOT NULL)'
    }

    void "test joinMapPath metamodel overload on runtime map association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(MapOwnerEntity)
            def root = criteriaQuery.from(MapOwnerEntity)
            def mapAttribute = root.getModel().getDeclaredMap('attributes')

        when:
            def mapPath = root.joinMapPath(mapAttribute, JoinType.LEFT)
            criteriaQuery.where(criteriaBuilder.isNotNull(mapPath.get('id')))

        then:
            mapPath != null
            getSqlQuery(criteriaQuery) == 'SELECT map_owner_entity_."id",map_owner_entity_attributes_."id" AS attributes_id,map_owner_entity_attributes_."name" AS attributes_name FROM "map_owner_entity" map_owner_entity_ LEFT JOIN "map_owner_entity_map_value_entity" map_owner_entity_attributes_map_owner_entity_map_value_entity_ ON map_owner_entity_."id"=map_owner_entity_attributes_map_owner_entity_map_value_entity_."map_owner_entity_id"  LEFT JOIN "map_value_entity" map_owner_entity_attributes_ ON map_owner_entity_attributes_map_owner_entity_map_value_entity_."map_value_entity_id"=map_owner_entity_attributes_."id" WHERE (map_owner_entity_attributes_."id" IS NOT NULL)'
    }

    void "test joinMapPath micronaut join type overload on runtime map association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(MapOwnerEntity)
            def root = criteriaQuery.from(MapOwnerEntity)

        when:
            def mapPath = root.joinMapPath('attributes', io.micronaut.data.annotation.Join.Type.LEFT_FETCH)
            criteriaQuery.where(criteriaBuilder.isNotNull(mapPath.get('id')))

        then:
            mapPath != null
            getSqlQuery(criteriaQuery) == 'SELECT map_owner_entity_."id",map_owner_entity_attributes_."id" AS attributes_id,map_owner_entity_attributes_."name" AS attributes_name FROM "map_owner_entity" map_owner_entity_ LEFT JOIN "map_owner_entity_map_value_entity" map_owner_entity_attributes_map_owner_entity_map_value_entity_ ON map_owner_entity_."id"=map_owner_entity_attributes_map_owner_entity_map_value_entity_."map_owner_entity_id"  LEFT JOIN "map_value_entity" map_owner_entity_attributes_ ON map_owner_entity_attributes_map_owner_entity_map_value_entity_."map_value_entity_id"=map_owner_entity_attributes_."id" WHERE (map_owner_entity_attributes_."id" IS NOT NULL)'
    }

    void "test collection emptiness predicates on list association"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)

        when:
            criteriaQuery.where(criteriaBuilder.and(
                    criteriaBuilder.isEmpty(root.get("others")),
                    criteriaBuilder.isNotEmpty(root.get("others"))
            ))

        then:
            getSqlQuery(criteriaQuery) == 'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ WHERE (test_."other_entity_test" IS EMPTY AND test_."other_entity_test" IS NOT EMPTY)'
    }

    void "test collection size and membership functions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)

        when:
            criteriaQuery.where(criteriaBuilder.and(
                    criteriaBuilder.equal(criteriaBuilder.size(root.get("others")), 3),
                    criteriaBuilder.isMember(new OtherEntity("x"), root.get("others")),
                    criteriaBuilder.isNotMember(new OtherEntity("y"), root.get("others"))
            ))

        then:
            getSqlQuery(criteriaQuery) == 'SELECT test_."id",test_."name",test_."enabled2",test_."enabled",test_."age",test_."amount",test_."budget" FROM "test" test_ WHERE ((SELECT COUNT(*) FROM other_entity_test) = ? AND EXISTS (SELECT 1 FROM other_entity_test WHERE test = ?) = TRUE AND EXISTS (SELECT 1 FROM other_entity_test WHERE test = ?) = FALSE)'
    }

    void "test query parameter extraction through nested expressions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def functionParameter = criteriaBuilder.parameter(String, "title")
            def left = criteriaBuilder.concat(bookRoot.get("title"), functionParameter)
            def right = criteriaBuilder.concat(criteriaBuilder.parameter(String, "prefix"), criteriaBuilder.literal("-x"))
            def likeParameter = criteriaBuilder.parameter(String, "like")
            criteriaQuery.select(criteriaBuilder.concat(left, right))
            criteriaQuery.where(criteriaBuilder.like(criteriaBuilder.lower(bookRoot.get("title")), likeParameter))
            criteriaQuery.orderBy(criteriaBuilder.asc(criteriaBuilder.concat(bookRoot.get("title"), criteriaBuilder.parameter(String, "order"))))

        expect:
            criteriaQuery.getParameters()*.name as Set == ["title", "prefix", "like", "order", "default"] as Set
    }

    void "test query parameter extraction for aliased compound selection"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object)
            def bookRoot = criteriaQuery.from(Book)
            def left = criteriaBuilder.concat(bookRoot.get("title"), criteriaBuilder.parameter(String, "left"))
            def right = criteriaBuilder.concat(criteriaBuilder.parameter(String, "right"), bookRoot.get("title"))
            criteriaQuery.multiselect(left.alias("l"), right.alias("r"))

        expect:
            criteriaQuery.getParameters()*.name as Set == ["left", "right"] as Set
    }

    void "test query parameter extraction for between and in expressions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def fromParameter = criteriaBuilder.parameter(Long, "from")
            def toParameter = criteriaBuilder.parameter(Long, "to")
            def inParameter = criteriaBuilder.parameter(Long, "in")
            criteriaQuery.where(criteriaBuilder.and(
                    criteriaBuilder.between(bookRoot.get("id"), fromParameter, toParameter),
                    bookRoot.get("id").in(inParameter)
            ))

        expect:
            criteriaQuery.getParameters()*.name as Set == ["from", "to", "in"] as Set
    }

    void "test sum widening expressions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object[])
            def root = criteriaQuery.from(Test)

        when:
            criteriaQuery.multiselect(
                    criteriaBuilder.sumAsLong(root.get("age")),
                    criteriaBuilder.sumAsDouble(criteriaBuilder.literal(1.5f))
            )

        then:
            getSqlQuery(criteriaQuery) == 'SELECT SUM(test_."age"),SUM(?) FROM "test" test_'
    }

    void "test substring locate and current time functions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object[])
            def root = criteriaQuery.from(Book)

        when:
            criteriaQuery.multiselect(
                    criteriaBuilder.substring(root.get("title"), 2),
                    criteriaBuilder.substring(root.get("title"), 2, 3),
                    criteriaBuilder.locate(root.get("title"), "Micronaut"),
                    criteriaBuilder.locate(root.get("title"), "Data", 4),
                    criteriaBuilder.currentDate(),
                    criteriaBuilder.currentTime(),
                    criteriaBuilder.currentTimestamp()
            )

        then:
            getSqlQuery(criteriaQuery) == 'SELECT SUBSTRING(book_."title",?),SUBSTRING(book_."title",?,?),LOCATE(?,book_."title"),LOCATE(?,book_."title",?),CURRENT_DATE(),CURRENT_TIME(),CURRENT_TIMESTAMP() FROM "book" book_'
    }

    void "test trim functions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object[])
            def root = criteriaQuery.from(Book)

        when:
            criteriaQuery.multiselect(
                    criteriaBuilder.trim(root.get("title")),
                    criteriaBuilder.trim(jakarta.persistence.criteria.CriteriaBuilder.Trimspec.LEADING, root.get("title")),
                    criteriaBuilder.trim(criteriaBuilder.literal('x' as char), root.get("title")),
                    criteriaBuilder.trim(jakarta.persistence.criteria.CriteriaBuilder.Trimspec.TRAILING, 'y' as char, root.get("title"))
            )

        then:
            getSqlQuery(criteriaQuery) == 'SELECT TRIM(book_."title"),TRIM(LEADING FROM book_."title"),TRIM(BOTH ? FROM book_."title"),TRIM(TRAILING ? FROM book_."title") FROM "book" book_'
    }

    void "test mod sqrt and numeric conversions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object[])
            def root = criteriaQuery.from(Test)

        when:
            criteriaQuery.multiselect(
                    criteriaBuilder.mod(root.get("age"), 2),
                    criteriaBuilder.sqrt(root.get("amount")),
                    criteriaBuilder.toLong(root.get("age")),
                    criteriaBuilder.toInteger(root.get("budget")),
                    criteriaBuilder.toFloat(root.get("age")),
                    criteriaBuilder.toDouble(root.get("amount")),
                    criteriaBuilder.toBigDecimal(root.get("age")),
                    criteriaBuilder.toBigInteger(root.get("budget"))
            )

        then:
            getSqlQuery(criteriaQuery) == 'SELECT test_."age" % ?,SQRT(test_."amount"),CAST(test_."age" AS BIGINT),CAST(test_."budget" AS INTEGER),CAST(test_."age" AS FLOAT),CAST(test_."amount" AS DOUBLE),CAST(test_."age" AS DECIMAL),CAST(test_."budget" AS BIGINT) FROM "test" test_'
    }

    void "test coalesce and nullif functions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object[])
            def root = criteriaQuery.from(Book)

        when:
            criteriaQuery.multiselect(
                    criteriaBuilder.coalesce(root.get("title"), criteriaBuilder.literal("fallback")),
                    criteriaBuilder.coalesce(root.get("title"), "default"),
                    criteriaBuilder.nullif(root.get("title"), criteriaBuilder.literal("unknown")),
                    criteriaBuilder.nullif(root.get("title"), "n/a")
            )

        then:
            getSqlQuery(criteriaQuery) == 'SELECT COALESCE(book_."title",?),COALESCE(book_."title",?),NULLIF(book_."title",?),NULLIF(book_."title",?) FROM "book" book_'
    }

    void "test searched and simple case expressions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Object[])
            def root = criteriaQuery.from(Test)
            def searchedCase = criteriaBuilder.selectCase()
                    .when(criteriaBuilder.isTrue(root.get("enabled2")), "yes")
                    .otherwise("no")
            def simpleCase = criteriaBuilder.selectCase(root.get("name"))
                    .when("foo", "FOO")
                    .otherwise("OTHER")

        when:
            criteriaQuery.multiselect(searchedCase, simpleCase)

        then:
            getSqlQuery(criteriaQuery) == "SELECT CASE WHEN test_.\"enabled2\" = TRUE THEN 'yes' ELSE 'no' END,CASE test_.\"name\" WHEN 'foo' THEN 'FOO' ELSE 'OTHER' END FROM \"test\" test_"
    }

    void "test unary numeric expressions"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Test)
            def root = criteriaQuery.from(Test)

        when:
            criteriaQuery.select(criteriaBuilder.abs(criteriaBuilder.neg(root.get("amount"))))

        then:
            getSqlQuery(criteriaQuery) == 'SELECT ABS(-test_."amount") FROM "test" test_'
    }

    void "test runtime property paths expose metadata"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def titlePath = bookRoot.get("title")
            def customerRoot = criteriaBuilder.createQuery(TestCustomer).from(TestCustomer)
            def embeddedIdPath = customerRoot.get("id")
            def embeddedIdNamePath = embeddedIdPath.get("name")

        expect:
            titlePath.getJavaType() == String
            titlePath.getParentPath().is(bookRoot)
            titlePath.getModel().getBindableJavaType() == String
            bookRoot.getModel().getJavaType() == Book
            bookRoot.getModel().getBindableJavaType() == Book
            bookRoot.getModel().getName() == 'book'
            embeddedIdPath.getParentPath() != null
            embeddedIdNamePath.getJavaType() == String
            embeddedIdNamePath.getParentPath().is(embeddedIdPath)
    }

    void "test update entity type overloads"() {
        given:
            def criteriaUpdate = criteriaBuilder.createCriteriaUpdate(Book)
            def entityType = new SimpleEntityType<>(Book)
            def nameParameter = criteriaBuilder.parameter(String, "title", "Updated")
            def idParameter = criteriaBuilder.parameter(Long, "id")
            def root = criteriaUpdate.getRoot()

        when:
            def fromRoot = criteriaUpdate.from(entityType)
            criteriaUpdate.set("title", nameParameter)
            criteriaUpdate.where(criteriaBuilder.equal(root.get("id"), idParameter))

        then:
            fromRoot != null
            criteriaUpdate.getParameters() == [idParameter] as Set
            getSqlQuery(criteriaUpdate) == 'UPDATE "book" SET "title"=? WHERE ("id" = ?)'
    }

    void "test delete entity type overloads"() {
        given:
            def criteriaDelete = criteriaBuilder.createCriteriaDelete(Book)
            def entityType = new SimpleEntityType<>(Book)
            def idParameter = criteriaBuilder.parameter(Long, "id")
            def root = criteriaDelete.getRoot()

        when:
            def fromRoot = criteriaDelete.from(entityType)
            criteriaDelete.where(criteriaBuilder.equal(root.get("id"), idParameter))

        then:
            fromRoot != null
            criteriaDelete.getParameters() == [idParameter] as Set
            getSqlQuery(criteriaDelete) == 'DELETE  FROM "book"  WHERE ("id" = ?)'
    }

    void "test subquery EQ"() {
        given:
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(Book)
            def subquery = criteriaQuery.subquery(Long)
            def subqueryBookRoot = subquery.from(Book)
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("id"), 123))
            criteriaQuery.where(
                    criteriaBuilder.equal(bookRoot.<Long>get("id"), subquery)
            )
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT book_."id",book_."author_id",book_."title",book_."pages",book_."publisher_id" FROM "book" book_ WHERE (book_."id" = (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" = ?)))'''
    }

    void "test function projection 3"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(
                    criteriaBuilder.function(
                            "MYFUNC3",
                            String,
                            criteriaBuilder.parameter(String),
                            criteriaBuilder.literal("abc")
                    )
            )
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT MYFUNC3(?,?) FROM "test" test_'''
    }

    void "test criteria with embedded id"() {
        given:
        def criteriaQuery = criteriaBuilder.createQuery(TestCustomer)
        def testCustomerRoot = criteriaQuery.from(TestCustomer)
        criteriaQuery.where(criteriaBuilder.equal(testCustomerRoot.get("id").get("name"), "MyName"))
        String query = getSqlQuery(criteriaQuery)

        expect:
        query == 'SELECT test_customer_."id",test_customer_."name",test_customer_."version",test_customer_."address" FROM "CUSTOMER" test_customer_ WHERE (test_customer_."name" = ?)'
    }

    @Unroll
    void "test criteria predicate"(Specification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            specification << [
                    { root, query, cb ->
                        root.get("amount").in(100, 200)
                    } as Specification,
                    { root, query, cb ->
                        root.get("amount").in(100, 200).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get("amount")).value(100).value(200)
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get("amount")).value(100).value(200).not()
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get("amount").in([parameter] as Expression<?>[])
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get("amount").in([parameter] as Expression<?>[]).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.between(root.get("enabled"), true, false)
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        cb.between(root.get("amount"), parameter, parameter)
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        null
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        query.orderBy(cb.desc(root.get("amount")), cb.asc(root.get("budget")))
                        null
                    } as Specification,
                    { root, query, cb ->
                        def pred1 = cb.or(root.get("enabled"), root.get("enabled2"))
                        def pred2 = cb.or(pred1, cb.equal(root.get("amount"), 100))
                        def andPred = cb.and(cb.equal(root.get("budget"), 200), pred2)
                        andPred
                    } as Specification,
                    { root, query, cb ->
                        cb.equal(cb.lower(cb.upper(root.get("name"))), "Denis")
                    } as Specification,
                    { root, query, cb ->
                        cb.equal(cb.lower(cb.upper(root.get("name"))), cb.lower(cb.literal("Denis")))
                    } as Specification
            ]
            expectedWhereQuery << [
                    '(test_."amount" IN (?,?))',
                    '(test_."amount" NOT IN (?,?))',
                    '(test_."amount" IN (?,?))',
                    '(test_."amount" NOT IN (?,?))',
                    '(test_."amount" IN (?))',
                    '(test_."amount" NOT IN (?))',
                    '((test_."enabled" >= ? AND test_."enabled" <= ?))',
                    '((test_."amount" >= ? AND test_."amount" <= ?))',
                    '(test_."enabled" = TRUE)',
                    '(test_."enabled" = TRUE) ORDER BY test_."amount" DESC,test_."budget" ASC',
                    '(test_."budget" = ? AND (test_."enabled" = TRUE OR test_."enabled2" = TRUE OR test_."amount" = ?))',
                    '(LOWER(UPPER(test_."name")) = ?)',
                    '(LOWER(UPPER(test_."name")) = LOWER(?))'
            ]
    }


    @Unroll
    void "test delete"(DeleteSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaDelete)
            def predicate = specification.toPredicate(entityRoot, criteriaDelete, criteriaBuilder)
            if (predicate) {
                criteriaDelete.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaDelete)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.ge(root.get("amount"), 1000)
                    } as DeleteSpecification,
            ]
            expectedQuery << [
                    'DELETE  FROM "test"  WHERE ("amount" >= ?)',
            ]
    }

    @Unroll
    void "test update"(UpdateSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaUpdate)
            def predicate = specification.toPredicate(entityRoot, criteriaUpdate, criteriaBuilder)
            if (predicate) {
                criteriaUpdate.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaUpdate)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        query.set("name", "ABC")
                        query.set(root.get("amount"), 123)
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", cb.parameter(String))
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedQuery << [
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= ?)',
            ]
    }

    @Unroll
    void "test properties not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateProps(predicate, entityRoot, property1, property2).not())
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '(test_."enabled" != test_."enabled2")'
            "enabled" | "enabled2" | "notEqual"             | '(test_."enabled" = test_."enabled2")'
            "enabled" | "enabled2" | "greaterThan"          | '(NOT(test_."enabled" > test_."enabled2"))'
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '(NOT(test_."enabled" >= test_."enabled2"))'
            "enabled" | "enabled2" | "lessThan"             | '(NOT(test_."enabled" < test_."enabled2"))'
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '(NOT(test_."enabled" <= test_."enabled2"))'
            "amount"  | "budget"   | "gt"                   | '(NOT(test_."amount" > test_."budget"))'
            "amount"  | "budget"   | "ge"                   | '(NOT(test_."amount" >= test_."budget"))'
            "amount"  | "budget"   | "lt"                   | '(NOT(test_."amount" < test_."budget"))'
            "amount"  | "budget"   | "le"                   | '(NOT(test_."amount" <= test_."budget"))'
    }

    @Unroll
    void "test property value #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateValue(predicate, entityRoot, property1, value))
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '(test_."enabled" = ?)'
            "enabled" | true                    | "notEqual"             | '(test_."enabled" != ?)'
            "enabled" | true                    | "greaterThan"          | '(test_."enabled" > ?)'
            "enabled" | true                    | "greaterThanOrEqualTo" | '(test_."enabled" >= ?)'
            "enabled" | true                    | "lessThan"             | '(test_."enabled" < ?)'
            "enabled" | true                    | "lessThanOrEqualTo"    | '(test_."enabled" <= ?)'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '(test_."amount" > ?)'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '(test_."amount" >= ?)'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '(test_."amount" < ?)'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '(test_."amount" <= ?)'
    }

    @Unroll
    void "test property value not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateValue(predicate, entityRoot, property1, value).not())
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '(test_."enabled" != ?)'
            "enabled" | true                    | "notEqual"             | '(test_."enabled" = ?)'
            "enabled" | true                    | "greaterThan"          | '(NOT(test_."enabled" > ?))'
            "enabled" | true                    | "greaterThanOrEqualTo" | '(NOT(test_."enabled" >= ?))'
            "enabled" | true                    | "lessThan"             | '(NOT(test_."enabled" < ?))'
            "enabled" | true                    | "lessThanOrEqualTo"    | '(NOT(test_."enabled" <= ?))'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '(NOT(test_."amount" > ?))'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '(NOT(test_."amount" >= ?))'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '(NOT(test_."amount" < ?))'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '(NOT(test_."amount" <= ?))'
    }

}

class SimpleEntityType<T> implements EntityType<T> {
    private final Class<T> javaType

    SimpleEntityType(Class<T> javaType) {
        this.javaType = javaType
    }

    @Override
    String getName() {
        javaType.simpleName
    }

    @Override
    PersistenceType getPersistenceType() {
        PersistenceType.ENTITY
    }

    @Override
    Class<T> getJavaType() {
        javaType
    }

    @Override
    Bindable.BindableType getBindableType() {
        Bindable.BindableType.ENTITY_TYPE
    }

    @Override
    Class<T> getBindableJavaType() {
        javaType
    }

    @Override
    Type<?> getIdType() {
        throw new UnsupportedOperationException()
    }

    @Override
    def <Y> SingularAttribute<? super T, Y> getId(Class<Y> type) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <Y> SingularAttribute<T, Y> getDeclaredId(Class<Y> type) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <Y> SingularAttribute<? super T, Y> getVersion(Class<Y> type) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <Y> SingularAttribute<T, Y> getDeclaredVersion(Class<Y> type) {
        throw new UnsupportedOperationException()
    }

    @Override
    IdentifiableType<? super T> getSupertype() {
        null
    }

    @Override
    boolean hasSingleIdAttribute() {
        true
    }

    @Override
    boolean hasVersionAttribute() {
        false
    }

    @Override
    Set<SingularAttribute<? super T, ?>> getIdClassAttributes() {
        [] as Set
    }

    @Override
    Attribute<? super T, ?> getAttribute(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    Set<Attribute<? super T, ?>> getAttributes() {
        [] as Set
    }

    @Override
    Attribute<T, ?> getDeclaredAttribute(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    Set<Attribute<T, ?>> getDeclaredAttributes() {
        [] as Set
    }

    @Override
    SingularAttribute<? super T, ?> getSingularAttribute(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <Y> SingularAttribute<? super T, Y> getSingularAttribute(String name, Class<Y> type) {
        throw new UnsupportedOperationException()
    }

    @Override
    SingularAttribute<T, ?> getDeclaredSingularAttribute(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <Y> SingularAttribute<T, Y> getDeclaredSingularAttribute(String name, Class<Y> type) {
        throw new UnsupportedOperationException()
    }

    @Override
    Set<SingularAttribute<? super T, ?>> getSingularAttributes() {
        [] as Set
    }

    @Override
    Set<SingularAttribute<T, ?>> getDeclaredSingularAttributes() {
        [] as Set
    }

    @Override
    CollectionAttribute<? super T, ?> getCollection(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <E> CollectionAttribute<? super T, E> getCollection(String name, Class<E> elementType) {
        throw new UnsupportedOperationException()
    }

    @Override
    CollectionAttribute<T, ?> getDeclaredCollection(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <E> CollectionAttribute<T, E> getDeclaredCollection(String name, Class<E> elementType) {
        throw new UnsupportedOperationException()
    }

    @Override
    SetAttribute<? super T, ?> getSet(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <E> SetAttribute<? super T, E> getSet(String name, Class<E> elementType) {
        throw new UnsupportedOperationException()
    }

    @Override
    SetAttribute<T, ?> getDeclaredSet(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <E> SetAttribute<T, E> getDeclaredSet(String name, Class<E> elementType) {
        throw new UnsupportedOperationException()
    }

    @Override
    ListAttribute<? super T, ?> getList(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <E> ListAttribute<? super T, E> getList(String name, Class<E> elementType) {
        throw new UnsupportedOperationException()
    }

    @Override
    ListAttribute<T, ?> getDeclaredList(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <E> ListAttribute<T, E> getDeclaredList(String name, Class<E> elementType) {
        throw new UnsupportedOperationException()
    }

    @Override
    MapAttribute<? super T, ?, ?> getMap(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <K, V> MapAttribute<? super T, K, V> getMap(String name, Class<K> keyType, Class<V> valueType) {
        throw new UnsupportedOperationException()
    }

    @Override
    MapAttribute<T, ?, ?> getDeclaredMap(String name) {
        throw new UnsupportedOperationException()
    }

    @Override
    def <K, V> MapAttribute<T, K, V> getDeclaredMap(String name, Class<K> keyType, Class<V> valueType) {
        throw new UnsupportedOperationException()
    }

    @Override
    Set<PluralAttribute<? super T, ?, ?>> getPluralAttributes() {
        [] as Set
    }

    @Override
    Set<PluralAttribute<T, ?, ?>> getDeclaredPluralAttributes() {
        [] as Set
    }
}

class SimplePluralAttribute<X, C, E> implements PluralAttribute<X, C, E> {
    private final String name

    SimplePluralAttribute(String name) {
        this.name = name
    }

    @Override
    String getName() {
        name
    }

    @Override
    PersistentAttributeType getPersistentAttributeType() {
        PersistentAttributeType.ONE_TO_MANY
    }

    @Override
    ManagedType<X> getDeclaringType() {
        throw new UnsupportedOperationException()
    }

    @Override
    Class<C> getJavaType() {
        throw new UnsupportedOperationException()
    }

    @Override
    java.lang.reflect.Member getJavaMember() {
        throw new UnsupportedOperationException()
    }

    @Override
    boolean isAssociation() {
        true
    }

    @Override
    boolean isCollection() {
        true
    }

    @Override
    CollectionType getCollectionType() {
        CollectionType.LIST
    }

    @Override
    Type<E> getElementType() {
        throw new UnsupportedOperationException()
    }

    @Override
    Bindable.BindableType getBindableType() {
        Bindable.BindableType.PLURAL_ATTRIBUTE
    }

    @Override
    Class<E> getBindableJavaType() {
        throw new UnsupportedOperationException()
    }
}
