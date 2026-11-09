package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@OracleGraphRepository(graph = "bank_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
public interface BankGraph extends GenericRepository<Account, Long> {

    Optional<Account> findById(Long id);

    Account save(Account account);

    List<Account> saveAll(Iterable<Account> accounts);

    Account update(Account account);

    List<Account> updateAll(Iterable<Account> accounts);

    Account insertReturning(Account account);

    Account updateReturning(Account account);

    Account deleteReturning(Account account);

    List<Account> deleteReturning(Iterable<Account> accounts);

    void deleteById(Long id);

    List<Account> findByNameStartsWithOrderByName(String prefix);

    Page<Account> findByNameStartsWith(String prefix, Pageable pageable);

    List<Account> findByIdGreaterThan(Long id, Sort sort);

    List<Account> findByIdGreaterThanOrderByName(Long id, Sort sort);

    List<String> findNameByIdGreaterThan(Long id, Sort sort);

    Page<Account> findByIdGreaterThan(Long id, Pageable pageable);

    List<Account> findByTransfersAmountGreaterThan(BigDecimal amount);

    List<Account> findByTransfersToName(String name);

    List<Account> findByTransfers(Transfer transfer);

    List<Account> findByTransfersTo(Account to);

    long countByNameIsNotNull();

    @Join(value = "transfers", type = Join.Type.INNER)
    long countByNameContains(String part);

    @Join(value = "transfers", type = Join.Type.INNER)
    Page<Account> findByNameContains(String part, Pageable pageable);

    @Join(value = "transfers", type = Join.Type.INNER)
    List<String> findNameByNameContains(String part);

    List<String> findNameByIdIn(List<Long> ids);

    @PathLimit(3)
    boolean existsPathByTransfers(Long from, Long to);

    @PathLimit(3)
    long countPathsByTransfers(Long from, Long to);

    /**
     * The argument overrides the depth of the annotation.
     */
    @PathLimit(3)
    long countPathsByTransfers(Long from, Long to, @PathLimit int maxDepth);

    List<Account> findByOpenedGreaterThan(LocalDateTime opened);

    List<GraphRelationship<Account, Account>> findRelationshipsByTransfers(Long from, Long to);

    @Query(value = "UPDATE accounts SET name = $name WHERE id = $id", readOnly = false)
    int renameAccount(Long id, String name);

    @Query("SELECT * FROM GRAPH_TABLE (bank_graph MATCH (a IS Account) -[t IS TRANSFERS]-> (b IS Account) WHERE t.amount > $amount COLUMNS (a.name AS \"source\", b.name AS \"target\", t.amount AS \"amount\")) ORDER BY \"amount\"")
    List<TransferView> largeTransfers(BigDecimal amount);

    @Query("SELECT * FROM GRAPH_TABLE (bank_graph MATCH (n IS Account) COLUMNS (n.id AS \"n__ID_\", n.id AS \"n__id\", 'Account' AS \"n__LABEL_\", n.name AS \"n__name\", n.opened AS \"n__opened\")) g WHERE NOT EXISTS (SELECT 1 FROM GRAPH_TABLE (bank_graph MATCH (a IS Account) -[IS TRANSFERS]-> (b IS Account) COLUMNS (a.id AS \"k\")) e WHERE e.\"k\" = g.\"n__ID_\")")
    List<Account> findWithoutTransfers();
}
