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
package io.micronaut.data.document.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class TxBookService extends AbstractBookService {

    public TxBookService(ApplicationContext beanContext) {
        super(beanContext);
    }

    @Transactional(readOnly = true)
    public void bookAddedInReadOnlyTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @Transactional(readOnly = true)
    public void readOnlyTxCallingAddingBookInAnotherTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    protected void addBook() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    public void bookAddedInMandatoryTransaction() {
        mandatoryTransaction();
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    public void bookAddedInNoSupportedPropagation(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
    public void bookAddedInNeverPropagation(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
    }

    @jakarta.transaction.Transactional
    public void bookAddedInInnerNeverPropagation(Runnable noTxCheck) {
        bookAddedInNeverPropagation(noTxCheck);
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    public void bookAddedInNoSupportedPropagationAndFailed(Runnable noTxCheck) {
        bookRepository.save(newBook("MandatoryBook"));
        noTxCheck.run();
        throw new IllegalStateException("Big fail!");
    }

    @jakarta.transaction.Transactional
    public void bookAddedInInnerNoSupportedPropagation(Runnable noTxCheck) {
        bookAddedInNoSupportedPropagation(noTxCheck);
    }

    @jakarta.transaction.Transactional
    public void bookAddedInInnerNoSupportedPropagationFailedAndExceptionSuppressed(Runnable noTxCheck) {
        try {
            bookAddedInNoSupportedPropagationAndFailed(noTxCheck);
        } catch (Exception e) {
            // Ignore
        }
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.MANDATORY)
    public void mandatoryTransaction() {
        bookRepository.save(newBook("MandatoryBook"));
    }

    @jakarta.transaction.Transactional
    public void checkInTransaction(Runnable runnable) {
        runnable.run();
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedInTxMethod() {
        bookRepository.save(newBook("Toys"));
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedInAnotherRequiresNewTx() {
        addBookRequiresNew();
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedInAnotherRequiresNewTxWhichIsFailing() {
        addBookRequiresNewFailing();
    }

    @jakarta.transaction.Transactional
    public void bookIsAddedAndAnotherRequiresNewTxIsFailing() {
        bookRepository.save(newBook("Book1"));
        transactionRequiresNewFailing();
    }

    @jakarta.transaction.Transactional
    public void innerTransactionHasSuppressedException() {
        try {
            transactionFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional
    public void innerRequiresNewTransactionHasSuppressedException() {
        try {
            transactionRequiresNewFailing();
        } catch (Exception e) {
            // Ignore
        }
        bookRepository.save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected void addBookRequiresNew() {
        bookRepository.save(newBook("Book1"));
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected void addBookRequiresNewFailing() {
        bookRepository.save(newBook("Book2"));
        throw new IllegalStateException("Big fail!");
    }

    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.REQUIRES_NEW)
    protected void transactionRequiresNewFailing() {
        throw new IllegalStateException("Big fail!");
    }

    @jakarta.transaction.Transactional
    protected void transactionFailing() {
        throw new IllegalStateException("Big fail!");
    }

}
