package io.micronaut.data.document.processor

import io.micronaut.data.annotation.Query
import io.micronaut.data.document.mongo.MongoAnnotations
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.mongodb.annotation.MongoSort

class BuildMongoQuerySpec extends AbstractDataSpec {

    void "test custom method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoFindQuery;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoFindQuery(\"$customQuery\")
    Book queryById(String id);

}
"""
            )

        when:
            String q = TestUtils.getQuery(repository.getRequiredMethod("queryById", String))
        then:
            q == storedQuery

        where:
            customQuery             || storedQuery
            '{_id:{$eq:\\"abc\\"}}' || '{_id:{$eq:"abc"}}'
            '{_id:{$eq:123}}'       || '{_id:{$eq:123}}'
            '{_id:{$eq: :id}}'       || '{_id:{$eq: {$mn_qp:0}}}'
    }

    void "test custom method2"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends CrudRepository<Book, String> {

    @MongoFindQuery(value = \"{_id:{\$eq:123}}\", sort = \"{ title : -1 }\")
    Book queryById(String id);

}
"""
            )

            def method = repository.getRequiredMethod("queryById", String)
        when:
            String q = TestUtils.getQuery(method)
        then:
            q == '{_id:{$eq:123}}'
        when:
            String sort = method.getAnnotation(MongoSort).stringValue().get()
        then:
            sort == "{ title : -1 }"
    }

    void "test delete method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends CrudRepository<Book, String> {

    @MongoDeleteQuery(\"{_id:{\$eq:123}}\")
    void customDelete();

}
"""
            )

            def method = repository.getRequiredMethod("customDelete")
        when:
            String q = TestUtils.getQuery(method)
        then:
            q == '{_id:{$eq:123}}'
    }

    void "test query method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends CrudRepository<Book, String> {

    List<org.bson.BsonDocument> queryAll();

}
"""
            )

            def method = repository.getRequiredMethod("queryAll")
        when:
            String resultType = method.stringValue(DataMethod.NAME, DataMethod.META_MEMBER_RESULT_TYPE).get()
        then:
            resultType == 'org.bson.BsonDocument'
    }

    void "test find query method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoFindQuery(filter = \"{title:{\$eq: :t}}\", sort = \"{ title : 1 }\", project = \"{ title: 1, totalPages: 1}\", collation = \"{ locale: 'en_US', numericOrdering: true}\")
    List<Book> listBooks(String t);

}
"""
            )

            def method = repository.getRequiredMethod("listBooks", String)
        when:
            String filter = method.stringValue(Query).get()
            String sort = method.stringValue(MongoAnnotations.SORT).get()
            String project = method.stringValue(MongoAnnotations.PROJECTION).get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            sort == '{ title : 1 }'
            project == '{ title: 1, totalPages: 1}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test aggregate query method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoAggregateQuery(pipeline = \"[\$match: {title:{\$eq: :t}}, \$sort: { title : 1 }, \$project: { title: 1, totalPages: 1}]\", collation = \"{ locale: 'en_US', numericOrdering: true}\")
    List<Book> customAgg(String t);

}
"""
            )

            def method = repository.getRequiredMethod("customAgg", String)
        when:
            String pipeline = method.stringValue(Query).get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            pipeline == '[$match: {title:{$eq: {$mn_qp:0}}}, $sort: { title : 1 }, $project: { title: 1, totalPages: 1}]'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test find query method2"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
