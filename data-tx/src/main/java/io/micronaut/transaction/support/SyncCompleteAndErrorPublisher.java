/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A publisher that will execute an action on complete, on throwable and delegate to downstream after it's complted.
 *
 * @param <T> The producing type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
final class SyncCompleteAndErrorPublisher<T> implements CorePublisher<T> {

    private final CorePublisher<T> actualPublisher;
    private final Supplier<Publisher<Void>> onComplete;
    private final Function<Throwable, Publisher<Void>> onThrowable;
    private final boolean isMono;

    SyncCompleteAndErrorPublisher(CorePublisher<T> actualPublisher,
                                  Supplier<Publisher<Void>> onComplete,
                                  Function<Throwable, Publisher<Void>> onThrowable,
                                  boolean isMono) {
        this.actualPublisher = actualPublisher;
        this.onComplete = onComplete;
        this.onThrowable = onThrowable;
        this.isMono = isMono;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actualSubscriber) {
        doSubscribe(actualSubscriber, actualSubscriber);
    }

    @Override
    public void subscribe(Subscriber<? super T> actualSubscriber) {
        if (actualSubscriber instanceof CoreSubscriber<? super T> coreSubscriber) {
            doSubscribe(actualSubscriber, coreSubscriber);
        } else {
            doSubscribe(actualSubscriber, null);
        }
    }

    private void doSubscribe(Subscriber<? super T> actualSubscriber, @Nullable CoreSubscriber<? super T> coreSubscriber) {
        actualPublisher.subscribe(new CoreSubscriber<>() {

            Subscription actualSubscription;

            @Override
            public Context currentContext() {
                if (coreSubscriber == null) {
                    return Context.empty();
                }
                return coreSubscriber.currentContext();
            }

            @Override
            public void onSubscribe(Subscription s) {
                actualSubscription = s;
                actualSubscriber.onSubscribe(s);
            }

            @Override
            public void onNext(T t) {
                if (isMono) {
                    actualSubscription.cancel();
                    onComplete.get().subscribe(new Subscriber<>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            s.request(1);
                        }

                        @Override
                        public void onNext(Void unused) {

                        }

                        @Override
                        public void onError(Throwable t) {
                            actualSubscriber.onError(t);
                        }

                        @Override
                        public void onComplete() {
                            actualSubscriber.onNext(t);
                            actualSubscriber.onComplete();
                        }
                    });
                } else {
                    actualSubscriber.onNext(t);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                onThrowable.apply(throwable).subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onNext(Void unused) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        actualSubscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        actualSubscriber.onError(throwable);

                    }
                });
            }

            @Override
            public void onComplete() {
                onComplete.get().subscribe(new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onNext(Void unused) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        actualSubscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        actualSubscriber.onComplete();
                    }
                });
            }

        });
    }

}
