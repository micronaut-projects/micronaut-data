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

import java.util.HashMap;
import java.util.Map;

/**
 * Holder for the current Nitrite transaction context.
 *
 * @since 5.2.0
 */
public class NitriteTransactionHolder {

  /*
   * One static ThreadLocal keyed by holder, rather than a ThreadLocal field per holder: a
   * ThreadLocal instance field pins per-instance state onto every thread that touches it. Keying
   * keeps datasources isolated all the same - one holder is one per-datasource singleton bean (see
   * NitriteOperationsFactory), so one datasource's bind() cannot clobber another's context on the
   * same thread. The map is dropped once its last entry is unbound, so a pooled thread retains
   * nothing after its transactions end.
   */
  private static final ThreadLocal<Map<NitriteTransactionHolder, NitriteTransactionContext>> CONTEXTS =
      new ThreadLocal<>();

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
      unbind();
      return;
    }
    Map<NitriteTransactionHolder, NitriteTransactionContext> contexts = CONTEXTS.get();
    if (contexts == null) {
      contexts = new HashMap<>(2);
      CONTEXTS.set(contexts);
    }
    contexts.put(this, context);
  }

  private void unbind() {
    Map<NitriteTransactionHolder, NitriteTransactionContext> contexts = CONTEXTS.get();
    if (contexts == null) {
      return;
    }
    contexts.remove(this);
    if (contexts.isEmpty()) {
      CONTEXTS.remove();
    }
  }

  /**
   * Returns the current transaction context.
   *
   * @return the context, or null if none bound
   */
  @Nullable
  public NitriteTransactionContext get() {
    Map<NitriteTransactionHolder, NitriteTransactionContext> contexts = CONTEXTS.get();
    return contexts != null ? contexts.get(this) : null;
  }

  /**
   * Clears the current transaction context.
   */
  public void clear() {
    unbind();
  }

  /**
   * Returns whether a transaction is active on the current thread.
   *
   * @return true if active
   */
  public boolean isActive() {
    return get() != null;
  }
}
