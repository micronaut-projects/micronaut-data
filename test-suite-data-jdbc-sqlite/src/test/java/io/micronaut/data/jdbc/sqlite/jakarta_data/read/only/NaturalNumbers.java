package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumber.NumberType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * This is a read only repository that represents the set of Natural Numbers from 1-100.
 * This repository will be pre-populated at test startup and verified prior to running tests.
 *
 * TODO figure out a way to make this a ReadOnlyRepository instead.
 */
@Repository
@JdbcRepository(dialect = Dialect.ANSI)
public interface NaturalNumbers extends BasicRepository<NaturalNumber, Long>, IdOperations {

    long countAll();

    CursoredPage<NaturalNumber> findByFloorOfSquareRootOrderByIdAsc(long sqrtFloor,
                                                                    PageRequest pagination);

    Stream<NaturalNumber> findByIdBetweenOrderByNumTypeOrdinalAsc(long minimum,
                                                                  long maximum,
                                                                  Order<NaturalNumber> sorts);

    List<NaturalNumber> findByIdGreaterThanEqual(long minimum,
                                                 Limit limit,
                                                 Order<NaturalNumber> sorts);

    NaturalNumber[] findByIdLessThan(long exclusiveMax, Sort<NaturalNumber> primarySort, Sort<NaturalNumber> secondarySort);

    List<NaturalNumber> findByIdLessThanEqual(long maximum, Sort<?>... sorts);

    Page<NaturalNumber> findByIdLessThanOrderByFloorOfSquareRootDesc(long exclusiveMax,
                                                                     PageRequest pagination,
                                                                     Order<NaturalNumber> order);

    CursoredPage<NaturalNumber> findByNumTypeAndNumBitsRequiredLessThan(NumberType type,
                                                                        short bitsUnder,
                                                                        Order<NaturalNumber> order,
                                                                        PageRequest pagination);

    NaturalNumber[] findByNumTypeNot(NumberType notThisType, Limit limit, Order<NaturalNumber> sorts);

    Page<NaturalNumber> findByNumTypeAndFloorOfSquareRootLessThanEqual(NumberType type,
                                                                       long maxSqrtFloor,
                                                                       PageRequest pagination,
                                                                       Sort<NaturalNumber> sort);

    @Query("SELECT id WHERE isOdd = true AND id BETWEEN 21 AND ?1 ORDER BY id ASC")
    Page<Long> oddsFrom21To(long max, PageRequest pageRequest);

    @Query("WHERE isOdd = false AND numType = io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumber.NumberType.PRIME")
    Optional<NaturalNumber> two();
}
