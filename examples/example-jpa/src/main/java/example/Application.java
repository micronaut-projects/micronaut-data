package example;

import java.util.Arrays;

import javax.inject.Singleton;

import io.micronaut.core.annotation.TypeHint;

import org.hibernate.dialect.PostgreSQL10Dialect;
import org.postgresql.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import example.domain.Owner;
import example.domain.Pet;
import example.domain.Pet.PetType;
import example.repositories.OwnerRepository;
import example.repositories.PetRepository;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;

@Singleton
@TypeHint( {
    PetType.class,
    PostgreSQL10Dialect.class,
    Driver.class
})
public class Application {
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;

    Application(OwnerRepository ownerRepository, PetRepository petRepository) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }

    @EventListener
    void init(StartupEvent event) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Populating data");
        }

        Owner fred = new Owner();
        fred.setName("Fred");
        fred.setAge(45);
        Owner barney = new Owner();
        barney.setName("Barney");
        barney.setAge(40);
        ownerRepository.saveAll(Arrays.asList(fred, barney));

        Pet dino = new Pet();
        dino.setName("Dino");
        dino.setOwner(fred);
        Pet bp = new Pet();
        bp.setName("Baby Puss");
        bp.setOwner(fred);
        bp.setType(PetType.CAT);
        Pet hoppy = new Pet();
        hoppy.setName("Hoppy");
        hoppy.setOwner(barney);

        petRepository.saveAll(Arrays.asList(dino, bp, hoppy));
    }
}