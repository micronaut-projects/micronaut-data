/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.oraclexe.sessionless

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.Transactional
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionHttpConfiguration
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionPropagationOperations
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@Property(name = "spec.name", value = OracleSessionlessTransactionPropagationSpec.SPEC_NAME)
@Property(name = "micronaut.data.oracle.sessionless.http.propagation-enabled", value = "true")
@Property(name = "micronaut.http.client.read-timeout", value = "600s")
@MicronautTest(transactional = false)
class OracleSessionlessTransactionPropagationSpec extends Specification implements OracleTestPropertyProvider {

    static final String SPEC_NAME = "OracleSessionlessTransactionPropagationSpec"
    private static final String SESSIONLESS_TRANSACTION_HEADER = OracleSessionlessTransactionHttpConfiguration.DEFAULT_HEADER_NAME

    @Inject
    ExpenseReportService expenseReportService

    @Inject
    ExpenseReportRepository expenseReportRepository

    @Inject
    OracleSessionlessTransactionPropagationOperations transactionPropagationOperations

    @Inject
    @Client("/")
    HttpClient client

    @Override
    List<String> packages() {
        [getClass().package.name]
    }

    void setup() {
        expenseReportRepository.deleteAll()
    }

    void "http propagation commits a suspended expense report approval"() {
        when:
        SubmittedExpenseReport report = submitViaHttp("employee-http-1", "travel", 125.75)

        then:
        expenseReportRepository.findById(report.id()).isEmpty()

        when:
        HttpRequest<String> approveRequest = HttpRequest.POST("/expense-reports/approve/" + report.id(), "")
                .header(SESSIONLESS_TRANSACTION_HEADER, report.transactionId())
        HttpResponse<Void> approveResponse = client.toBlocking().exchange(approveRequest, Void)

        then:
        approveResponse.status == HttpStatus.NO_CONTENT
        !approveResponse.headers.contains(SESSIONLESS_TRANSACTION_HEADER)
        expenseReportRepository.findById(report.id()).orElseThrow().status == "APPROVED"
    }

    void "http propagation rolls back a resumed expense report approval"() {
        when:
        SubmittedExpenseReport report = submitViaHttp("employee-http-2", "meals", 43.20)

        then:
        expenseReportRepository.findById(report.id()).isEmpty()

        when:
        HttpRequest<String> rejectRequest = HttpRequest.POST("/expense-reports/reject/" + report.id(), "")
                .header(SESSIONLESS_TRANSACTION_HEADER, report.transactionId())
        client.toBlocking().exchange(rejectRequest, Void)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        !e.response.headers.contains(SESSIONLESS_TRANSACTION_HEADER)
        expenseReportRepository.findById(report.id()).isEmpty()
    }