@MongoSort(\"{ title : 1 }\")
@MongoProjection(\"{ title: 1, totalPages: 1}\")
@MongoCollation(\"{ locale: 'en_US', numericOrdering: true}\")
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoFindQuery(\"{title:{\$eq: :t}}\")
    List<Book> listBooks(String t);

}
"""
            )

            def method = repository.getRequiredMethod("listBooks", String)
        when:
            String filter = method.stringValue(Query).get()
            String sort = method.stringValue(MongoAnnotations.SORT).get()
            String project = method.stringValue(MongoAnnotations.PROJECTION).get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            sort == '{ title : 1 }'
            project == '{ title: 1, totalPages: 1}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test find query method3"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
@MongoSort(\"{ title : 1 }\")
@MongoProjection(\"{ title: 1, totalPages: 1}\")
@MongoCollation(\"{ locale: 'en_US', numericOrdering: true}\")
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoFindQuery(\"{title:{\$eq: :t}, totalPages: {\$eq: :p}}\")
    List<Book> listBooks(String t, int p);

}
"""
            )

            def method = repository.getRequiredMethod("listBooks", String, int)
        when:
            String filter = method.stringValue(Query).get()
            String sort = method.stringValue(MongoAnnotations.SORT).get()
            String project = method.stringValue(MongoAnnotations.PROJECTION).get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}, totalPages: {$eq: {$mn_qp:1}}}'
            sort == '{ title : 1 }'
            project == '{ title: 1, totalPages: 1}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test delete query method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoDeleteQuery(filter = \"{title:{\$eq: :t}}\", collation = \"{ locale: 'en_US', numericOrdering: true}\")
    void customDelete(String t);

}
"""
            )

            def method = repository.getRequiredMethod("customDelete", String)
        when:
            String filter = method.stringValue(Query).get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test delete query method2"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
@MongoCollation(\"{ locale: 'en_US', numericOrdering: true}\")
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoDeleteQuery(\"{title:{\$eq: :t}}\")
    void customDelete(String t);

}
"""
            )

            def method = repository.getRequiredMethod("customDelete", String)
        when:
            String filter = method.stringValue(Query).get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test update query method"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoUpdateQuery(filter = \"{title:{\$eq: :t}}\", update = \"{\$set:{name: \\"tom\\"}}\", collation = \"{ locale: 'en_US', numericOrdering: true}\")
    List<Book> customUpdate(String t);

}
"""
            )

            def method = repository.getRequiredMethod("customUpdate", String)
        when:
            String filter = method.stringValue(Query).get()
            String update = method.stringValue(Query, "update").get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            update == '{$set:{name: \"tom\"}}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test update query method2"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
@MongoCollation(\"{ locale: 'en_US', numericOrdering: true}\")
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoUpdateQuery(filter = \"{title:{\$eq: :t}}\", update = \"{\$set:{name: \\"tom\\"}}\")
    List<Book> customUpdate(String t);

}
"""
            )

            def method = repository.getRequiredMethod("customUpdate", String)
        when:
            String filter = method.stringValue(Query).get()
            String update = method.stringValue(Query, "update").get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            update == '{$set:{name: \"tom\"}}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test update query method3"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Book;

