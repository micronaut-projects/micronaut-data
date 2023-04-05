package example;

import example.domain.Usr;
import example.domain.view.UsrView;
import example.repository.UsrRepository;
import example.repository.UsrViewRepository;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;

/**
 * Run new Oracle 23c docker image with this command
 * >> docker run -p 1521:1521 -e ORACLE_PWD=test -d --name oracle container-registry.oracle.com/database/free
 * Since flyway doesn't work with Oracle23c schema can be created manually from schema.sql file in resources folder.
 * Then this test can be executed.
 */
@MicronautTest
public class UsrRepositorySpec {

    @Inject
    UsrRepository usrRepository;

    @Inject
    UsrViewRepository usrViewRepository;

    @Test
    void testCrud() {
        Long id = 10L;
        Optional<Usr> optUsr = usrRepository.findById(id);
        if (!optUsr.isPresent()) {
            Usr usr = new Usr(10L, "Usr10", Period.ofYears(1).plusMonths(6), Duration.ofHours(5), 15.65d, LocalDate.now(), LocalDateTime.now());
            usrRepository.save(usr);
        }

        Optional<UsrView> optUsrView = usrViewRepository.findUsrViewByUsrId(id);
        Assertions.assertTrue(optUsrView.isPresent());
        UsrView usrView = optUsrView.get();
        usrView.setName("User10-Updated");
        if (usrView.getMemo() == null) {
            usrView.setMemo("".getBytes(Charset.defaultCharset()));
        }
        usrViewRepository.updateUsrView(usrView, id);

        optUsr = usrRepository.findById(id);
        Assertions.assertTrue(optUsr.isPresent());
        Assertions.assertEquals(usrView.getName(), optUsr.get().getName());

        usrViewRepository.updateUsrViewBd(150.5, id);
        optUsr = usrRepository.findById(id);
        Assertions.assertTrue(optUsr.isPresent());
        Assertions.assertEquals(150.5, optUsr.get().getBd());

        int deletedCount = usrViewRepository.deleteUsrView(id);
        Assertions.assertEquals(1, deletedCount);

        Assertions.assertFalse(usrRepository.findById(id).isPresent());
        Assertions.assertFalse(usrViewRepository.findUsrViewByUsrId(id).isPresent());

        id = 20L;
        optUsrView = usrViewRepository.findUsrViewByUsrId(id);
        if (!optUsrView.isPresent()) {
            usrView = new UsrView(id, "User" + id, Period.ofYears(2).plusMonths(10), Duration.ofMinutes(30), 9.9999,
                ("memo" + id).getBytes(Charset.defaultCharset()), null, LocalDateTime.now(), LocalDate.now());
            usrViewRepository.insertUsrView(usrView);
        }

        optUsr = usrRepository.findById(id);
        Assertions.assertTrue(optUsr.isPresent());
        Assertions.assertEquals(usrView.getName(), optUsr.get().getName());

        // Test optimistic locking
        usrView = usrViewRepository.findUsrViewByUsrId(id).get();
        usrView.getMetadata().setEtag(UUID.randomUUID().toString());
        try {
            usrViewRepository.updateUsrView(usrView, id);
            Assertions.fail("Should throw OptimisticLockException when ETAG is not matching");
        } catch (OptimisticLockException e) {
            Assertions.assertTrue(e.getMessage().startsWith("ETAG did not match when updating record"));
        }

        usrRepository.deleteAll();

        Assertions.assertFalse(usrRepository.findById(id).isPresent());
        Assertions.assertFalse(usrViewRepository.findUsrViewByUsrId(id).isPresent());
    }
}
