package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest(transactional = false)
class ItemRepositorySpec {

    @Inject
    private ItemRepository itemRepository;

    /**
     * Verifies issue https://github.com/micronaut-projects/micronaut-data/issues/3267 is fixed in core.
     */
    @Test
    void testGetItems() {
        List<Item> items = itemRepository.getItems();
        assertEquals(1, items.size());
        Item item = items.get(0);
        assertEquals(1, item.getId());
        assertNull(item.getTitle());
    }
}
