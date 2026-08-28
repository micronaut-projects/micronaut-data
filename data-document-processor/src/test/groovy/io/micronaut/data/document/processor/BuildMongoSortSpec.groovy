package io.micronaut.data.document.processor

class BuildMongoSortSpec extends AbstractDataSpec {

    void "test @OrderBy null ordering builds a rank field to sort on"() {
        given:
            def repository = buildRepository('test.MongoNullOrderingRepo', """
import io.micronaut.data.annotation.Find;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.model.Sort;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.document.tck.entities.Book;
import java.util.List;

@MongoRepository
interface MongoNullOrderingRepo extends GenericRepository<Book, String> {

    @Find
    @OrderBy(value = "title", nullOrdering = Sort.Order.NullOrdering.LAST)
    List<Book> nullsLast();

    @Find
    @OrderBy(value = "title", descending = true, nullOrdering = Sort.Order.NullOrdering.FIRST)
    List<Book> descNullsFirst();

    @Find
    @OrderBy("title")
    List<Book> unspecified();
}
"""
            )

        when: "nulls last ranks null and missing values after the rest"
            String nullsLast = TestUtils.getQuery(repository.getRequiredMethod("nullsLast"))

        then:
            nullsLast == '[{$addFields:{__micronaut_nulls_0:{$cond:[{$in:[{$type:\'$title\'},[\'missing\',\'null\']]},1,0]}}},' +
                    '{$sort:{__micronaut_nulls_0:1,title:1}},' +
                    '{$unset:[\'__micronaut_nulls_0\']}]'

        when: "nulls first ranks them before the rest, independently of the sort direction"
            String descNullsFirst = TestUtils.getQuery(repository.getRequiredMethod("descNullsFirst"))

        then:
            descNullsFirst == '[{$addFields:{__micronaut_nulls_0:{$cond:[{$in:[{$type:\'$title\'},[\'missing\',\'null\']]},0,1]}}},' +
                    '{$sort:{__micronaut_nulls_0:1,title:-1}},' +
                    '{$unset:[\'__micronaut_nulls_0\']}]'

        when: "no null ordering leaves the sort untouched"
            String unspecified = TestUtils.getQuery(repository.getRequiredMethod("unspecified"))

        then:
            unspecified == '[{$sort:{title:1}}]'
    }

    void "test ordering by a path into an association uses the persisted field names"() {
        given:
            def repository = buildRepository('test.MongoNestedSortRepo', """
import io.micronaut.data.annotation.Find;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.document.tck.entities.Book;
import java.util.List;

@MongoRepository
interface MongoNestedSortRepo extends GenericRepository<Book, String> {

    @Find
    @OrderBy("author.name")
    List<Book> byAuthorName();
}
"""
            )

        expect:
            TestUtils.getQuery(repository.getRequiredMethod("byAuthorName")) ==
                    "[{\$lookup:{from:'author',localField:'author._id',foreignField:'_id',as:'author'}}," +
                    "{\$unwind:{path:'\$author',preserveNullAndEmptyArrays:true}}," +
                    "{\$sort:{'author.name':1}}]" 
    }
}
