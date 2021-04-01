package example;

import com.mysql.cj.exceptions.*;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;

@TypeHint({
        AssertionFailedException.class,
        CJCommunicationsException.class,
        CJConnectionFeatureNotAvailableException.class,
        CJException.class,
        CJOperationNotSupportedException.class,
        CJPacketTooBigException.class,
        CJTimeoutException.class,
        ClosedOnExpiredPasswordException.class,
        ConnectionIsClosedException.class,
        DataConversionException.class,
        DataReadException.class,
        DataTruncationException.class,
        FeatureNotAvailableException.class,
        InvalidConnectionAttributeException.class,
        MysqlErrorNumbers.class,
        NumberOutOfRange.class,
        OperationCancelledException.class,
        PasswordExpiredException.class,
        PropertyNotModifiableException.class,
        RSAException.class,
        SSLParamsException.class,
        StatementIsClosedException.class,
        UnableToConnectException.class,
        UnsupportedConnectionStringException.class,
        WrongArgumentException.class
})
public class Application {
    public static void main(String...args) {
        Micronaut.run(args);
    }
}
