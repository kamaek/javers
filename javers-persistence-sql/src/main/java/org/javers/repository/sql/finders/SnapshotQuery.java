package org.javers.repository.sql.finders;

import org.javers.common.string.ToStringBuilder;
import org.javers.core.json.CdoSnapshotSerialized;
import org.javers.repository.api.QueryParams;
import org.javers.repository.sql.schema.TableNameProvider;
import org.javers.repository.sql.session.ObjectMapper;
import org.javers.repository.sql.session.SelectBuilder;
import org.javers.repository.sql.session.Session;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.javers.repository.sql.schema.FixedSchemaFactory.*;
import static org.javers.repository.sql.schema.FixedSchemaFactory.GLOBAL_ID_TYPE_NAME;
import static org.javers.repository.sql.session.Parameter.*;

class SnapshotQuery {
    private final QueryParams queryParams;
    private final SelectBuilder selectBuilder;
    private final TableNameProvider tableNameProvider;
    private final CdoSnapshotMapper cdoSnapshotMapper = new CdoSnapshotMapper();

    public SnapshotQuery(TableNameProvider tableNames, QueryParams queryParams, Session session) {
        this.selectBuilder = session
            .select(
                SNAPSHOT_STATE + ", " +
                SNAPSHOT_TYPE + ", " +
                SNAPSHOT_VERSION + ", " +
                SNAPSHOT_CHANGED + ", " +
                SNAPSHOT_MANAGED_TYPE + ", " +
                COMMIT_PK + ", " +
                COMMIT_AUTHOR + ", " +
                COMMIT_COMMIT_DATE + ", " +
                COMMIT_COMMIT_ID + ", " +
                "g." + GLOBAL_ID_LOCAL_ID + ", " +
                "g." + GLOBAL_ID_FRAGMENT + ", " +
                "g." + GLOBAL_ID_OWNER_ID_FK + ", " +
                "o." + GLOBAL_ID_LOCAL_ID + " owner_" + GLOBAL_ID_LOCAL_ID + ", " +
                "o." + GLOBAL_ID_FRAGMENT + " owner_" + GLOBAL_ID_FRAGMENT + ", " +
                "o." + GLOBAL_ID_TYPE_NAME + " owner_" + GLOBAL_ID_TYPE_NAME
            )
            .from(
                tableNames.getSnapshotTableNameWithSchema() +
                " INNER JOIN " + tableNames.getCommitTableNameWithSchema() + " ON " + COMMIT_PK + " = " + SNAPSHOT_COMMIT_FK +
                " INNER JOIN " + tableNames.getGlobalIdTableNameWithSchema() + " g ON g." + GLOBAL_ID_PK + " = " + SNAPSHOT_GLOBAL_ID_FK +
                " LEFT OUTER JOIN " + tableNames.getGlobalIdTableNameWithSchema() + " o ON o." + GLOBAL_ID_PK + " = g." + GLOBAL_ID_OWNER_ID_FK)
            .queryName("select snapshots");

        this.queryParams = queryParams;
        this.tableNameProvider = tableNames;
        applyQueryParams();
    }

    private void applyQueryParams() {
        queryParams.changedProperty().ifPresent(changedProperty -> {
            selectBuilder.and(SNAPSHOT_CHANGED, "like", stringParam("%\"" + queryParams.changedProperty().get() +"\"%"));
        });

        queryParams.from().ifPresent(from -> {
            selectBuilder.and(COMMIT_COMMIT_DATE, ">=", localDateTimeParam(from));
        });

        queryParams.to().ifPresent(to -> {
            selectBuilder.and(COMMIT_COMMIT_DATE, "<=", localDateTimeParam(to));
        });

        queryParams.toCommitId().ifPresent(commitId -> {
            selectBuilder.and(COMMIT_COMMIT_ID, "<=", bigDecimalParam(commitId.valueAsNumber()));
        });

        if (queryParams.commitIds().size() > 0) {
            selectBuilder.and(COMMIT_COMMIT_ID + " IN (" + ToStringBuilder.join(
                    queryParams.commitIds().stream().map(c -> c.valueAsNumber()).collect(toList())) + ")");
        }

        queryParams.version().ifPresent(ver -> selectBuilder.and(SNAPSHOT_VERSION, ver));

        queryParams.author().ifPresent(author -> selectBuilder.and(COMMIT_AUTHOR, author));

        if (queryParams.commitProperties().size() > 0) {
            for (Map.Entry<String, String> commitProperty : queryParams.commitProperties().entrySet()) {
                addCommitPropertyCondition(selectBuilder, commitProperty.getKey(), commitProperty.getValue());
            }
        }

        queryParams.snapshotType().ifPresent(snapshotType -> selectBuilder.and(SNAPSHOT_TYPE, snapshotType.name()));
    }

