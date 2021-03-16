package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class CartRepositorySpec {

    @Inject
    CartRepository cartRepository;

    @Test
    void testCrud() {
        Cart cart = new Cart(
                List.of(new CartItem("Food"), new CartItem("Drinks"))
        );
        cart = cartRepository.save(cart);

        assertNotNull(cart.id());
        assertNotNull(cart.items());
        assertEquals(2, cart.items().size());
        assertNotNull(cart.items().get(0).id());
        assertNotNull(cart.items().get(1).id());

        cart = cartRepository.findById(cart.id()).orElse(null);

        assertNotNull(cart);
        assertNotNull(cart.id());
        assertNotNull(cart.items());
        assertEquals(2, cart.items().size());
        assertNotNull(cart.items().get(0).id());
        assertNotNull(cart.items().get(1).id());
        assertEquals("Food", cart.items().get(0).name());
        assertEquals("Drinks", cart.items().get(1).name());
    }
}