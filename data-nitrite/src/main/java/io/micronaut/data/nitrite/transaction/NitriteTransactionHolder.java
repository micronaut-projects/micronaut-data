/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.transaction;

import io.micronaut.core.annotation.Nullable;

/**
 * Holder for the current Nitrite transaction context.
 *
 * @since 5.2.0
 */
public class NitriteTransactionHolder {

  /*
   * Deliberately an instance field, not static: one holder (and one ThreadLocal slot) exists per
   * datasource, since a per-datasource singleton bean owns it (see NitriteOperationsFactory). A
   * static ThreadLocal would be shared across datasources, letting one datasource's bind() clobber
   * another's transaction context on the same thread.
   */
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
