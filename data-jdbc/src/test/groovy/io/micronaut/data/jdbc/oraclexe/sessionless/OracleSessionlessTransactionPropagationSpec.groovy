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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.exceptions.TransactionSystemException
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionPropagationOperations
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.sql.Connection

@Property(name = "spec.name", value = OracleSessionlessTransactionPropagationSpec.SPEC_NAME)
@MicronautTest(transactional = false)
class OracleSessionlessTransactionPropagationSpec extends Specification implements OracleTestPropertyProvider {

    static final String SPEC_NAME = "OracleSessionlessTransactionPropagationSpec"

    @Inject
    ExpenseReportService expenseReportService

    @Inject
    ExpenseReportRepository expenseReportRepository

    @Inject
    OracleSessionlessTransactionPropagationOperations transactionPropagationOperations

    @Inject
    TransactionOperations<Connection> transactionOperations

    @Override
    List<String> packages() {
        [getClass().package.name]
    }

    void setup() {
        expenseReportRepository.deleteAll()
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

    void "empty initial stage can suspend and resume for later database work"() {
        given:
        String transactionId = transactionPropagationOperations.withPropagation({
            expenseReportService.emptyInitialStage()
            transactionPropagationOperations.currentTransactionId().orElseThrow()
        })
        assert !transactionOperations.hasConnection()
        def definition = new DefaultTransactionDefinition()
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, OracleTransactional.Sessionless.RESUME_AND_SUSPEND)

        when:
        Long reportId = transactionPropagationOperations.withPropagation(transactionId, {
            transactionOperations.execute(definition, { status ->
                expenseReportRepository.save(new ExpenseReport(
                    employeeId: "employee-empty-start",
                    category: "travel",
                    expenseAmount: 120.00,
                    status: "SUBMITTED"
                )).id
            })
        })

        then:
        !transactionOperations.hasConnection()
        expenseReportRepository.findById(reportId).isEmpty()

        when:
        transactionPropagationOperations.withPropagation(transactionId, {
            expenseReportService.approveReport(reportId)
            assert transactionPropagationOperations.currentTransactionId().isEmpty()
            null
        })

        then:
        expenseReportRepository.findById(reportId).orElseThrow().status == "APPROVED"
    }

    void "same transaction survives multiple resumed stages including an empty stage"() {
        given:
        SubmittedExpenseReport report = suspendReport("employee-stages", "travel", 120.00)

        when:
        ["REVIEWED", "VALIDATED"].each { nextStatus ->
            transactionPropagationOperations.withPropagation(report.transactionId(), {
                expenseReportService.reviewReport(report.id(), nextStatus)
                assert transactionPropagationOperations.currentTransactionId().orElseThrow() == report.transactionId()
                null
            })
            assert !transactionOperations.hasConnection()
            assert expenseReportRepository.findById(report.id()).isEmpty()
        }
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.emptyStage()
            assert transactionPropagationOperations.currentTransactionId().orElseThrow() == report.transactionId()
            null
        })

        then:
        expenseReportRepository.findById(report.id()).isEmpty()

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.approveReport(report.id())
            assert transactionPropagationOperations.currentTransactionId().isEmpty()
            null
        })

        then:
        expenseReportRepository.findById(report.id()).orElseThrow().status == "APPROVED"
    }

    void "failure in an intermediate stage rolls back all earlier work"() {
        given:
        SubmittedExpenseReport report = suspendReport("employee-stage-failure", "travel", 120.00)
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.reviewReport(report.id(), "REVIEWED")
            null
        })

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            try {
                expenseReportService.failReview(report.id())
            } finally {
                assert transactionPropagationOperations.currentTransactionId().isEmpty()
            }
            null
        })

        then:
        thrown(ExpenseRejectedException)
        expenseReportRepository.findById(report.id()).isEmpty()

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.approveReport(report.id())
            null
        })

        then:
        thrown(RuntimeException)
    }

    void "programmatic intermediate stage marked rollback only rolls back previous stages"() {
        given:
        SubmittedExpenseReport report = suspendReport("employee-rollback-only", "travel", 120.00)
        def definition = new DefaultTransactionDefinition()
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, OracleTransactional.Sessionless.RESUME_AND_SUSPEND)

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.reviewReport(report.id(), "REVIEWED")
            transactionOperations.execute(definition, { status ->
                assert expenseReportRepository.findById(report.id()).orElseThrow().status == "REVIEWED"
                status.setRollbackOnly()
                null
            })
            assert transactionPropagationOperations.currentTransactionId().isEmpty()
            null
        })

        then:
        !transactionOperations.hasConnection()
        expenseReportRepository.findById(report.id()).isEmpty()
    }

    void "completed transaction cannot be resumed even by an empty stage"() {
        given:
        SubmittedExpenseReport report = suspendReport("employee-completed", "travel", 120.00)
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.approveReport(report.id())
            null
        })

        when:
        transactionPropagationOperations.withPropagation(report.transactionId(), {
            expenseReportService.emptyStage()
            null
        })

        then:
        thrown(TransactionSystemException)
        expenseReportRepository.findById(report.id()).orElseThrow().status == "APPROVED"
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

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.SUSPEND, timeout = 3600)
    Long submitReport(String employeeId, String category, BigDecimal amount) {
        ExpenseReport report = expenseReportRepository.save(new ExpenseReport(
            employeeId: employeeId,
            category: category,
            expenseAmount: amount,
            status: "SUBMITTED"
        ))
        report.id
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.RESUME_AND_SUSPEND)
    void reviewReport(Long id, String nextStatus) {
        ExpenseReport report = expenseReportRepository.findById(id).orElseThrow()
        assert report.status in ["SUBMITTED", "REVIEWED"]
        expenseReportRepository.updateStatus(id, nextStatus)
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.SUSPEND, timeout = 3600)
    void emptyInitialStage() {
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.RESUME_AND_SUSPEND)
    void emptyStage() {
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.RESUME_AND_SUSPEND)
    void failReview(Long id) {
        assert expenseReportRepository.findById(id).orElseThrow().status == "REVIEWED"
        expenseReportRepository.updateStatus(id, "REJECTED")
        throw new ExpenseRejectedException("Review failed")
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
    void approveReport(Long id) {
        expenseReportRepository.updateStatus(id, "APPROVED")
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
    void rejectReport(Long id) {
        expenseReportRepository.updateStatus(id, "REJECTED")
        throw new ExpenseRejectedException("Expense report failed policy check")
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
