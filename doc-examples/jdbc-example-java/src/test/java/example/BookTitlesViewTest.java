package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookTitlesViewTest {

    @Inject
    BookRepository bookRepository;

    @Inject
    TransactionOperations<Connection> transactionOperations;

    @Test
    void theContributedViewIsCreatedOverTheBookTable() {
        bookRepository.save(new Book("The Left Hand of Darkness", 300));

        List<String> titles = transactionOperations.executeRead(status -> {
            try (PreparedStatement ps = status.getConnection().prepareStatement("SELECT title FROM book_titles");
                 ResultSet rs = ps.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
                return result;
            }
        });

        assertTrue(titles.contains("The Left Hand of Darkness"));
    }
}
