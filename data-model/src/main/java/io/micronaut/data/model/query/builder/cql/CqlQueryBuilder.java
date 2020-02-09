package io.micronaut.data.model.query.builder.cql;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;

import java.util.Map;

public class CqlQueryBuilder extends AbstractSqlLikeQueryBuilder implements QueryBuilder {

   public CqlQueryBuilder(){

   }

    @Override
    protected String getTableName(PersistentEntity entity) {
        return entity.getName();
    }

    @Override
    protected String[] buildJoin(String alias, JoinPath joinPath, String joinType, StringBuilder stringBuilder, Map<String, String> appliedJoinPaths, QueryState queryState) {
        return new String[0];
    }

    @Override
    protected String getColumnName(PersistentProperty persistentProperty) {
        return null;
    }

    @Override
    protected void selectAllColumns(QueryState queryState) {

    }

    @Override
    protected void appendProjectionRowCount(StringBuilder queryString, String logicalName) {

    }

    @Override
    protected boolean computePropertyPaths() {
        return false;
    }

    @Override
    protected boolean isAliasForBatch() {
        return false;
    }

    @Override
    protected Placeholder formatParameter(int index) {
        return null;
    }

    @Override
    public String resolveJoinType(Join.Type jt) {
        return null;
    }

    @Nullable
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        return null;
    }

    @NonNull
    @Override
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        return null;
    }
}
