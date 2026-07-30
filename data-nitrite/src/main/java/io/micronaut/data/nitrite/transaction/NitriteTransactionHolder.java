package io.micronaut.data.nitrite.transaction;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Holder for the current Nitrite transaction context.
 *
 * @since 1.0.0
 */
@Singleton
public class NitriteTransactionHolder {

  private final ThreadLocal<NitriteTransactionContext> current = new ThreadLocal<>();

  /**
   * Default constructor.
   */
  public NitriteTransactionHolder() {
  }

  /**
   * Bind a transaction context to the current thread.
   *
   * @param context the context
   */
  public void bind(@Nullable final NitriteTransactionContext context) {
    if (context == null) {
      current.remove();
    } else {
      current.set(context);
    }
  }

  /**
   * Returns the current transaction context.
   *
   * @return the context, or null if none bound
   */
  @Nullable
  public NitriteTransactionContext get() {
    return current.get();
  }

  /**
   * Clears the current transaction context.
   */
  public void clear() {
    current.remove();
  }

  /**
   * Returns whether a transaction is active on the current thread.
   *
   * @return true if active
   */
  public boolean isActive() {
    return current.get() != null;
  }
}