    void addSnapshotPkFilter(long snapshotPk) {
        selectBuilder.and(SNAPSHOT_PK, snapshotPk);
    }

    void addGlobalIdFilter(long globalIdPk) {
        if (!queryParams.isAggregate()) {
            selectBuilder.and("g." + GLOBAL_ID_PK, globalIdPk);
        }
        else {
            selectBuilder.and("( g." + GLOBAL_ID_PK + " = ? OR g." + GLOBAL_ID_OWNER_ID_FK + " = ? )",
                    longParam(globalIdPk), longParam(globalIdPk));
        }
    }

    List<CdoSnapshotSerialized> run() {
        selectBuilder.orderByDesc(SNAPSHOT_PK);
        selectBuilder.limit(queryParams.limit(), queryParams.skip());
        return selectBuilder.executeQuery(cdoSnapshotMapper);
    }

    private void addCommitPropertyCondition(SelectBuilder selectBuilder, String propertyName, String propertyValue) {
        selectBuilder.and("EXISTS (" +
                "SELECT * FROM " + tableNameProvider.getCommitPropertyTableNameWithSchema() +
                " WHERE " + COMMIT_PROPERTY_COMMIT_FK + " = " + COMMIT_PK +
                " AND " + COMMIT_PROPERTY_NAME + " = ?" +
                " AND " + COMMIT_PROPERTY_VALUE + " ?)",
                stringParam(propertyName), stringParam(propertyValue));
    }

    private static class CdoSnapshotMapper implements ObjectMapper<CdoSnapshotSerialized> {
        @Override
        public CdoSnapshotSerialized get(ResultSet resultSet) throws SQLException {
            return new CdoSnapshotSerialized()
                    .withCommitAuthor(resultSet.getString(COMMIT_AUTHOR))
                    .withCommitDate(resultSet.getTimestamp(COMMIT_COMMIT_DATE))
                    .withCommitId(resultSet.getBigDecimal(COMMIT_COMMIT_ID))
                    .withCommitPk(resultSet.getLong(COMMIT_PK))
                    .withVersion(resultSet.getLong(SNAPSHOT_VERSION))
                    .withSnapshotState(resultSet.getString(SNAPSHOT_STATE))
                    .withChangedProperties(resultSet.getString(SNAPSHOT_CHANGED))
                    .withSnapshotType(resultSet.getString(SNAPSHOT_TYPE))
                    .withGlobalIdFragment(resultSet.getString(GLOBAL_ID_FRAGMENT))
                    .withGlobalIdLocalId(resultSet.getString(GLOBAL_ID_LOCAL_ID))
                    .withGlobalIdTypeName(resultSet.getString(SNAPSHOT_MANAGED_TYPE))
                    .withOwnerGlobalIdFragment(resultSet.getString("owner_" + GLOBAL_ID_FRAGMENT))
                    .withOwnerGlobalIdLocalId(resultSet.getString("owner_" + GLOBAL_ID_LOCAL_ID))
                    .withOwnerGlobalIdTypeName(resultSet.getString("owner_" + GLOBAL_ID_TYPE_NAME));
        }
    }
}
