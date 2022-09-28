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
}
