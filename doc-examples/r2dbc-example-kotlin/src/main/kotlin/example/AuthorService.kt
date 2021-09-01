package example

import reactor.core.publisher.Mono
import jakarta.inject.Singleton
import javax.transaction.Transactional

@Singleton
open class AuthorService(
        private val authorRepository: AuthorRepository,
        private val bookRepository: BookRepository) { // <1>

    @Transactional // <2>
    open fun setupData(): Mono<Void> {
        return Mono.from(authorRepository.save(Author("Stephen King")))
                .flatMapMany { author: Author ->
                    bookRepository.saveAll(listOf(
                            Book("The Stand", 1000, author),
                            Book("The Shining", 400, author)
                    ))
                }
                .then(Mono.from(authorRepository.save(Author("James Patterson"))))
                .flatMapMany { author: Author ->
                    bookRepository.save(Book("Along Came a Spider", 300, author))
                }.then()
    }
}