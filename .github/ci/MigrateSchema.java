import org.flywaydb.core.Flyway;

/**
 * Migrates a schema from nothing to head, for the acceptance job in
 * {@code .github/workflows/maven.yml}. Run as a single-file source program with the account
 * module's classpath, so {@code classpath:db/migration} is the project's own migrations:
 *
 * <pre>
 * java -cp "account/target/classes:$(cat cp.txt)" .github/ci/MigrateSchema.java &lt;url&gt; &lt;user&gt; &lt;password&gt;
 * </pre>
 *
 * <p>It exists because the gated acceptance classes need a schema that is already at head -
 * the application migrates one on start-up and nothing else here does. Building it <em>from
 * nothing</em> on every run is the point rather than a convenience: `V55`, `V56` and `V57` each
 * shipped a migration that could not apply to a fresh database, and no green build could see
 * it, because a migration is only wrong when MySQL reads it.
 *
 * <p>{@code validateOnMigrate(false)} matches what {@code DatabaseMigrationService} does.
 */
public final class MigrateSchema {

    public static void main(String[] args) {
        var result = Flyway.configure()
                .dataSource(args[0], args[1], args.length > 2 ? args[2] : "")
                .locations("classpath:db/migration")
                .validateOnMigrate(false)
                .load()
                .migrate();
        System.out.println("migrations=" + result.migrationsExecuted
                + " target=" + result.targetSchemaVersion);
    }
}
