package example;

import io.micronaut.context.annotation.Context;
import io.micronaut.r2dbc.rxjava2.RxConnectionFactory;


// just tests RxConnectionFactory is injectable
@Context
public class RxConnectionFactoryTest {
    private final RxConnectionFactory rxConnectionFactory;

    public RxConnectionFactoryTest(RxConnectionFactory rxConnectionFactory) {
        this.rxConnectionFactory = rxConnectionFactory;
    }
}
