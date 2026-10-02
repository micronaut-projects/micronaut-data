package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@Requires(notEnv = "oracle")
class PessimisticLockingSpec {

    @Inject
    AccountBalanceRepository accountBalanceRepository;

    @Inject
    ProductLockingRepository productLockingRepository;

    @Inject
    ManufacturerRepository manufacturerRepository;

    @AfterEach
    void cleanup() {
        accountBalanceRepository.deleteAll();
        productLockingRepository.deleteAll();
        manufacturerRepository.deleteAll();
    }

    @Test
    void concurrentUpdatesDoNotLoseWrites() throws Exception {
        Long id = accountBalanceRepository.save(new AccountBalance(BigInteger.valueOf(100))).getId();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> accountBalanceRepository.addToBalance(id, BigInteger.TEN)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }

        assertEquals(BigInteger.valueOf(300), accountBalanceRepository.findById(id).orElseThrow().getBalance());
    }

    @Test
    void findMethodsForUpdate() {
        Manufacturer apple = manufacturerRepository.save("Apple");
        Product macBook = productLockingRepository.save(new Product("MacBook", apple));
        productLockingRepository.save(new Product("iPhone", apple));

        Product found = productLockingRepository.findByIdForUpdate(macBook.getId()).orElseThrow();
        assertEquals("Apple", found.getManufacturer().getName());

        List<Product> ordered = productLockingRepository.findAllOrderByNameForUpdate();
        assertEquals(List.of("MacBook", "iPhone"), ordered.stream().map(Product::getName).toList());

        List<Product> byName = productLockingRepository.findByNameForUpdate("iPhone");
        assertEquals(1, byName.size());
        assertTrue(byName.stream().allMatch(p -> p.getName().equals("iPhone")));
    }
}
