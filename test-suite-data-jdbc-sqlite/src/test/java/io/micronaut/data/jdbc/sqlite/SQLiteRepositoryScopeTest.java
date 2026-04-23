package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.data.runtime.intercept.DataInterceptorResolver;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties
class SQLiteRepositoryScopeTest {

    private static final Field INTERCEPTORS_FIELD = interceptorsField();

    @Inject
    BeanContext beanContext;

    private DataInterceptorResolver dataInterceptor;
    private SQLiteBookRepository bookRepository;

    @Test
    void testDefaultRepositoryScopeIsPrototype() {
        SQLiteBookRepository instance1 = beanContext.getBean(SQLiteBookRepository.class);
        SQLiteBookRepository instance2 = beanContext.getBean(SQLiteBookRepository.class);

        assertNotSame(instance1, instance2);
    }

    @Test
    void testExplicitSingletonRepositoryScopeIsHonored() {
        SQLiteBookDtoRepository instance1 = beanContext.getBean(SQLiteBookDtoRepository.class);
        SQLiteBookDtoRepository instance2 = beanContext.getBean(SQLiteBookDtoRepository.class);

        assertSame(instance1, instance2);
    }

    @Test
    void testNoMemoryLeak1() {
        DataInterceptorResolver resolver = getDataInterceptor();
        SQLiteBookRepository instance = beanContext.getBean(SQLiteBookRepository.class);

        for (int i = 0; i < 30000; i++) {
            instance.deleteAll();
            assertTrue(interceptorCount(resolver) < 10000);
        }
    }

    @Test
    void testNoMemoryLeak2() {
        DataInterceptorResolver resolver = getDataInterceptor();
        SQLiteBookRepository instance = getBookRepository();

        for (int i = 0; i < 30000; i++) {
            instance.deleteAll();
            assertTrue(interceptorCount(resolver) < 10000);
        }
    }

    @Test
    void testNoMemoryLeak3() {
        DataInterceptorResolver resolver = getDataInterceptor();
        MyPrototypeService myService = beanContext.getBean(MyPrototypeService.class);

        myService.getBookRepository().deleteAll();
        for (int i = 0; i < 30000; i++) {
            assertTrue(interceptorCount(resolver) < 10000);
        }
    }

    private DataInterceptorResolver getDataInterceptor() {
        if (dataInterceptor == null) {
            dataInterceptor = beanContext.getBean(DataInterceptorResolver.class);
        }
        return dataInterceptor;
    }

    private SQLiteBookRepository getBookRepository() {
        if (bookRepository == null) {
            bookRepository = beanContext.getBean(SQLiteBookRepository.class);
        }
        return bookRepository;
    }

    @SuppressWarnings("unchecked")
    private int interceptorCount(DataInterceptorResolver resolver) {
        try {
            return ((Map<Object, Object>) INTERCEPTORS_FIELD.get(resolver)).size();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access interceptor cache", e);
        }
    }

    private static Field interceptorsField() {
        try {
            Field field = DataInterceptorResolver.class.getDeclaredField("interceptors");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Unable to locate interceptor cache field", e);
        }
    }

    @Prototype
    static class MyPrototypeService {

        private final SQLiteBookRepository bookRepository;

        MyPrototypeService(SQLiteBookRepository bookRepository) {
            this.bookRepository = bookRepository;
        }

        SQLiteBookRepository getBookRepository() {
            return bookRepository;
        }
    }
}
