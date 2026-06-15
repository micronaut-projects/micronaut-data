package example.oracle;

import example.ETagBook;
import example.ETagBookRepository;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@SuppressWarnings("java:S3577")
class ETagBookRepositorySpec {

    @Inject
    ETagBookRepository bookRepository;

    @Test
    void testGeneratedETagOptimisticLocking() {
        ETagBook book = bookRepository.save(new ETagBook(null, "Initial", new ETagBook.BookDetails(200, 10), null));
        ETagBook fresh = bookRepository.findById(book.id()).orElseThrow();
        String etag = fresh.etag();
        assertNotNull(etag);

        bookRepository.update(new ETagBook(fresh.id(), "Updated", fresh.bookDetails(), etag));

        ETagBook stale = new ETagBook(fresh.id(), "Stale", fresh.bookDetails(), etag);
        assertThrows(OptimisticLockException.class, () -> bookRepository.update(stale));
    }
}
