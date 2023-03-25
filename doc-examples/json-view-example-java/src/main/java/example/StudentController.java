
package example;

import example.domain.Usr;
import example.domain.UsrDto;
import example.domain.view.StudentView;
import example.domain.view.UsrView;
import example.repository.StudentRepository;
import example.repository.StudentViewRepository;
import example.repository.UsrRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonBinaryObjectMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonTextObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;

@ExecuteOn(TaskExecutors.IO)
@Controller("/students")
public class StudentController {

    private static final Logger LOG = LoggerFactory.getLogger(StudentController.class);

    private final StudentViewRepository studentViewRepository;
    private final StudentRepository studentRepository;
    private final UsrRepository usrRepository;

    private final OracleJdbcJsonTextObjectMapper textObjectMapper;
    private final OracleJdbcJsonBinaryObjectMapper binaryObjectMapper;

    public StudentController(StudentViewRepository studentViewRepository, StudentRepository studentRepository,
                             UsrRepository usrRepository, OracleJdbcJsonTextObjectMapper textObjectMapper,
                             OracleJdbcJsonBinaryObjectMapper binaryObjectMapper) {
        this.studentViewRepository = studentViewRepository;
        this.studentRepository = studentRepository;
        this.usrRepository = usrRepository;
        this.textObjectMapper = textObjectMapper;
        this.binaryObjectMapper = binaryObjectMapper;
    }

    @Get("/{id}")
    public Optional<StudentView> show(Long id) {
        return studentViewRepository.findByStudentId(id);
    }

    @Get("/view")
    public List<StudentView> view() {
        return studentViewRepository.findAll();
    }

    @Get("/user/{id}")
    public Optional<UsrDto> user(Long id) {
        Optional<Usr> optUser = usrRepository.findById(id);
        return Optional.of(UsrDto.fromUsr(optUser.get()));
    }

    @Get("/user_view/{id}")
    public Optional<UsrView> userView(Long id) {
        Optional<UsrView> optUserView = usrRepository.findByUsrId(id);
        if (optUserView.isPresent()) {
            UsrView usrView = optUserView.get();
            try {
                usrView.setName("User123");
                // Make update fail due to different etag
                // usrView.setMetadata(Metadata.of("TEST", usrView.getMetadata().getAsof()));
                boolean updateCustomObject = false;
                if (!updateCustomObject) {
                    usrRepository.update(usrView, usrView.getUsrId());
                } else {
                    boolean updateBinary = false;
                    if (updateBinary) {
                        byte[] bytes = binaryObjectMapper.writeValueAsBytes(usrView);
                        usrRepository.updateBinary(bytes, usrView.getUsrId());
                    } else {
                        String data = textObjectMapper.writeValueAsString(usrView);
                        usrRepository.updateCustom(data, usrView.getUsrId());
                    }
                }

                // Create new one on the fly
                boolean insertNew = id == 2;
                if (insertNew) {
                    Long newId = id + 1;
                    UsrView newUsrView = new UsrView(newId, "New User " + newId, Period.of(1, 2, 0), Duration.ofDays(1), 9.9999,
                        "memo123".getBytes(Charset.defaultCharset()), null, LocalDateTime.now(),
                        LocalDate.now(), OffsetDateTime.now());
                    usrRepository.insert(newUsrView);
                }

                optUserView = usrRepository.findByUsrId(id);
                usrView = optUserView.get();
                return Optional.of(usrView);
            } catch (Exception e) {
                LOG.error("Update failed", e);
                return Optional.of(usrView);
            }
        } else {
            // Create new one on the fly
            Long newId = id < 1 ? 1 : id;
            UsrView newUsrView = new UsrView(newId, "New User " + newId, Period.of(1, 2, 0), Duration.ofDays(1), 9.9999,
                "memo123".getBytes(Charset.defaultCharset()), null, LocalDateTime.now(),
                LocalDate.now(), OffsetDateTime.now());
            try {
                usrRepository.insert(newUsrView);
            } catch (Exception e) {
                LOG.error("Insert failed", e);
                return Optional.empty();
            }
            optUserView = usrRepository.findByUsrId(newId);
            return optUserView;
        }
    }

}
