package db.migration;

import com.meetple.backend.domain.image.config.ImageStorageProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7__StoreUploadedImageObjectKeys extends BaseJavaMigration {

    private static final Pattern GENERATED_IMAGE_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$"
    );

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addObjectKeyColumns(connection);

        String rawKeyPrefix = context.getConfiguration()
                .getPlaceholders()
                .get("imageStorageKeyPrefix");
        String keyPrefix = ImageStorageProperties.normalizeKeyPrefix(rawKeyPrefix);

        backfillMemberProfileImages(connection, keyPrefix);
        backfillMeetingThumbnails(connection, keyPrefix);
        backfillMeetingImages(connection, keyPrefix);

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE meeting_images ALTER COLUMN image_url DROP NOT NULL");
        }
    }

    private void addObjectKeyColumns(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE members "
                    + "ADD COLUMN profile_image_object_key VARCHAR(255)");
            statement.execute("ALTER TABLE meetings "
                    + "ADD COLUMN thumbnail_image_object_key VARCHAR(255)");
            statement.execute("ALTER TABLE meeting_images "
                    + "ADD COLUMN object_key VARCHAR(255)");
        }
    }

    private void backfillMemberProfileImages(Connection connection, String keyPrefix)
            throws SQLException {
        backfillOwnedImages(
                connection,
                "SELECT id, id AS owner_id, profile_image_url AS image_url FROM members",
                "UPDATE members SET profile_image_object_key = ? WHERE id = ?",
                keyPrefix,
                "profile"
        );
    }

    private void backfillMeetingThumbnails(Connection connection, String keyPrefix)
            throws SQLException {
        backfillOwnedImages(
                connection,
                "SELECT id, host_id AS owner_id, thumbnail_image_url AS image_url FROM meetings",
                "UPDATE meetings SET thumbnail_image_object_key = ? WHERE id = ?",
                keyPrefix,
                "meeting"
        );
    }

    private void backfillMeetingImages(Connection connection, String keyPrefix)
            throws SQLException {
        backfillOwnedImages(
                connection,
                "SELECT mi.id, m.host_id AS owner_id, mi.image_url "
                        + "FROM meeting_images mi "
                        + "JOIN meetings m ON m.id = mi.meeting_id",
                "UPDATE meeting_images SET object_key = ? WHERE id = ?",
                keyPrefix,
                "meeting"
        );
    }

    private void backfillOwnedImages(
            Connection connection,
            String selectSql,
            String updateSql,
            String keyPrefix,
            String purpose
    ) throws SQLException {
        try (Statement selectStatement = connection.createStatement();
             ResultSet rows = selectStatement.executeQuery(selectSql);
             PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
            while (rows.next()) {
                String objectKey = extractOwnedObjectKey(
                        rows.getString("image_url"),
                        keyPrefix,
                        purpose,
                        rows.getLong("owner_id")
                );
                if (objectKey == null) {
                    continue;
                }

                updateStatement.setString(1, objectKey);
                updateStatement.setLong(2, rows.getLong("id"));
                updateStatement.addBatch();
            }
            updateStatement.executeBatch();
        }
    }

    private String extractOwnedObjectKey(
            String imageUrl,
            String keyPrefix,
            String purpose,
            long ownerId
    ) {
        if (imageUrl == null) {
            return null;
        }

        String ownerPrefix = String.join("/", keyPrefix, purpose, Long.toString(ownerId)) + "/";
        int objectKeyStart = imageUrl.indexOf(ownerPrefix);
        if (objectKeyStart < 0) {
            return null;
        }

        String objectKey = imageUrl.substring(objectKeyStart);
        String fileName = objectKey.substring(ownerPrefix.length());
        return GENERATED_IMAGE_FILE_NAME.matcher(fileName).matches() ? objectKey : null;
    }
}
