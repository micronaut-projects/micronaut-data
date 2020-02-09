package io.micronaut.data.cql.operations;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.runtime.mapper.QueryStatement;

import javax.annotation.Nonnull;
import java.util.UUID;

public interface CqlQueryStatment<PS, IDX> extends QueryStatement<PS, IDX> {

    default @NonNull
    QueryStatement<PS, IDX> SetUUID(PS statement, IDX name, UUID value) {
        setValue(statement,name,value);
        return this;
    }
}
