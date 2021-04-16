package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedDatabaseContainerTestPropertyProvider

trait PostgresTestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    @Override
    int sharedSpecsCount() {
        return 6
    }

    @Override
    boolean usePool() {
        // Enable pool to disable exceptions:
//        reactor.core.publisher.FluxUsingWhen - Async resource cleanup failed after cancel
//        io.r2dbc.postgresql.client.ReactorNettyClient$PostgresConnectionClosedException: Connection closed
//        at io.r2dbc.postgresql.client.ReactorNettyClient.lambda$static$1(ReactorNettyClient.java:102)
//        at io.r2dbc.postgresql.client.ReactorNettyClient$BackendMessageSubscriber.close(ReactorNettyClient.java:1018)
//        at io.r2dbc.postgresql.client.ReactorNettyClient.drainError(ReactorNettyClient.java:518)
//        at io.r2dbc.postgresql.client.ReactorNettyClient.lambda$close$8(ReactorNettyClient.java:191)
//        at reactor.core.publisher.MonoDefer.subscribe(MonoDefer.java:44)
//        at reactor.core.publisher.Mono.subscribe(Mono.java:3987)
//        at reactor.core.publisher.MonoIgnoreThen$ThenIgnoreMain.drain(MonoIgnoreThen.java:173)
//        at reactor.core.publisher.MonoIgnoreThen.subscribe(MonoIgnoreThen.java:56)
//        at reactor.core.publisher.FluxFromMonoOperator.subscribe(FluxFromMonoOperator.java:83)
//        at reactor.core.publisher.FluxUsingWhen$UsingWhenSubscriber.cancel(FluxUsingWhen.java:342)
//        at reactor.core.publisher.FluxUsingWhen$ResourceSubscriber.cancel(FluxUsingWhen.java:253)
//        at reactor.core.publisher.MonoNext$NextSubscriber.onNext(MonoNext.java:81)
//        at reactor.core.publisher.FluxUsingWhen$UsingWhenSubscriber.onNext(FluxUsingWhen.java:358){
        return false
    }
}
