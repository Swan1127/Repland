package com.swan1127.repland.data.room

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the database migration chain.
 *
 * Why this is a source-level test rather than a database test: the two things that actually
 * break upgrades here are both *consistency* defects between two hand-maintained lists —
 *
 *   1. `ReplandDatabase`'s companion declares `MIGRATION_a_b` objects;
 *   2. `AppContainer` registers them by hand in `addMigrations(...)`.
 *
 * A migration added to (1) and forgotten in (2) compiles, passes every other test, and then
 * throws `IllegalStateException: A migration from N to N+1 was required but not found` for
 * every existing user on upgrade. Room's `@Database` annotation has CLASS retention, so the
 * version cannot be read reflectively, and the companion's migrations are anonymous objects
 * that do not reflect as `Migration` fields — both verified by experiment. Reading the two
 * source files is therefore the only way to assert the pairing without a device.
 *
 * What this proves: the declared chain is contiguous, its end matches the declared database
 * version, every declared name agrees with its own version pair, and the registration list
 * matches the declarations exactly.
 *
 * What this does NOT prove: that the SQL inside each migration actually runs. That needs a
 * real database (Robolectric or an instrumented test).
 */
class DatabaseMigrationChainTest {

    private val sourceRoot = File("src/main/java/com/swan1127/repland")
    private val databaseSource = sourceRoot.resolve("data/room/ReplandDatabase.kt")
    private val applicationSource = sourceRoot.resolve("ReplandApplication.kt")

    /** name -> (startVersion, endVersion), in source order. */
    private val declared: List<Triple<String, Int, Int>> =
        MIGRATION_DECLARATION.findAll(databaseSource.readText())
            .map { Triple(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
            .toList()

    private val databaseVersion: Int =
        DATABASE_VERSION.find(databaseSource.readText())!!.groupValues[1].toInt()

    private val registered: List<String> =
        REGISTERED_MIGRATION.findAll(applicationSource.readText()).map { it.groupValues[1] }.toList()

    // ------------------------------------------------------------ the declarations themselves

    @Test
    fun `the source files this test reads are where it expects them`() {
        // If the layout moves, fail loudly here rather than silently asserting nothing.
        assertTrue("missing ${databaseSource.absolutePath}", databaseSource.isFile)
        assertTrue("missing ${applicationSource.absolutePath}", applicationSource.isFile)
        assertTrue("no migrations parsed", declared.isNotEmpty())
        assertTrue("no registrations parsed", registered.isNotEmpty())
    }

    @Test
    fun `each migration name agrees with its own version pair`() {
        // Catches a copy-paste slip such as `val MIGRATION_7_8 = object : Migration(7, 9)`,
        // which would otherwise leave a hole in the chain while looking perfectly fine.
        declared.forEach { (name, start, end) ->
            assertEquals("declaration $name has a mismatched version pair", "MIGRATION_${start}_$end", name)
        }
    }

    @Test
    fun `every migration advances by exactly one version`() {
        declared.forEach { (name, start, end) ->
            assertEquals("$name does not advance by one", start + 1, end)
        }
    }

    @Test
    fun `the declared migrations form one contiguous chain from one`() {
        val starts = declared.map { it.second }.sorted()
        assertEquals("a start version is missing or duplicated", (1..starts.size).toList(), starts)
    }

    @Test
    fun `no start version is declared twice`() {
        val starts = declared.map { it.second }
        assertEquals("duplicate start version", starts.distinct().size, starts.size)
    }

    @Test
    fun `the declared database version is the end of the chain`() {
        // A version bump with no migration, or a migration with no version bump, both land here.
        assertEquals(
            "the @Database version must equal the last migration's end version",
            declared.maxOf { it.third },
            databaseVersion,
        )
    }

    @Test
    fun `there is one migration per version step`() {
        assertEquals(
            "the chain should have one migration for each step from 1 to $databaseVersion",
            databaseVersion - 1,
            declared.size,
        )
    }

    // ------------------------------------------------------------ the registration list

    @Test
    fun `every declared migration is registered in the application`() {
        // This is the defect the whole file exists for: a migration that exists but is never
        // handed to Room is invisible until a user on the previous version upgrades.
        val missing = declared.map { it.first }.toSet() - registered.toSet()
        assertEquals("declared but never registered in addMigrations(...)", emptySet<String>(), missing)
    }

    @Test
    fun `nothing is registered that is not declared`() {
        val extra = registered.toSet() - declared.map { it.first }.toSet()
        assertEquals("registered but not declared", emptySet<String>(), extra)
    }

    @Test
    fun `no migration is registered twice`() {
        assertEquals("duplicate registration", registered.distinct().size, registered.size)
    }

    @Test
    fun `the registration list is in ascending order`() {
        // Not a correctness requirement for Room, but the list is hand-maintained and ordered;
        // an out-of-order entry is the visible symptom of a careless paste.
        //
        // Compared numerically on purpose: the names are strings, so lexicographic order puts
        // MIGRATION_10_11 before MIGRATION_1_2 and would report a correct list as unsorted.
        val starts = registered.map { it.removePrefix("MIGRATION_").substringBefore('_').toInt() }
        assertEquals(starts.sorted(), starts)
    }

    // ------------------------------------------------------------ destructive fallback

    @Test
    fun `the database is not built with a destructive migration fallback`() {
        // `fallbackToDestructiveMigration()` would delete every table when no migration is
        // found, turning a crash into silent total data loss. Pinned so it is never added
        // casually as a way to make an upgrade error go away.
        val applicationText = applicationSource.readText()
        assertFalse(
            "a destructive fallback would silently wipe user data on an unhandled upgrade",
            applicationText.contains("fallbackToDestructiveMigration"),
        )
    }

    private companion object {
        val MIGRATION_DECLARATION =
            Regex("""val\s+(MIGRATION_\d+_\d+)\s*=\s*object\s*:\s*Migration\((\d+)\s*,\s*(\d+)\)""")
        val DATABASE_VERSION = Regex("""version\s*=\s*(\d+)""")
        val REGISTERED_MIGRATION = Regex("""ReplandDatabase\.(MIGRATION_\d+_\d+)""")
    }
}
