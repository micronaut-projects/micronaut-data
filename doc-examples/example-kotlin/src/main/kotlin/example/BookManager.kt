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
package example

import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.transaction.annotation.TransactionalEventListener
import javax.inject.Singleton
import javax.transaction.Transactional

@Singleton
open class BookManager(
        private val bookRepository: BookRepository, private val eventPublisher: ApplicationEventPublisher) { // <1>

    @Transactional
    open fun saveBook(title: String, pages: Int) {
        val book = Book(0, title, pages)
        bookRepository.save(book)
        eventPublisher.publishEvent(NewBookEvent(book)) // <2>
    }

    @TransactionalEventListener
    open fun onNewBook(event: NewBookEvent) {
        println("book = ${event.book}") // <3>
    }

    class NewBookEvent(val book: Book)
}
