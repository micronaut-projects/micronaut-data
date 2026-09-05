package io.micronaut.data.nitrite.generated;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Derived finders covering every predicate shape
 * {@code io.micronaut.data.nitrite.runtime.query.GeneratedQueryParser} claims to support.
 *
 * <p>The point of this repository is <em>how it is compiled</em>, not what it declares. The
 * {@code generatedQueryTest} source set deliberately leaves
 * {@code io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder} off its annotation
 * processor path, so {@code RepositoryTypeElementVisitor#newQueryBuilder} cannot find an
 * introspection for the builder named by {@link NitriteRepository} and silently falls back to
 * {@code JpaQueryBuilder}. Every method below therefore compiles to a SQL-shaped string such as
 * {@code SELECT book_ FROM ... WHERE (book_.title = :p1)} rather than to a Nitrite JSON filter,
 * which is the one code path the module's own suite and the Jakarta Data TCK cannot reach —
 * both put the builder on the processor path, so every query they generate is JSON.
 *
 * <p>That gap is not academic: it is the configuration a user gets by following the Nitrite
 * quick start, and it hid a defect where a missing filter map made every read match the whole
 * collection.
 *
 * @see io.micronaut.data.nitrite.runtime.query.GeneratedQueryParser
 */
@NitriteRepository
public interface GeneratedQueryBookRepository extends CrudRepository<GeneratedQueryBook, String> {

  /**
   * Equality against a bound parameter.
   *
   * @param title exact title
   * @return the matching book, if any
   */
  Optional<GeneratedQueryBook> findByTitle(String title);

  /**
   * Ordering comparison.
   *
   * @param pages exclusive lower bound
   * @return books longer than {@code pages}
   */
  List<GeneratedQueryBook> findByPagesGreaterThan(int pages);

  /**
   * Range comparison, which the parser has to keep together with the {@code AND} that bounds it.
   *
   * @param from inclusive lower bound
   * @param to   inclusive upper bound
   * @return books whose page count falls in the range
   */
  List<GeneratedQueryBook> findByPagesBetween(int from, int to);

  /**
   * Membership against a bound collection.
   *
   * @param titles candidate titles
   * @return books with one of the given titles
   */
  List<GeneratedQueryBook> findByTitleIn(Collection<String> titles);

  /**
   * Pattern match, which the parser converts from SQL {@code LIKE} syntax to a regex.
   *
   * @param pattern SQL {@code LIKE} pattern
   * @return matching books
   */
  List<GeneratedQueryBook> findByTitleLike(String pattern);

  /**
   * Null test on an unset field.
   *
   * @return books with no genre
   */
  List<GeneratedQueryBook> findByGenreIsNull();

  /**
   * Negated null test.
   *
   * @return books that carry a genre
   */
  List<GeneratedQueryBook> findByGenreIsNotNull();

  /**
   * Conjunction of two predicates.
   *
   * @param title exact title
   * @param pages exact page count
   * @return matching books
   */
  List<GeneratedQueryBook> findByTitleAndPages(String title, int pages);

  /**
   * Disjunction of two predicates.
   *
   * @param title exact title
   * @param pages exact page count
   * @return matching books
   */
  List<GeneratedQueryBook> findByTitleOrPages(String title, int pages);

  /**
   * Negated equality.
   *
   * @param genre genre to exclude
   * @return books of any other genre
   */
  List<GeneratedQueryBook> findByGenreNotEquals(String genre);

  /**
   * Dotted field reference through a {@code MANY_TO_ONE} association.
   *
   * @param authorId owning author id
   * @return that author's books
   */
  List<GeneratedQueryBook> findByAuthorId(String authorId);

  /**
   * Predicate followed by an {@code ORDER BY} clause, which is applied through Nitrite's find
   * options and so must not be parsed as part of the predicate.
   *
   * @param pages exclusive lower bound
   * @return matching books, shortest title first
   */
  List<GeneratedQueryBook> findByPagesGreaterThanOrderByTitle(int pages);

  /**
   * Predicate followed by an {@code ORDER BY} over a dotted embedded path whose leaf carries a
   * {@code @MappedProperty} rename, so the sort only lands if the path is resolved segment by
   * segment — the same resolution the JSON {@code $sort} branch relies on.
   *
   * @param pages exclusive lower bound
   * @return matching books, by edition label
   */
  List<GeneratedQueryBook> findByPagesGreaterThanOrderByEditionLabel(int pages);

  /**
   * Count projection over a predicate.
   *
   * @param pages exclusive lower bound
   * @return number of matching books
   */
  long countByPagesGreaterThan(int pages);

  /**
   * Deletion driven by a generated predicate rather than by identity.
   *
   * @param genre genre to remove
   * @return number of deleted books
   */
  long deleteByGenre(String genre);

  /**
   * Existence check over a generated predicate.
   *
   * @param title exact title
   * @return whether such a book exists
   */
  boolean existsByTitle(String title);

  /**
   * Negated IN over the generated {@code NOT IN (...)} shape.
   *
   * @param titles the titles to exclude
   * @return the books whose title is not one of {@code titles}
   */
  List<GeneratedQueryBook> findByTitleNotIn(Collection<String> titles);

  /**
   * Negated LIKE over the generated {@code NOT LIKE ?} shape.
   *
   * @param pattern the SQL LIKE pattern to exclude
   * @return the books whose title does not match {@code pattern}
   */
  List<GeneratedQueryBook> findByTitleNotLike(String pattern);

  /**
   * Strict less-than over the generated {@code < ?} shape.
   *
   * @param pages the exclusive upper bound
   * @return the matching books
   */
  List<GeneratedQueryBook> findByPagesLessThan(int pages);

  /**
   * Inclusive less-than over the generated {@code <= ?} shape.
   *
   * @param pages the inclusive upper bound
   * @return the matching books
   */
  List<GeneratedQueryBook> findByPagesLessThanEquals(int pages);

  /**
   * Inclusive greater-than over the generated {@code >= ?} shape.
   *
   * @param pages the inclusive lower bound
   * @return the matching books
   */
  List<GeneratedQueryBook> findByPagesGreaterThanEquals(int pages);

  /**
   * Inequality over the generated {@code != ?} shape.
   *
   * @param pages the page count to exclude
   * @return the matching books
   */
  List<GeneratedQueryBook> findByPagesNotEquals(int pages);

  /**
   * Paged read, so the generated query also carries a LIMIT/OFFSET and a total count.
   *
   * @param pages the exclusive lower bound
   * @param pageable the requested page
   * @return the requested page of matching books
   */
  Page<GeneratedQueryBook> findByPagesGreaterThan(int pages, Pageable pageable);

  /**
   * Single-result DTO projection, so the generated query carries an explicit column list.
   *
   * @param title the title to match
   * @return the projection, or {@code null} when nothing matches
   */
  GeneratedQueryBookDto queryByTitle(String title);

  /**
   * Many-result DTO projection over the same shape as {@link #queryByTitle(String)}.
   *
   * @param title the title to match
   * @return the matching projections
   */
  List<GeneratedQueryBookDto> searchByTitle(String title);
}
