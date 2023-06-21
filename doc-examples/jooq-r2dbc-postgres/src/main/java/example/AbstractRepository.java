package example;

import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.r2dbc.spi.Connection;
import org.jooq.DSLContext;
import org.jooq.Publisher;
import org.jooq.impl.DSL;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.function.Function;

public class AbstractRepository {

    protected final DSLContext ctx;
    protected final ReactorReactiveTransactionOperations<Connection> transactionOperations;

    public AbstractRepository(DSLContext db, ReactorReactiveTransactionOperations<Connection> transactionOperations) {
        this.ctx = db;
        this.transactionOperations = transactionOperations;
    }

    protected <T> Flux<T> withDSLContextFlux(Function<DSLContext, Publisher<T>> fn) {
        return Flux.deferContextual(contextView -> {
            ReactiveTransactionStatus<Connection> status = getTransactionStatus(contextView);
            if (status == null) {
                return Flux.from(fn.apply(ctx));
            }
            return Flux.from(fn.apply(DSL.using(status.getConnection())));
        });
    }

    protected <T> Mono<T> withDSLContextMono(Function<DSLContext, Publisher<T>> fn) {
        return Mono.deferContextual(contextView -> {
            ReactiveTransactionStatus<Connection> status = getTransactionStatus(contextView);
            if (status == null) {
                return Mono.from(fn.apply(ctx));
            }
            return Mono.from(fn.apply(DSL.using(status.getConnection())));
        });
    }

    private ReactiveTransactionStatus<Connection> getTransactionStatus(ContextView contextView) {
        return transactionOperations.findTransactionStatus(contextView).orElse(null);
    }
}
