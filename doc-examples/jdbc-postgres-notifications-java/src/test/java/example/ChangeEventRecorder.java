package example;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.annotation.ChangeListener;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.notification.ChangeEvent;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Records the events delivered to the example after its handler has completed. */
@Singleton
@InterceptorBean(ChangeListener.class)
class ChangeEventRecorder implements MethodInterceptor<Object, Object> {
    private final BlockingQueue<ChangeEvent<Book>> events = new LinkedBlockingQueue<>();

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        Object result = context.proceed();
        events.add((ChangeEvent<Book>) context.getParameterValues()[0]);
        return result;
    }

    @Nullable ChangeEvent<Book> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return events.poll(timeout, unit);
    }

    /** Counts real repository calls independently of reads performed by listener workers. */
    @Singleton
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @InterceptorBean(Repository.class)
    static class RepositoryReadCounter implements MethodInterceptor<Object, Object> {
        private final ThreadLocal<Map<String, Integer>> calls = ThreadLocal.withInitial(HashMap::new);

        @Override
        public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
            calls.get().merge(context.getMethodName(), 1, Integer::sum);
            return context.proceed();
        }

        void reset() {
            calls.remove();
        }

        int count(String method) {
            return calls.get().getOrDefault(method, 0);
        }
    }
}