@MongoRepository
@MongoCollation(\"{ locale: 'en_US', numericOrdering: true}\")
@MongoFilter(\"{title:{\$eq: :t}}\")
interface MyInterface2 extends GenericRepository<Book, String> {

    @MongoUpdateQuery(update = \"{\$set:{name: \\"tom\\"}}\")
    List<Book> customUpdate(String t);

}
"""
            )

            def method = repository.getRequiredMethod("customUpdate", String)
        when:
            String filter = method.stringValue(Query).get()
            String update = method.stringValue(Query, "update").get()
            String collation = method.stringValue(MongoAnnotations.COLLATION).get()
        then:
            filter == '{title:{$eq: {$mn_qp:0}}}'
            update == '{$set:{name: \"tom\"}}'
            collation == '{ locale: \'en_US\', numericOrdering: true}'
    }

    void "test find by ids method"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Person;
import java.util.Optional;

@MongoRepository
interface PersonRepository extends GenericRepository<Person, String> {

    Optional<Person> findById(String ids);

    List<Person> findByIdIn(List<String> ids);

    List<Person> findByIdNotIn(List<String> ids);

    Optional<Person> findByIdIsEmpty(String id);

    Optional<Person> findByIdEquals(String id);
}
"""
        )

            def findByIdMethod = repository.getRequiredMethod("findById", String)
            def findByIdInMethod = repository.getRequiredMethod("findByIdIn", List<String>)
            def findByIdNotInMethod = repository.getRequiredMethod("findByIdNotIn", List<String>)
            def findByIdIsEmptyMethod = repository.getRequiredMethod("findByIdIsEmpty", String)
            def findByIdEqualMethod = repository.getRequiredMethod("findByIdEquals", String)
        when:
            def findByIdQuery = findByIdMethod.getAnnotation(Query).stringValue().get()
            def findByIdInQuery = findByIdInMethod.getAnnotation(Query).stringValue().get()
            def findByIdNotInQuery = findByIdNotInMethod.getAnnotation(Query).stringValue().get()
            def findByIdIsEmptyQuery = findByIdIsEmptyMethod.getAnnotation(Query).stringValue().get()
            def findByIdEqualQuery = findByIdEqualMethod.getAnnotation(Query).stringValue().get()
        then:
            findByIdQuery == '{_id:{$eq:{$mn_qp:0}}}'
            findByIdInQuery == '{_id:{$in:[{$mn_qp:0}]}}'
            findByIdNotInQuery == '{_id:{$nin:[{$mn_qp:0}]}}'
            findByIdIsEmptyQuery == '{$or:[{_id:{$eq:\'\'}},{_id:{$exists:false}}]}'
            findByIdEqualQuery == '{_id:{$eq:{$mn_qp:0}}}'
    }

    void "test equal/not equal ignore case"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Person;
import java.util.Optional;

@MongoRepository
interface PersonRepository extends GenericRepository<Person, String> {

    Optional<Person> findByNameEqualIgnoreCase(String name);

    List<Person> findByNameNotEqualIgnoreCase(String name);

    List<Person> findByNameStartsWith(String name);

    List<Person> findByNameStartsWithIgnoreCase(String name);

    List<Person> findByNameEndsWith(String name);

    List<Person> findByNameEndsWithIgnoreCase(String name);

    List<Person> findByNameContains(String name);

    List<Person> findByNameContainsIgnoreCase(String name);
}
"""
        )

        when:
        def findByNameEqualIgnoreCaseQuery = repository.getRequiredMethod("findByNameEqualIgnoreCase", String).getAnnotation(Query).stringValue().get()
        def findByNameNotEqualIgnoreCaseQuery = repository.getRequiredMethod("findByNameEqualIgnoreCase", String).getAnnotation(Query).stringValue().get()
        def findByNameStartsWithQuery = repository.getRequiredMethod("findByNameStartsWith", String).getAnnotation(Query).stringValue().get()
        def findByNameStartsWithIgnoreCaseQuery = repository.getRequiredMethod("findByNameStartsWithIgnoreCase", String).getAnnotation(Query).stringValue().get()
        def findByNameEndsWithQuery = repository.getRequiredMethod("findByNameEndsWith", String).getAnnotation(Query).stringValue().get()
        def findByNameEndsWithIgnoreCaseQuery = repository.getRequiredMethod("findByNameEndsWithIgnoreCase", String).getAnnotation(Query).stringValue().get()
        def findByNameContainsQuery = repository.getRequiredMethod("findByNameContains", String).getAnnotation(Query).stringValue().get()
        def findByNameContainsIgnoreCaseQuery = repository.getRequiredMethod("findByNameContainsIgnoreCase", String).getAnnotation(Query).stringValue().get()
        then:
        findByNameEqualIgnoreCaseQuery == '{name:{$options:\'i\',$regex:\'^$mn_qp:0$\'}}'
        findByNameNotEqualIgnoreCaseQuery == '{name:{$options:\'i\',$regex:\'^$mn_qp:0$\'}}'
        findByNameStartsWithQuery == '{name:{$options:\'\',$regex:\'^$mn_qp:0\'}}'
        findByNameStartsWithIgnoreCaseQuery == '{name:{$options:\'i\',$regex:\'^$mn_qp:0\'}}'
        findByNameEndsWithQuery == '{name:{$options:\'\',$regex:\'$mn_qp:0$\'}}'
        findByNameEndsWithIgnoreCaseQuery == '{name:{$options:\'i\',$regex:\'$mn_qp:0$\'}}'
        findByNameContainsQuery == '{name:{$options:\'\',$regex:\'$mn_qp:0\'}}'
        findByNameContainsIgnoreCaseQuery == '{name:{$options:\'i\',$regex:\'$mn_qp:0\'}}'
    }

    void "test array contains query"() {
        given:
        def repository = buildRepository('test.DocumentRepository', """
import io.micronaut.data.mongodb.annotation.*;
import io.micronaut.data.document.tck.entities.Document;
import java.util.List;

@MongoRepository
interface DocumentRepository extends GenericRepository<Document, String> {

    List<Document> findByTagsArrayContains(String tag);

    List<Document> findByTagsArrayContains(List<String> tags);
}
"""
        )

        when:
        def findByTagsArrayContainsQuery = repository.getRequiredMethod("findByTagsArrayContains", String).getAnnotation(Query).stringValue().get()
        def findByTagsArrayContainsListQuery = repository.getRequiredMethod("findByTagsArrayContains", List<String>).getAnnotation(Query).stringValue().get()
        then:
        findByTagsArrayContainsQuery == '{tags:{$all:[{$mn_qp:0}]}}'
        findByTagsArrayContainsListQuery == '{tags:{$all:[{$mn_qp:0}]}}'
    }

    void "test query by field in embedded relation"() {
        given:
        def repository = buildRepository('test.TestRepository', """
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.mongodb.annotation.*;
import org.bson.types.ObjectId;

@MongoRepository
interface TestRepository extends GenericRepository<TestEntity, String> {

    TestEntity findByParentChildIn(List<String> children);

    TestEntity findByParentChildInList(List<String> children);

    TestEntity findByParentChildEquals(String child);

    TestEntity findByParentChildIsNotEmpty();
}

@Embeddable
class ParentObject {

    private String child;

    public String getChild() { return child; }

    public void setChild(String child) { this.child = child; }
}

@MappedEntity("test")
class TestEntity {

    @Id
    private ObjectId id;

    @Relation(value = Relation.Kind.EMBEDDED)
    private ParentObject parent;

    public ObjectId getId() { return id; }

    public void setId(ObjectId id) { this.id = id; }

    public ParentObject getParent() { return parent; }

    public void setParent(ParentObject parent) { this.parent = parent; }
}
"""
        )

        when:
        def queryIn = TestUtils.getQuery(repository.getRequiredMethod("findByParentChildIn", List<String>))
        def queryInList = TestUtils.getQuery(repository.getRequiredMethod("findByParentChildInList", List<String>))
        def queryEquals = TestUtils.getQuery(repository.getRequiredMethod("findByParentChildEquals", String))
        def queryIsNotEmpty = TestUtils.getQuery(repository.getRequiredMethod("findByParentChildIsNotEmpty"))
        then:
        queryIn == '{\'parent.child\':{$in:[{$mn_qp:0}]}}'
        queryInList == '{\'parent.child\':{$in:[{$mn_qp:0}]}}'
        queryEquals == '{\'parent.child\':{$eq:{$mn_qp:0}}}'
        queryIsNotEmpty == '{$and:[{\'parent.child\':{$ne:\'\'}},{\'parent.child\':{$exists:true}}]}'
    }

    void "test projections"() {
        given:
            def repository = buildRepository('test.PersonRepository', """

import io.micronaut.data.mongodb.annotation.*;
import java.time.LocalDate;
import io.micronaut.data.document.tck.entities.Person;
import java.util.Optional;

@MongoRepository
interface PersonRepository extends GenericRepository<Person, String> {

    LocalDate findMaxDateOfBirth();

    LocalDate findMinDateOfBirth();
}
"""
            )

        when:
            def findMaxDateOfBirthQuery = repository.getRequiredMethod("findMaxDateOfBirth").getAnnotation(Query).stringValue().get()
            def findMinDateOfBirth = repository.getRequiredMethod("findMinDateOfBirth").getAnnotation(Query).stringValue().get()

        then:
            findMaxDateOfBirthQuery == '[{$group:{dateOfBirth:{$max:\'$dateOfBirth\'},_id:null}}]'
            findMinDateOfBirth == '[{$group:{dateOfBirth:{$min:\'$dateOfBirth\'},_id:null}}]'
    }
}
