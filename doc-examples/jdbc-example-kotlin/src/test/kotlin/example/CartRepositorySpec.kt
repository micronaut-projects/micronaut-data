package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class CartRepositorySpec(private val cartRepository: CartRepository?) {

    @Test
    fun testCrud() {
        var cart = Cart(
                items = listOf(CartItem(name = "Food"), CartItem(name = "Drinks"))
        )
        cart = cartRepository!!.save(cart)
        assertNotNull(cart.id)
        assertNotNull(cart.items)
        assertEquals(2, cart.items.size)
        assertNotNull(cart.items[0].id)
        assertNotNull(cart.items[1].id)
        cart = cartRepository.findById(cart.id!!).orElse(null)
        assertNotNull(cart)
        assertNotNull(cart.id)
        assertNotNull(cart.items)
        assertEquals(2, cart.items.size)
        assertNotNull(cart.items[0].id)
        assertNotNull(cart.items.get(1).id)
        assertEquals("Food", cart.items[0].name)
        assertEquals("Drinks", cart.items[1].name)
    }
}
