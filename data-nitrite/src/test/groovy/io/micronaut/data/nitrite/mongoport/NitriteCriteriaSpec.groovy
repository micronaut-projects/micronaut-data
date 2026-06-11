package io.micronaut.data.nitrite.mongoport

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.document.tck.entities.Settlement
import io.micronaut.data.document.tck.entities.SettlementPk
import io.micronaut.data.model.jpa.criteria.*
import io.micronaut.data.nitrite.model.CompositeFkChild
import io.micronaut.data.nitrite.model.CompositeIdEntity
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder
import io.micronaut.data.nitrite.mongoport.entities.NitriteTestEntity
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.persistence.criteria.*
import org.jspecify.annotations.NonNull
import spock.lang.Specification
import spock.lang.Unroll

class NitriteCriteriaSpec extends Specification {

    PersistentEntityCriteriaBuilder criteriaBuilder

    PersistentEntityCriteriaQuery criteriaQuery

    PersistentEntityCriteriaDelete criteriaDelete

    PersistentEntityCriteriaUpdate criteriaUpdate

    void setup() {
        criteriaBuilder = new RuntimeCriteriaBuilder()
        criteriaQuery = criteriaBuilder.createQuery()
        criteriaDelete = criteriaBuilder.createCriteriaDelete(NitriteTestEntity)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(NitriteTestEntity)
    }

    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(NitriteTestEntity)
    }

    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(NitriteTestEntity)
    }

    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(NitriteTestEntity)
    }

    void "test embedded predicate"(QuerySpecification specification) {
        given:
            PersistentEntityCriteriaQuery criteriaQuery = criteriaBuilder.createQuery()
            PersistentEntityRoot entityRoot = criteriaQuery.from(Settlement)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.equal(root.get("id"), cb.parameter(SettlementPk))
                    } as QuerySpecification
            ]
            expectedWhereQuery << [
                    '''{'_id.code':{$eq:{$mn_qp:0}},'_id.code_id':{$eq:{$mn_qp:1}},'_id.county._id.id':{$eq:{$mn_qp:2}},'_id.county._id.state_id._id':{$eq:{$mn_qp:3}}}''',
            ]
    }

    @Unroll
    void "test criteria predicate"(QuerySpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.between(root.get("enabled"), true, false)
                    } as QuerySpecification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        cb.between(root.get("amount"), parameter, parameter)
                    } as QuerySpecification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        null
                    } as QuerySpecification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        query.orderBy(cb.desc(root.get("amount")), cb.asc(root.get("budget")))
                        null
                    } as QuerySpecification,
                    { root, query, cb ->
                        cb.isTrue(root.get("enabled"))
                    } as QuerySpecification,
                    { root, query, cb ->
                        cb.and(cb.isTrue(root.get("enabled")), cb.isTrue(root.get("enabled")))
                    } as QuerySpecification,
                    { root, query, cb ->
                        root.get("name").in("A", "B", "C")
                    } as QuerySpecification,
                    { root, query, cb ->
                        cb.in(root.get("name")).value("A").value("B").value("C")
                    } as QuerySpecification,
                    { root, query, cb ->
                        root.get("name").in("A", "B", "C").not()
                    } as QuerySpecification,
                    {
                        root, query, cb ->
                            def colors = Arrays.asList("red", "white")
                            def parameter = cb.literal(colors)
                            ((PersistentEntityCriteriaBuilder)cb).arrayContains(root.get("colors"), parameter)
                    } as QuerySpecification,
            ]
            expectedWhereQuery << [
                    '{enabled:{$between:[{$mn_qp:0},{$mn_qp:1}]}}',
                    '{amount:{$between:[{$mn_qp:0},{$mn_qp:1}]}}',
                    '{enabled:{$eq:true}}',
                    '[{$match:{enabled:{$eq:true}}},{$sort:{amount:-1,budget:1}}]',
                    '{enabled:{$eq:true}}',
                    '{$and:[{enabled:{$eq:true}},{enabled:{$eq:true}}]}',
                    '''{name:{$in:[{$mn_qp:0},{$mn_qp:1},{$mn_qp:2}]}}''',
                    '''{name:{$in:[{$mn_qp:0},{$mn_qp:1},{$mn_qp:2}]}}''',
                    '''{name:{$nin:[{$mn_qp:0},{$mn_qp:1},{$mn_qp:2}]}}''',
                    '{colors:{$all:[{$mn_qp:0}]}}'
            ]
    }

    @Unroll
    void "test extended string predicate #desc produces #expected"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def builder = (PersistentEntityCriteriaBuilder) criteriaBuilder
            criteriaQuery.where(build.call(entityRoot, builder))
            String predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expected

        where:
            desc                       | build                                                                          | expected
            "startsWith"               | { r, cb -> cb.startsWithString(r.get("name"), cb.literal("Al")) }              | regex("^", ".*", false)
            "startsWithIgnoreCase"     | { r, cb -> cb.startsWithStringIgnoreCase(r.get("name"), cb.literal("Al")) }    | regex("^", ".*", true)
            "endsWith"                 | { r, cb -> cb.endingWithString(r.get("name"), cb.literal("Al")) }              | regex(".*", "\$", false)
            "endsWithIgnoreCase"       | { r, cb -> cb.endingWithStringIgnoreCase(r.get("name"), cb.literal("Al")) }    | regex(".*", "\$", true)
            "contains"                 | { r, cb -> cb.containsString(r.get("name"), cb.literal("Al")) }                | regex(".*", ".*", false)
            "containsIgnoreCase"       | { r, cb -> cb.containsStringIgnoreCase(r.get("name"), cb.literal("Al")) }      | regex(".*", ".*", true)
            "ilike"                    | { r, cb -> cb.ilike(r.get("name"), cb.literal("Al%")) }                        | regex(".*", ".*", true)
            "regex"                    | { r, cb -> cb.regex(r.get("name"), cb.literal("^Al.*")) }                      | '''{name:{$regex:{$mn_qp:0}}}'''
    }

    void "test equality on the id field"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(entityRoot.id(), criteriaBuilder.literal("X")))

        expect:
            getQuery(criteriaQuery) == '''{id:{$eq:{$mn_qp:0}}}'''
    }

    void "test id equals via IdExpression covers visitIdEquals"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            // entityRoot.id() returns an IdExpression; BinaryPredicate(EQUALS) routes to visitIdEquals
            def idExpr = entityRoot.id()
            criteriaQuery.where(criteriaBuilder.equal(idExpr, criteriaBuilder.parameter(String)))

        expect:
            getQuery(criteriaQuery) == '''{id:{$eq:{$mn_qp:0}}}'''
    }

    void "test in with empty collection produces impossible condition"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(entityRoot.get("name").in(Collections.emptyList()))
        expect:
            getQuery(criteriaQuery) == '''{_id:{$eq:null}}'''
    }

    void "test not-in with empty collection negates the impossible condition"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(entityRoot.get("name").in(Collections.emptyList()).not())
        expect:
            getQuery(criteriaQuery) == '''{_id:{$not:{$eq:null}}}'''
    }

    void "test regexp with parameter expression"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def builder = (PersistentEntityCriteriaBuilder) criteriaBuilder
            criteriaQuery.where(builder.regex(entityRoot.get("name"), criteriaBuilder.parameter(String)))
        expect:
            getQuery(criteriaQuery) == '''{name:{$regex:{$mn_qp:0}}}'''
    }

    void "test arrayContains with single non-iterable value"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def builder = (PersistentEntityCriteriaBuilder) criteriaBuilder
            criteriaQuery.where(builder.arrayContains(entityRoot.get("colors"), criteriaBuilder.literal("red")))
        expect:
            getQuery(criteriaQuery) == '''{colors:{$all:[{$mn_qp:0}]}}'''
    }

    void "test composite foreign key join builds composite \$lookup"() {
        given:
            PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
            PersistentEntityRoot root = query.from(CompositeFkChild)
            // Referencing the join registers a JoinPath, so buildSelect -> addLookups walks the
            // association, finds >1 @JoinColumn and emits a composite $lookup via the list-based
            // lookup overload (localField/foreignField arrays from the @JoinColumn name/ref names).
            def join = root.join("parent")
            query.where(criteriaBuilder.equal(join.get("tenantId"), criteriaBuilder.literal("t1")))
            String pipeline = getQuery(query)

        expect:
            pipeline.contains('$lookup')
            pipeline.contains('fk_tenant_id')
            pipeline.contains('fk_ref_id')
            pipeline.contains('tenantId')
            pipeline.contains('refId')
    }

    void "test appendOperatorExpression with PROD expression throws"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def prod = criteriaBuilder.prod(entityRoot.get("amount"), entityRoot.get("budget"))
            criteriaQuery.where(criteriaBuilder.equal(prod, criteriaBuilder.literal(100)))
        when:
            getQuery(criteriaQuery)
        then:
            thrown(UnsupportedOperationException)
    }

    void "test appendOperatorExpression with LENGTH expression throws"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def length = criteriaBuilder.length(entityRoot.get("name"))
            criteriaQuery.where(criteriaBuilder.equal(length, criteriaBuilder.literal(5)))
        when:
            getQuery(criteriaQuery)
        then:
            thrown(UnsupportedOperationException)
    }

    void "test equalStringIgnoreCase covers visitEquals ignoreCase branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def builder = (PersistentEntityCriteriaBuilder) criteriaBuilder
            criteriaQuery.where(builder.equalStringIgnoreCase(entityRoot.get("name"), criteriaBuilder.literal("Al")))

        expect:
            getQuery(criteriaQuery) == '''{name:{$regex:'(?i).*$mn_qp:0.*'}}'''
    }

    void "test notEqualStringIgnoreCase covers visitNotEquals ignoreCase branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def builder = (PersistentEntityCriteriaBuilder) criteriaBuilder
            criteriaQuery.where(builder.notEqualStringIgnoreCase(entityRoot.get("name"), criteriaBuilder.literal("Al")))

        expect:
            getQuery(criteriaQuery) == '''{name:{$not:{$regex:'(?i).*$mn_qp:0.*'}}}'''
    }

    void "test in with collection parameter covers visitIn BindingParameter branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(entityRoot.get("name").in(criteriaBuilder.parameter(Collection)))

        expect:
            getQuery(criteriaQuery) == '''{name:{$in:['$mn_qp:0']}}'''
    }

    void "test isNull on association covers getFieldNameForNullCheck non-embedded branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.isNull(entityRoot.get("manyToOneOther")))

        expect:
            getQuery(criteriaQuery) == '''{many_to_one_other_id:{$eq:null}}'''
    }

    void "test isNotNull on association covers getFieldNameForNullCheck non-embedded branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.isNotNull(entityRoot.get("manyToOneOther")))

        expect:
            getQuery(criteriaQuery) == '''{many_to_one_other_id:{$ne:null}}'''
    }

    // cb.literal(...) is bound as a parameter ($mn_qp:N) by RuntimeCriteriaBuilder, so these
    // exercise handleRegexExpression's parameter branch (the literal branch is compile-time only).
    private static String regex(String prefix, String suffix, boolean ignoreCase) {
        String ci = ignoreCase ? "(?i)" : ""
        return "{name:{\$regex:'" + ci + prefix + "\$mn_qp:0" + suffix + "'}}"
    }

    @Unroll
    void "test joins"(QuerySpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String q = getQuery(criteriaQuery)

        expect:
            q == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        def othersJoin = root.join("others")
                        cb.and(cb.equal(root.get("amount"), othersJoin.get("amount")))
                    } as QuerySpecification,

                    { root, query, cb ->
                        def othersJoin = root.join("others")
                        def simpleJoin = othersJoin.join("simple")
                        cb.and(
                                cb.equal(root.get("amount"), othersJoin.get("amount")),
                                cb.equal(root.get("amount"), simpleJoin.get("amount")),
                        )
                    } as QuerySpecification,
                    { root, query, cb ->
                        def oneOthersJoin = root.join("oneOther")
                        cb.equal(oneOthersJoin.get("name"), "xyz")
                    } as QuerySpecification,
                    { root, query, cb ->
                        def oneOthersJoin = root.join("manyToOneOther")
                        cb.equal(oneOthersJoin.get("name"), "xyz")
                    } as QuerySpecification,
                    { root, query, cb ->
                        def oneOthersJoin = root.join("oneOther")
                        cb.isTrue(oneOthersJoin.get("enabled"))
                    } as QuerySpecification
            ]
            expectedQuery << [
                    '''[{$lookup:{from:'nitrite_other',localField:'_id',foreignField:'test_id',as:'others'}},{$match:{$expr:{$eq:['$amount','$others.amount']}}}]''',
                    '''[{$lookup:{from:'nitrite_other',localField:'_id',foreignField:'test_id',pipeline:[{$lookup:{from:'nitrite_simple',localField:'simple_id',foreignField:'_id',as:'simple'}},{$unwind:{path:'$simple',preserveNullAndEmptyArrays:true}}],as:'others'}},{$match:{$and:[{$expr:{$eq:['$amount','$others.amount']}},{$expr:{$eq:['$amount','$others.simple.amount']}}]}}]''',
                    '''[{$lookup:{from:'nitrite_other',localField:'one_other_id',foreignField:'_id',as:'oneOther'}},{$unwind:{path:'$oneOther',preserveNullAndEmptyArrays:true}},{$match:{'oneOther.name':{$eq:{$mn_qp:0}}}}]''',
                    '''[{$lookup:{from:'nitrite_other',localField:'many_to_one_other_id',foreignField:'_id',as:'manyToOneOther'}},{$unwind:{path:'$manyToOneOther',preserveNullAndEmptyArrays:true}},{$match:{'manyToOneOther.name':{$eq:{$mn_qp:0}}}}]''',
                    '''[{$lookup:{from:'nitrite_other',localField:'one_other_id',foreignField:'_id',as:'oneOther'}},{$unwind:{path:'$oneOther',preserveNullAndEmptyArrays:true}},{$match:{'one_other_id.enabled':{$eq:true}}}]'''
            ]
    }

    @Unroll
    void "test projection #projection"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(criteriaBuilder."$projection"(entityRoot.get(property)))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property | projection | expectedWhereQuery
            "age"    | "max"      | '''[{$group:{age:{$max:'$age'},_id:null}}]'''
            "age"    | "min"      | '''[{$group:{age:{$min:'$age'},_id:null}}]'''
            "age"    | "avg"      | '''[{$group:{age:{$avg:'$age'},_id:null}}]'''
            "age"    | "sum"      | '''[{$group:{age:{$sum:'$age'},_id:null}}]'''
    }

    void "test count"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(criteriaBuilder.count(entityRoot))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == '''[{$count:'result'}]'''
    }

    @Unroll
    void "test delete"(DeleteSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaDelete)
            def predicate = specification.toPredicate(entityRoot, criteriaDelete, criteriaBuilder)
            if (predicate) {
                criteriaDelete.where(predicate)
            }
            String predicateQuery = getQuery(criteriaDelete)

        expect:
            predicateQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.ge(root.get("amount"), 1000)
                    } as DeleteSpecification,
            ]
            expectedQuery << [
                    '''{amount:{$gte:{$mn_qp:0}}}''',
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
            String predicateQuery = getQuery(criteriaUpdate)
            String updateQuery = getUpdateQuery(criteriaUpdate)

        expect:
            predicateQuery == expectedPredicateQuery
            updateQuery == expectedUpdateQuery
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
                        cb.lessThan(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.le(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedPredicateQuery << [
                    '''{amount:{$gte:{$mn_qp:0}}}''',
                    '''{amount:{$lt:{$mn_qp:0}}}''',
                    '''{amount:{$lte:{$mn_qp:0}}}''',
            ]
            expectedUpdateQuery << [
                    '''{$set:{name:{$mn_qp:1},amount:{$mn_qp:2}}}''',
                    '''{$set:{name:{$mn_qp:1},amount:{$mn_qp:2}}}''',
                    '''{$set:{name:{$mn_qp:1},amount:{$mn_qp:2}}}''',
            ]
    }

    @Unroll
    void "test #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property)))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property   | predicate          | expectedWhereQuery
            "enabled"  | "isTrue"           | '{enabled:{$eq:true}}'
            "enabled2" | "isTrue"           | '{enabled2:{$eq:true}}'
            "enabled"  | "isFalse"          | '{enabled:{$eq:false}}'
            "enabled2" | "isFalse"          | '{enabled2:{$eq:false}}'
            "enabled"  | "isNull"           | '{enabled:{$eq:null}}'
            "enabled2" | "isNull"           | '{enabled2:{$eq:null}}'
            "enabled"  | "isNotNull"        | '{enabled:{$ne:null}}'
            "enabled2" | "isNotNull"        | '{enabled2:{$ne:null}}'
            "name"     | "isNotNull"        | '{name:{$ne:null}}'
            "name"     | "isEmptyString"    | '''{$or:[{name:{$eq:''}},{name:{$exists:false}}]}'''
            "name"     | "isNotEmptyString" | '''{$and:[{name:{$ne:''}},{name:{$exists:true}}]}'''
    }

    @Unroll
    void "test not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property)).not())
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property   | predicate   | expectedWhereQuery
            "enabled"  | "isTrue"    | '{enabled:{$eq:false}}'
            "enabled2" | "isTrue"    | '{enabled2:{$eq:false}}'
            "enabled"  | "isFalse"   | '{enabled:{$eq:true}}'
            "enabled2" | "isFalse"   | '{enabled2:{$eq:true}}'
            "enabled"  | "isNull"    | '{enabled:{$ne:null}}'
            "enabled2" | "isNull"    | '{enabled2:{$ne:null}}'
            "enabled"  | "isNotNull" | '{enabled:{$eq:null}}'
            "enabled2" | "isNotNull" | '{enabled2:{$eq:null}}'
            "name"     | "isNotNull" | '{name:{$eq:null}}'
    }

    @Unroll
    void "test properties #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), entityRoot.get(property2)))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '''{$expr:{$eq:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "notEqual"             | '''{$expr:{$ne:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "greaterThan"          | '''{$expr:{$gt:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '''{$expr:{$gte:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "lessThan"             | '''{$expr:{$lt:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '''{$expr:{$lte:['$enabled','$enabled2']}}'''
            "amount"  | "budget"   | "gt"                   | '''{$expr:{$gt:['$amount','$budget']}}'''
            "amount"  | "budget"   | "ge"                   | '''{$expr:{$gte:['$amount','$budget']}}'''
            "amount"  | "budget"   | "lt"                   | '''{$expr:{$lt:['$amount','$budget']}}'''
            "amount"  | "budget"   | "le"                   | '''{$expr:{$lte:['$amount','$budget']}}'''
    }

    @Unroll
    void "test properties not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), entityRoot.get(property2)).not())
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | property2  | predicate              | expectedWhereQuery
            "enabled" | "enabled2" | "equal"                | '''{$expr:{$ne:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "notEqual"             | '''{$expr:{$eq:['$enabled','$enabled2']}}'''
            "enabled" | "enabled2" | "greaterThan"          | '''{$expr:{$not:{$gt:['$enabled','$enabled2']}}}'''
            "enabled" | "enabled2" | "greaterThanOrEqualTo" | '''{$expr:{$not:{$gte:['$enabled','$enabled2']}}}'''
            "enabled" | "enabled2" | "lessThan"             | '''{$expr:{$not:{$lt:['$enabled','$enabled2']}}}'''
            "enabled" | "enabled2" | "lessThanOrEqualTo"    | '''{$expr:{$not:{$lte:['$enabled','$enabled2']}}}'''
            "amount"  | "budget"   | "gt"                   | '''{$expr:{$not:{$gt:['$amount','$budget']}}}'''
            "amount"  | "budget"   | "ge"                   | '''{$expr:{$not:{$gte:['$amount','$budget']}}}'''
            "amount"  | "budget"   | "lt"                   | '''{$expr:{$not:{$lt:['$amount','$budget']}}}'''
            "amount"  | "budget"   | "le"                   | '''{$expr:{$not:{$lte:['$amount','$budget']}}}'''
    }

    @Unroll
    void "test property value #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value))
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '{enabled:{$eq:{$mn_qp:0}}}'
            "enabled" | true                    | "notEqual"             | '{enabled:{$ne:{$mn_qp:0}}}'
            "enabled" | true                    | "greaterThan"          | '{enabled:{$gt:{$mn_qp:0}}}'
            "enabled" | true                    | "greaterThanOrEqualTo" | '{enabled:{$gte:{$mn_qp:0}}}'
            "enabled" | true                    | "lessThan"             | '{enabled:{$lt:{$mn_qp:0}}}'
            "enabled" | true                    | "lessThanOrEqualTo"    | '{enabled:{$lte:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '{amount:{$gt:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '{amount:{$gte:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '{amount:{$lt:{$mn_qp:0}}}'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '{amount:{$lte:{$mn_qp:0}}}'
    }

    @Unroll
    void "test property value not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder."$predicate"(entityRoot.get(property1), value).not())
            def predicateQuery = getQuery(criteriaQuery)

        expect:
            predicateQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '{enabled:{$ne:{$mn_qp:0}}}'
            "enabled" | true                    | "notEqual"             | '{enabled:{$eq:{$mn_qp:0}}}'
            "enabled" | true                    | "greaterThan"          | '{enabled:{$not:{$gt:{$mn_qp:0}}}}'
            "enabled" | true                    | "greaterThanOrEqualTo" | '{enabled:{$not:{$gte:{$mn_qp:0}}}}'
            "enabled" | true                    | "lessThan"             | '{enabled:{$not:{$lt:{$mn_qp:0}}}}'
            "enabled" | true                    | "lessThanOrEqualTo"    | '{enabled:{$not:{$lte:{$mn_qp:0}}}}'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '{amount:{$not:{$gt:{$mn_qp:0}}}}'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '{amount:{$not:{$gte:{$mn_qp:0}}}}'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '{amount:{$not:{$lt:{$mn_qp:0}}}}'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '{amount:{$not:{$lte:{$mn_qp:0}}}}'
    }

    // -----------------------------------------------------------------------
    // Phase 2: Error-path and edge-case coverage (NitritePredicateVisitor)
    // -----------------------------------------------------------------------

    void "test id equal on composite identity entity throws"() {
        given:
            criteriaQuery = criteriaBuilder.createQuery()
            def root = criteriaQuery.from(CompositeIdEntity)
            criteriaQuery.where(criteriaBuilder.equal(root.id(), criteriaBuilder.literal("someId")))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(IllegalStateException)
            e.message == "Composite ID not supported!"
    }

    void "test prod in equality throws"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(
                criteriaBuilder.prod(root.get("amount"), criteriaBuilder.literal(BigDecimal.TEN)),
                criteriaBuilder.literal(BigDecimal.ONE)
            ))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(UnsupportedOperationException)
            e.message.contains("multiply")
    }

    void "test sum in equality throws"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(
                criteriaBuilder.sum(root.get("amount"), criteriaBuilder.literal(BigDecimal.TEN)),
                criteriaBuilder.literal(BigDecimal.ONE)
            ))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(IllegalStateException)
            e.message.contains("SUM")
    }

    void "test diff in equality throws"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(
                criteriaBuilder.diff(root.get("amount"), criteriaBuilder.literal(BigDecimal.TEN)),
                criteriaBuilder.literal(BigDecimal.ONE)
            ))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(IllegalStateException)
            e.message.contains("DIFF")
    }

    void "test lower in equality throws"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(
                criteriaBuilder.lower(root.get("name")),
                criteriaBuilder.literal("test")
            ))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(IllegalStateException)
            e.message.contains("LOWER")
    }

    void "test upper in equality throws"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(
                criteriaBuilder.upper(root.get("name")),
                criteriaBuilder.literal("test")
            ))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(IllegalStateException)
            e.message.contains("UPPER")
    }

    void "test length in equality throws"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.equal(
                criteriaBuilder.length(root.get("name")),
                criteriaBuilder.literal(5)
            ))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(UnsupportedOperationException)
            e.message.contains("length")
    }

    void "test and with single isTrue covers conjunction single-predicate path"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.isTrue(root.get("enabled"))))

        expect:
            getQuery(criteriaQuery) == '{enabled:{$eq:true}}'
    }

    void "test isNull on EmbeddedId covers getFieldNameForNullCheck embedded branch"() {
        given:
            PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
            PersistentEntityRoot root = query.from(Settlement)
            query.where(criteriaBuilder.isNull(root.get("id")))
            String result = getQuery(query)

        expect:
            result.contains("'id.code'")
            result.contains('{$eq:null}')
    }

    void "test isNotNull on EmbeddedId covers getFieldNameForNullCheck embedded branch"() {
        given:
            PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
            PersistentEntityRoot root = query.from(Settlement)
            query.where(criteriaBuilder.isNotNull(root.get("id")))
            String result = getQuery(query)

        expect:
            result.contains("'id.code'")
            result.contains('{$ne:null}')
    }

    void "test negated between covers lambda visitInBetween negated branch"() {
        given:
            def root = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.between(root.get("enabled"), true, false).not())

        expect:
            getQuery(criteriaQuery) == '{enabled:{$not:{$between:[{$mn_qp:0},{$mn_qp:1}]}}}'
    }

    void "test containsString with non-property-path left covers handleRegexExpression early return"() {
        given:
            createRoot(criteriaQuery)
            def builder = (PersistentEntityCriteriaBuilder) criteriaBuilder
            criteriaQuery.where(builder.containsString(criteriaBuilder.literal("someField"), criteriaBuilder.literal("Al")))

        expect:
            getQuery(criteriaQuery) == '{}'
    }

    void "test exists subquery throws UnsupportedOperationException"() {
        given:
            createRoot(criteriaQuery)
            def subquery = criteriaQuery.subquery(Long)
            criteriaQuery.where(criteriaBuilder.exists(subquery))

        when:
            getQuery(criteriaQuery)

        then:
            def e = thrown(UnsupportedOperationException)
            e.message == 'NitriteDB does not support subqueries.'
    }

    void "test single-element in covers visitIn size-one literal branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(entityRoot.get("name").in("only"))

        expect:
            getQuery(criteriaQuery) == '{name:{$in:[\'$mn_qp:0\']}}'
    }

    void "test disjunction covers visitLogical or branch"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.or(
                criteriaBuilder.equal(entityRoot.get("name"), criteriaBuilder.literal("A")),
                criteriaBuilder.equal(entityRoot.get("name"), criteriaBuilder.literal("B"))
            ))

        expect:
            getQuery(criteriaQuery) == '{$or:[{name:{$eq:{$mn_qp:0}}},{name:{$eq:{$mn_qp:1}}}]}'
    }

    void "test negated conjunction of in covers NegatedPredicate nin optimization"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(criteriaBuilder.and(entityRoot.get("name").in("A", "B")).not())

        expect:
            getQuery(criteriaQuery) == '{name:{$nin:[{$mn_qp:0},{$mn_qp:1}]}}'
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private static String getQuery(PersistentEntityCriteriaQuery<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }

    private static String getQuery(PersistentEntityCriteriaDelete<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }

    private static String getQuery(PersistentEntityCriteriaUpdate<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }

    private static String getUpdateQuery(PersistentEntityCriteriaUpdate<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getUpdate()
    }

    @CompileStatic
    interface DeleteSpecification<T> {
        Predicate toPredicate(@NonNull Root<T> root, @NonNull CriteriaDelete<?> query, @NonNull CriteriaBuilder criteriaBuilder);
    }

    @CompileStatic
    interface UpdateSpecification<T> {
        Predicate toPredicate(@NonNull Root<T> root, @NonNull CriteriaUpdate<?> query, @NonNull CriteriaBuilder criteriaBuilder);
    }

}
