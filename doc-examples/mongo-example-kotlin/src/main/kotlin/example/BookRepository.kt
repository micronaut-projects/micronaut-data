
// tag::repository[]
package example

import io.micronaut.context.annotation.Executable
import io.micronaut.data.annotation.Id
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.mongodb.annotation.*
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId

@MongoRepository // <1>
interface BookRepository : KotlinCrudRepository<Book, ObjectId> { // <2>
// end::repository[]

    // tag::simple[]
    fun findByTitle(title: String): Book

    fun getByTitle(title: String): Book

    fun retrieveByTitle(title: String): Book
    // end::simple[]

    // tag::simple-alt[]
    // tag::repository[]
    @Executable
    fun find(title: String): Book
    // end::simple-alt[]
    // end::repository[]

    // tag::greaterthan[]
    fun findByPagesGreaterThan(pageCount: Int): List<Book>
    // end::greaterthan[]

    // tag::logical[]
    fun findByPagesGreaterThanOrTitleRegex(pageCount: Int, title: String): List<Book>
    // end::logical[]

    // tag::pageable[]
    fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    fun findByTitleRegex(title: String, pageable: Pageable): Page<Book>

    fun list(pageable: Pageable): Slice<Book>
    // end::pageable[]

    // tag::simple-projection[]
    fun findTitleByPagesGreaterThan(pageCount: Int): List<String>
    // end::simple-projection[]

    // tag::top-projection[]
    fun findTop3ByTitleRegex(title: String): List<Book>
    // end::top-projection[]

    // tag::ordering[]
    fun listOrderByTitle(): List<Book>

    fun listOrderByTitleDesc(): List<Book>
    // end::ordering[]

    // tag::save[]
    fun persist(entity: Book): Book
    // end::save[]

    // tag::save2[]
    fun persist(title: String, pages: Int): Book
    // end::save2[]

    // tag::update[]
    fun update(@Id id: ObjectId, pages: Int)

    fun update(@Id id: ObjectId, title: String)
    // end::update[]

    // tag::update2[]
    fun updateByTitle(title: String, pages: Int)
    // end::update2[]

    // tag::deleteall[]
    override fun deleteAll(): Int
    // end::deleteall[]

    // tag::deleteone[]
    fun delete(title: String)
    // end::deleteone[]

    // tag::deleteby[]
    fun deleteByTitleRegex(title: String)
    // end::deleteby[]

    // tag::dto[]
    fun findOne(title: String): BookDTO
    // end::dto[]

    // tag::custom[]
    @MongoFindQuery(filter = "{title:{\$regex: :t}}", sort = "{title: 1}")
    fun customFind(t: String): List<Book>

    @MongoAggregateQuery("[{\$match: {name:{\$regex: :t}}}, {\$sort: {name: 1}}, {\$project: {name: 1}}]")
    fun customAggregate(t: String): List<Person>

    @MongoUpdateQuery(filter = "{title:{\$regex: :t}}", update = "{\$set:{name: 'tom'}}")
    fun customUpdate(t: String): List<Book>

    @MongoDeleteQuery(filter = "{title:{\$regex: :t}}", collation = "{locale:'en_US', numericOrdering:true}")
    fun customDelete(t: String)
    // end::custom[]

// tag::repository[]
}
// end::repository[]
