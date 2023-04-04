
package example;

import example.domain.Usr;
import example.domain.UsrDto;
import example.domain.view.StudentView;
import example.domain.view.UsrView;
import example.repository.StudentViewRepository;
import example.repository.UsrRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

@ExecuteOn(TaskExecutors.IO)
@Controller("/students")
public class StudentController {

    private static final Logger LOG = LoggerFactory.getLogger(StudentController.class);

    private final StudentViewRepository studentViewRepository;
    private final UsrRepository usrRepository;

    public StudentController(StudentViewRepository studentViewRepository, UsrRepository usrRepository) {
        this.studentViewRepository = studentViewRepository;
        this.usrRepository = usrRepository;
    }

    @Get("/{id}")
    public Mono<StudentView> show(Long id) {
        return studentViewRepository.findByStudentId(id);
    }

    @Get("/view")
    public Flux<StudentView> view() {
        return studentViewRepository.findAll();
    }

    @Get("/user/{id}")
    public Mono<UsrDto> user(Long id) {
        Optional<Usr> optUser = usrRepository.findById(id);
        return optUser.isPresent() ? Mono.empty() : Mono.just(UsrDto.fromUsr(optUser.get()));
    }

    @Get("/user_view/{id}")
    public Mono<UsrView> userView(Long id) {
        Optional<UsrView> optUserView = usrRepository.findByUsrId(id);
        if (optUserView.isPresent()) {
            UsrView usrView = optUserView.get();
            try {
                usrView.setName("User123");
                // Make update fail due to different etag
                // usrView.setMetadata(Metadata.of("TEST", usrView.getMetadata().getAsof()));
                usrRepository.update(usrView, usrView.getUsrId());

                // Create new one on the fly
                boolean insertNew = id == 2;
                if (insertNew) {
                    Long newId = id + 1;
                    UsrView newUsrView = new UsrView(newId, "New User " + newId, Period.of(1, 2, 0), Duration.ofDays(1), 9.9999,
                        "memo123".getBytes(Charset.defaultCharset()), null, LocalDateTime.now(),
                        LocalDate.now()/*, OffsetDateTime.now()*/);
                    usrRepository.insert(newUsrView);
                }

                optUserView = usrRepository.findByUsrId(id);
                usrView = optUserView.get();
                return Mono.just(usrView);
            } catch (Exception e) {
                LOG.error("Update failed", e);
                return Mono.just(usrView);
            }
        } else {
            // Create new one on the fly
            Long newId = id < 1 ? 1 : id;
            UsrView newUsrView = new UsrView(newId, "New User " + newId, Period.of(1, 2, 0), Duration.ofDays(1), 9.9999,
                "memo123".getBytes(Charset.defaultCharset()), null, LocalDateTime.now(),
                LocalDate.now()/*, OffsetDateTime.now()*/);
            try {
                usrRepository.insert(newUsrView);
            } catch (Exception e) {
                LOG.error("Insert failed", e);
                return Mono.empty();
            }
            optUserView = usrRepository.findByUsrId(newId);
            return optUserView.isPresent() ? Mono.just(optUserView.get()) : Mono.empty();
        }
    }
}