    void "programmatic propagation commits a suspended expense report approval"() {
        when:
        SubmittedExpenseReport report = suspendReport("employee-prog-1", "lodging", 318.40)

        then:
        expenseReportRepository.findById(report.id()).isEmpty()

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.approveReport(report.id())
            null
        })

        then:
        expenseReportRepository.findById(report.id()).orElseThrow().status == "APPROVED"
    }

    void "programmatic propagation rolls back a resumed expense report approval"() {
        given:
        SubmittedExpenseReport report = suspendReport("employee-prog-2", "software", 799.99)
        assert expenseReportRepository.findById(report.id()).isEmpty()

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.rejectReport(report.id())
            null
        })

        then:
        ExpenseRejectedException e = thrown()
        e.message == "Expense report failed policy check"
        expenseReportRepository.findById(report.id()).isEmpty()
    }

    void "nested programmatic propagation restores the outer expense report approval"() {
        when:
        List<Long> reportIds = Objects.requireNonNull(transactionPropagationOperations.withPropagation({
            Long outerReportId = expenseReportService.submitReport("employee-nested-outer", "conference", 640.00)
            String outerTransactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
            assert expenseReportRepository.findById(outerReportId).isEmpty()

            SubmittedExpenseReport innerReport = Objects.requireNonNull(transactionPropagationOperations.withPropagation({
                Long innerReportId = expenseReportService.submitReport("employee-nested-inner", "training", 215.25)
                String innerTransactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
                assert innerTransactionId != outerTransactionId
                assert expenseReportRepository.findById(innerReportId).isEmpty()

                expenseReportService.approveReport(innerReportId)
                assert transactionPropagationOperations.currentTransactionId().isEmpty()
                new SubmittedExpenseReport(innerReportId, innerTransactionId)
            }))

            assert transactionPropagationOperations.currentTransactionId().orElseThrow() == outerTransactionId
            assert expenseReportRepository.findById(innerReport.id()).orElseThrow().status == "APPROVED"

            expenseReportService.approveReport(outerReportId)
            assert transactionPropagationOperations.currentTransactionId().isEmpty()
            [outerReportId, innerReport.id()]
        }))

        then:
        expenseReportRepository.findById(reportIds[0]).orElseThrow().status == "APPROVED"
        expenseReportRepository.findById(reportIds[1]).orElseThrow().status == "APPROVED"
    }

    private SubmittedExpenseReport submitViaHttp(String employeeId, String category, BigDecimal amount) {
        HttpResponse<String> submitResponse = client.toBlocking()
            .exchange(HttpRequest.POST("/expense-reports/submit/${employeeId}/${category}/${amount}", ""), String)
        String transactionId = submitResponse.headers.get(SESSIONLESS_TRANSACTION_HEADER)
        assert submitResponse.status == HttpStatus.OK
        assert transactionId
        new SubmittedExpenseReport(Long.valueOf(submitResponse.body()), transactionId)
    }

    private SubmittedExpenseReport suspendReport(String employeeId, String category, BigDecimal amount) {
        Objects.requireNonNull(transactionPropagationOperations.withPropagation({
            Long reportId = expenseReportService.submitReport(employeeId, category, amount)
            String transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
            new SubmittedExpenseReport(reportId, transactionId)
        }))
    }

    private static final class SubmittedExpenseReport {
        private final Long id
        private final String transactionId

        private SubmittedExpenseReport(Long id, String transactionId) {
            this.id = id
            this.transactionId = transactionId
        }

        Long id() {
            id
        }

        String transactionId() {
            transactionId
        }
    }
}

@Singleton
@Requires(property = "spec.name", value = OracleSessionlessTransactionPropagationSpec.SPEC_NAME)
class ExpenseReportService {

    private final ExpenseReportRepository expenseReportRepository

    ExpenseReportService(ExpenseReportRepository expenseReportRepository) {
        this.expenseReportRepository = expenseReportRepository
    }

    @Transactional(propagation = TransactionDefinition.Propagation.SUSPEND, timeout = 3600)
    Long submitReport(String employeeId, String category, BigDecimal amount) {
        ExpenseReport report = expenseReportRepository.save(new ExpenseReport(
            employeeId: employeeId,
            category: category,
            expenseAmount: amount,
            status: "SUBMITTED"
        ))
        report.id
    }

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_SUSPENDED)
    void approveReport(Long id) {
        expenseReportRepository.updateStatus(id, "APPROVED")
    }

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_SUSPENDED)
    void rejectReport(Long id) {
        expenseReportRepository.updateStatus(id, "REJECTED")
        throw new ExpenseRejectedException("Expense report failed policy check")
    }
}

@ExecuteOn(TaskExecutors.IO)
@Controller("/expense-reports")
@Requires(property = "spec.name", value = OracleSessionlessTransactionPropagationSpec.SPEC_NAME)
class ExpenseReportController {

    private final ExpenseReportService expenseReportService

    ExpenseReportController(ExpenseReportService expenseReportService) {
        this.expenseReportService = expenseReportService
    }

    @Post("/submit/{employeeId}/{category}/{amount}")
    Long submit(String employeeId, String category, BigDecimal amount) {
        expenseReportService.submitReport(employeeId, category, amount)
    }

    @Post("/approve/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void approve(Long id) {
        expenseReportService.approveReport(id)
    }

    @Post("/reject/{id}")
    @Status(HttpStatus.NO_CONTENT)
    void reject(Long id) {
        expenseReportService.rejectReport(id)
    }
}

@MappedEntity("expense_report")
class ExpenseReport {

    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "EXPENSE_REPORT_SEQ")
    Long id

    String employeeId
    String category
    BigDecimal expenseAmount
    String status
}

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(property = "spec.name", value = OracleSessionlessTransactionPropagationSpec.SPEC_NAME)
interface ExpenseReportRepository extends CrudRepository<ExpenseReport, Long> {

    @Query("UPDATE expense_report SET status = :status WHERE id = :id")
    void updateStatus(Long id, String status)
}

class ExpenseRejectedException extends RuntimeException {

    ExpenseRejectedException(String message) {
        super(message)
    }
}
