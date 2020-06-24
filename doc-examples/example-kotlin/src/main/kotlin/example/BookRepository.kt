/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// tag::repository[]
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.model.*
import io.micronaut.data.repository.CrudRepository

@Repository // <1>
interface BookRepository : CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    fun findByTitle(title: String): Book

    fun getByTitle(title: String): Book

    fun retrieveByTitle(title: String): Book
    // end::simple[]

    // tag::simple-alt[]
    // tag::repository[]
    fun find(title: String): Book
    // end::simple-alt[]
    // end::repository[]

    // tag::greaterthan[]
    fun findByPagesGreaterThan(pageCount: Int): List<Book>
    // end::greaterthan[]

    // tag::logical[]
    fun findByPagesGreaterThanOrTitleLike(pageCount: Int, title: String): List<Book>
    // end::logical[]

    // tag::pageable[]
    fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    fun findByTitleLike(title: String, pageable: Pageable): Page<Book>

    fun list(pageable: Pageable): Slice<Book>
    // end::pageable[]

    // tag::simple-projection[]
    fun findTitleByPagesGreaterThan(pageCount: Int): List<String>
    // end::simple-projection[]

    // tag::top-projection[]
    fun findTop3ByTitleLike(title: String): List<Book>
    // end::top-projection[]

    // tag::ordering[]
    fun listOrderByTitle(): List<Book>

    fun listOrderByTitleDesc(): List<Book>
    // end::ordering[]

    // tag::explicit[]
    @Query("FROM Book b WHERE b.title = :t ORDER BY b.title")
    fun listBooks(t: String): List<Book>
    // end::explicit[]

    // tag::save[]
    fun persist(entity: Book): Book
    // end::save[]

    // tag::save2[]
    fun persist(title: String, pages: Int): Book
    // end::save2[]

    // tag::update[]
    fun update(@Id id: Long?, pages: Int)
    // end::update[]

    // tag::update2[]
    fun updateByTitle(title: String, pages: Int)
    // end::update2[]

    // tag::update3[]
    fun updatePages(@Id id: Long?, pages: Int)
    // end::update3[]

    // tag::deleteall[]
    override fun deleteAll()
    // end::deleteall[]

    // tag::deleteone[]
    fun delete(title: String)
    // end::deleteone[]

    // tag::deleteby[]
    fun deleteByTitleLike(title: String)
    // end::deleteby[]

    // tag::dto[]
    fun findOne(title: String): BookDTO
    // end::dto[]

    // tag::native[]
    @Query(value = "select * from books b where b.title like :title limit 5", nativeQuery = true)
    fun findNativeBooks(title: String): List<Book>
    // end::native[]

// tag::repository[]
}
// end::repository[]
