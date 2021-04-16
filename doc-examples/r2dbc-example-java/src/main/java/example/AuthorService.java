package example;

import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import javax.transaction.Transactional;
import java.util.Arrays;

@Singleton
public class AuthorService {
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    public AuthorService(AuthorRepository authorRepository, BookRepository bookRepository) { // <1>
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional // <2>
    Mono<Void> setupData() {
        return Mono.from(authorRepository.save(new Author("Stephen King")))
                .flatMapMany((author -> bookRepository.saveAll(Arrays.asList(
                        new Book("The Stand", 1000, author),
                        new Book("The Shining", 400, author)
                ))))
                .then(Mono.from(authorRepository.save(new Author("James Patterson"))))
                .flatMapMany((author ->
                        bookRepository.save(new Book("Along Came a Spider", 300, author))
                )).then();
    }
}
