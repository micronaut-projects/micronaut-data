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
package io.micronaut.transaction.exceptions;

import io.micronaut.core.annotation.NonNull;
import java.util.Locale;

/**
 * Exception that represents a transaction failure caused by a heuristic
 * decision on the side of the transaction coordinator.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 17.03.2003
 */
@SuppressWarnings("serial")
public class HeuristicCompletionException extends TransactionException {

    /**
     * Possible state outcomes.
     */
    public enum State {
        /**
         * Unknown outcome state.
         */
        UNKNOWN,
        /**
         * Committed outcome state.
         */
        COMMITTED,
        /**
         * Rolled back outcome state.
         */
        ROLLED_BACK,
        /**
         * Mixed outcome state.
         */
        MIXED;

        private final String str;

        /**
         * Default constructor.
         */
        State() {
            str = name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        }

        @Override
        public String toString() {
            return str;
        }
    }

    /**
     * The outcome state of the transaction: have some or all resources been committed?
     */
    private final State outcomeState;

    /**
     * Constructor for HeuristicCompletionException.
     * @param outcomeState the outcome state of the transaction
     * @param cause the root cause from the transaction API in use
     */
    public HeuristicCompletionException(@NonNull State outcomeState, Throwable cause) {
        super("Heuristic completion: outcome state is " + outcomeState.toString(), cause);
        this.outcomeState = outcomeState;
    }

    /**
     * @return Return the outcome state of the transaction state,
     * as one of the constants in this class.
     * @see State#UNKNOWN
     * @see State#COMMITTED
     * @see State#ROLLED_BACK
     * @see State#MIXED
     */
    public @NonNull State getOutcomeState() {
        return this.outcomeState;
    }

}

