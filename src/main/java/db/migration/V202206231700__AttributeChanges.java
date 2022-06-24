package db.migration;

import com.czertainly.ca.connector.ejbca.util.DatabaseMigration;
import com.czertainly.core.util.AttributeMigrationUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Migration script for the Attributes changes.
 * Prerequisite for the successful migration is to have the AttributeDefinition stored in the database.
 * If the relaxed version of the AttributeDefinition is stored, the migration will fail, including missing
 * type, name, uuid, label.
 */
public class V202206231700__AttributeChanges extends BaseJavaMigration {

    private static final String AUTHORITY_INSTANCE_TABLE_NAME = "authority_instance";

    private static final String ATTRIBUTE_COLUMN_NAME = "attributes";

    @Override
    public Integer getChecksum() {
        return DatabaseMigration.JavaMigrationChecksums.V202206231700__AttributeChanges.getChecksum();
    }

    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            applyAuthorityInstanceMigration(context);
        }
    }

    private void applyAuthorityInstanceMigration(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, attributes FROM authority_instance ORDER BY id")) {
                List<String> migrationCommands = AttributeMigrationUtils.getMigrationCommands(rows, AUTHORITY_INSTANCE_TABLE_NAME, ATTRIBUTE_COLUMN_NAME);
                executeCommands(select, migrationCommands);
            }
        }
    }

    private void executeCommands(Statement select, List<String> commands) throws SQLException {
        for(String command: commands) {
            select.execute(command);
        }
    }
}