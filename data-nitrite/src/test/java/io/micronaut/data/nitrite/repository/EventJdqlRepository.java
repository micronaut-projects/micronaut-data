package io.micronaut.data.nitrite.repository;

import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.Event;
import io.micronaut.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import java.math.BigDecimal;

/**
 * Event queries written in JDQL. Kept apart from {@link EventRepository} because that one uses
 * Micronaut's own {@code @Query} annotation, whose simple name collides with the Jakarta Data one.
 */
@NitriteRepository
public interface EventJdqlRepository extends CrudRepository<Event, String> {

  /**
   * Applies a JDQL arithmetic subtraction to an event amount.
   *
   * @param type event type
   * @param delta amount to subtract
   * @return number of updated events
   */
  @Query("UPDATE Event SET amount = amount - :delta WHERE type = :type")
  int subtractAmountByType(String type, BigDecimal delta);

  /**
   * Applies a JDQL arithmetic subtraction using a literal constant (not a bound parameter).
   *
   * @param type event type
   * @return number of updated events
   */
  @Query("UPDATE Event SET amount = amount - 15 WHERE type = :type")
  int subtractLiteralAmountByType(String type);

  /**
   * Applies a JDQL arithmetic division to an event amount.
   *
   * @param type event type
   * @param divisor amount to divide by
   * @return number of updated events
   */
  @Query("UPDATE Event SET amount = amount / :divisor WHERE type = :type")
  int divideAmountByType(String type, BigDecimal divisor);
}
