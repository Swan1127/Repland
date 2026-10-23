package com.swan1127.repland.data.room

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays the hand-written migration chain and checks the schema it produces against the entity
 * declarations.
 *
 * **Why this is a source-level test.** The `@Database` annotation is declared with
 * `exportSchema = false`, so Room generates no schema JSON and there is nothing to compare a
 * migration against at build time. Room *does* validate the schema after running migrations, but
 * that check lives in `RoomOpenHelper` and only fires on a real device, against a real database,
 * for a user who is actually upgrading — it turns a developer's mistake into a crash on someone
 * else's phone. This test performs the same comparison statically, in CI.
 *
 * **The defect class.** `tasks`, `plan_segments`, `weekly_time_blocks` and friends each have a
 * column set that is produced by two independent hand-maintained lists:
 *
 *   1. the entity's constructor in `*Entity*.kt` / `*Entities.kt`;
 *   2. the chain of `CREATE TABLE` and `ALTER TABLE ... ADD COLUMN` statements in
 *      `ReplandDatabase.kt`.
 *
 * Room's annotation processor validates (1) against the queries, and never looks at (2). So
 * renaming a field in (1) — or adding one and forgetting (2) — compiles, passes every other test
 * in this suite, and then fails for upgrading users with
 * `Migration didn't properly handle: <table> (expected: ... found: ...)`. A **new** entity with no
 * migration at all is worse: the database version does not change, so Room sees 21 == 21, decides
 * no migration is needed, and the table simply does not exist.
 *
 * **What this proves:** for every table the chain creates, the replayed column set and the
 * declared primary key match the entity exactly; every column the chain adds to a pre-existing
 * table exists on its entity; every entity's table is accounted for somewhere in the chain; no
 * column is added twice; and no `NOT NULL` column is added without a `DEFAULT`.
 *
 * **What this does NOT prove:** column types, nullability and defaults are compared by name only,
 * because Room's exact SQL type mapping is an implementation detail this test would be pinning by
 * accident. The version-1 base schema of `tasks` is not in the chain at all, so only the columns
 * the chain adds to it can be checked. And, as with `DatabaseMigrationChainTest`, none of this
 * proves the SQL actually *runs* — that still needs a real database.
 */
class RoomSchemaConsistencyTest {

    private val sourceRoot = File("src/main/java/com/swan1127/repland")
    private val roomDir = sourceRoot.resolve("data/room")
    private val databaseSource = roomDir.resolve("ReplandDatabase.kt")

    // ------------------------------------------------------------ entities, parsed from source

    /** tableName -> declared column names, in declaration order. */
    private val entityColumns: Map<String, List<String>> = linkedMapOf<String, List<String>>().apply {
        entityFiles().forEach { file ->
            ENTITY_DECLARATION.findAll(file.readText()).forEach { match ->
                val annotation = match.groupValues[1]
                val body = match.groupValues[2]
                val table = requireNotNull(ENTITY_TABLE_NAME.find(annotation)?.groupValues?.get(1)) {
                    "an @Entity in ${file.name} has no explicit tableName"
                }
                val columns = COLUMN_DECLARATION.findAll(body).map { it.groupValues[1] }.toList()
                assertTrue("no columns parsed for $table in ${file.name}", columns.isNotEmpty())
                assertTrue("two entities claim the table $table", put(table, columns) == null)
            }
        }
    }

    /** tableName -> the entity's declared primary key columns. */
    private val entityPrimaryKeys: Map<String, List<String>> = linkedMapOf<String, List<String>>().apply {
        entityFiles().forEach { file ->
            ENTITY_DECLARATION.findAll(file.readText()).forEach { match ->
                val annotation = match.groupValues[1]
                val body = match.groupValues[2]
                val table = ENTITY_TABLE_NAME.find(annotation)!!.groupValues[1]
                val composite = COMPOSITE_PRIMARY_KEYS.find(annotation)
                    ?.groupValues?.get(1)
                    ?.let { keys -> QUOTED_STRING.findAll(keys).map { it.groupValues[1] }.toList() }
                val single = PRIMARY_KEY_FIELD.find(body)?.groupValues?.get(1)
                put(table, composite ?: listOfNotNull(single))
            }
        }
    }

    // ------------------------------------------------------------ the chain, replayed

    private val statements: List<Statement> = parseStatements()

    private val createdTables: Map<String, CreateTable> = linkedMapOf<String, CreateTable>().apply {
        statements.filterIsInstance<CreateTable>().forEach { statement ->
            assertTrue(
                "the chain creates ${statement.table} twice",
                put(statement.table, statement) == null,
            )
        }
    }

    /** table -> every column the chain adds, in chain order. */
    private val addedColumns: Map<String, List<AddColumn>> =
        statements.filterIsInstance<AddColumn>().groupBy(AddColumn::table)

    private val indexedTables: List<CreateIndex> = statements.filterIsInstance<CreateIndex>()

    // ------------------------------------------------------------ the parse itself

    @Test
    fun `the source files this test reads are where it expects them`() {
        // If the layout moves, fail loudly here rather than silently asserting nothing.
        assertTrue("missing ${databaseSource.absolutePath}", databaseSource.isFile)
        assertTrue("missing ${roomDir.absolutePath}", roomDir.isDirectory)
        assertTrue("no migrations parsed", statements.isNotEmpty())
        assertTrue("no entities parsed", entityColumns.isNotEmpty())
    }

    @Test
    fun `every entity and every migration is picked up`() {
        // Guards the parser, not the schema. A regex that silently stops matching would turn every
        // assertion below into a vacuous pass.
        //
        // The count is a literal on purpose, so adding a migration fails here until it is
        // acknowledged: it should always be one less than `ReplandDatabase`'s `version`.
        assertEquals("entities parsed", 14, entityColumns.size)
        assertEquals("migration count", 20, MIGRATION_HEADER.findAll(databaseSource.readText()).count())
        assertEquals(
            "table-creating statements",
            listOf(
                "weekly_time_blocks", "date_overrides", "plans", "plan_segments", "semester_settings",
                "execution_logs", "plan_task_order", "category_preferences", "reminder_settings",
                "profile_evidence", "ai_settings", "engagement_settings", "usage_events",
            ),
            createdTables.keys.toList(),
        )
    }

    @Test
    fun `every primary key the entities declare is parsed`() {
        assertTrue("no primary keys parsed", entityPrimaryKeys.values.all { it.isNotEmpty() })
        assertEquals(
            "the composite key must come from primaryKeys, not from a missing @PrimaryKey",
            listOf("planId", "taskId"),
            entityPrimaryKeys.getValue("plan_task_order"),
        )
        assertEquals(listOf("id"), entityPrimaryKeys.getValue("tasks"))
    }

    // ------------------------------------------------------------ the schema contract

    @Test
    fun `every table the chain touches is a declared entity`() {
        // An orphan table means either a typo in a statement or an entity that was renamed and
        // left a table behind. Either way the chain and the entities have drifted apart.
        val touched = createdTables.keys + addedColumns.keys
        assertEquals(emptySet<String>(), touched - entityColumns.keys)
    }

    @Test
    fun `every entity's table is accounted for by the migration chain`() {
        // THE check that catches a new entity added without a migration. Such a table exists for a
        // fresh install (Room creates it from the entity) but never appears for an upgrading user,
        // because the database version is unchanged and Room therefore runs no migration at all.
        val touched = createdTables.keys + addedColumns.keys
        assertEquals(emptySet<String>(), entityColumns.keys - touched)
    }

    @Test
    fun `every created table's columns match its entity exactly`() {
        val mismatches = createdTables.mapNotNull { (table, create) ->
            val declared = entityColumns.getValue(table).toSet()
            val replayed = (create.columns + addedColumns[table].orEmpty().map(AddColumn::column)).toSet()
            if (declared == replayed) null else "$table: entity=$declared chain=$replayed"
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `no created table lists a column twice`() {
        createdTables.forEach { (table, create) ->
            assertEquals(
                "$table lists a column twice in CREATE TABLE",
                create.columns.size,
                create.columns.toSet().size,
            )
        }
    }

    @Test
    fun `every created table declares a primary key`() {
        createdTables.forEach { (table, create) ->
            assertTrue("$table has no PRIMARY KEY clause", create.hasPrimaryKey)
        }
    }

    @Test
    fun `every created table's primary key matches the entity's`() {
        createdTables.forEach { (table, create) ->
            assertEquals(
                "$table primary key",
                entityPrimaryKeys.getValue(table).toSet(),
                create.primaryKeyColumns.toSet(),
            )
        }
    }

    @Test
    fun `a column the chain adds to a pre-existing table exists on that entity`() {
        // `tasks` is the version-1 table, so its base schema is not in the chain and only the
        // columns added later can be checked.
        val mismatches = addedColumns.flatMap { (table, columns) ->
            val declared = entityColumns.getValue(table)
            columns.map(AddColumn::column).filterNot(declared::contains).map { "$table.$it" }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `no column is added to the same table twice`() {
        val duplicates = addedColumns.flatMap { (table, columns) ->
            val names = columns.map(AddColumn::column)
            names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { "$table.$it" }
        }
        assertEquals(emptyList<String>(), duplicates)
    }

    @Test
    fun `every NOT NULL column added to an existing table carries a default`() {
        // SQLite refuses `ALTER TABLE ... ADD COLUMN x TEXT NOT NULL` on a table that already has
        // rows: there is no value to put in the new column. The migration would throw for exactly
        // the users it exists to serve, and pass for everyone testing on a fresh install.
        val offenders = addedColumns.values.flatten()
            .filter { it.clause.uppercase().contains("NOT NULL") }
            .filterNot { it.clause.uppercase().contains("DEFAULT") }
            .map { "${it.table}.${it.column}" }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `every index the chain creates names a table the chain has created`() {
        val offenders = indexedTables.map(CreateIndex::table).filterNot(createdTables::containsKey)
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `every entity index is created under the exact name and table Room expects`() {
        // Room derives its expected index list from `@Entity(indices = ...)` and builds each name
        // as `index_<table>_<columns joined by _>`. The post-migration schema comparison is by
        // name, so an index that exists under a hand-invented name is still an upgrade failure —
        // which makes the name part of the contract, not a cosmetic detail.
        //
        // The table is compared too: the name already encodes it, so a name that is right while
        // the `ON <table>` clause points somewhere else is exactly the kind of half-edit this
        // check exists to catch, and comparing names alone would let it through.
        val expected = entityIndices()
        val actual = indexedTables.map { it.name to it.table }.toSet()
        assertFalse("no indices parsed", expected.isEmpty())
        assertEquals(emptySet<Pair<String, String>>(), expected - actual)
        assertEquals(emptySet<Pair<String, String>>(), actual - expected)
    }

    /** Every (index name, table) Room expects, derived from the entities' `indices = [...]`. */
    private fun entityIndices(): Set<Pair<String, String>> = entityFiles().flatMap { file ->
        ENTITY_DECLARATION.findAll(file.readText()).flatMap { match ->
            val annotation = match.groupValues[1]
            if (!annotation.contains("indices")) return@flatMap emptySequence()
            val table = ENTITY_TABLE_NAME.find(annotation)!!.groupValues[1]
            INDEX_DECLARATION.findAll(annotation).map { index ->
                val columns = QUOTED_STRING.findAll(index.groupValues[1]).map { it.groupValues[1] }.toList()
                "index_${table}_${columns.joinToString("_")}" to table
            }
        }.toList()
    }.toSet()

    // ------------------------------------------------------------ parsing

    private fun entityFiles(): List<File> =
        roomDir.listFiles { file -> file.isFile && file.name.endsWith(".kt") }
            ?.sortedBy(File::getName)
            .orEmpty()

    private fun parseStatements(): List<Statement> {
        val text = databaseSource.readText()
        val headers = MIGRATION_HEADER.findAll(text).toList()
        val result = mutableListOf<Statement>()
        headers.forEachIndexed { index, header ->
            val end = headers.getOrNull(index + 1)?.range?.first ?: text.length
            val block = text.substring(header.range.first, end)
            EXEC_SQL.findAll(block).forEach { match ->
                val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
                classify(normalize(raw))?.let(result::add)
            }
        }
        return result
    }

    private fun normalize(raw: String): String =
        raw.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ")

    private fun classify(sql: String): Statement? {
        CREATE_TABLE.find(sql)?.let { match ->
            val body = match.groupValues[2]
            return CreateTable(
                table = match.groupValues[1],
                columns = columnNames(body),
                hasPrimaryKey = body.uppercase().contains("PRIMARY KEY"),
                primaryKeyColumns = SQL_PRIMARY_KEY.find(body)
                    ?.groupValues?.get(1)
                    ?.split(',')
                    ?.map(String::trim)
                    .orEmpty(),
            )
        }
        ADD_COLUMN.find(sql)?.let { match ->
            return AddColumn(
                table = match.groupValues[1],
                column = match.groupValues[2],
                clause = match.groupValues[3],
            )
        }
        CREATE_INDEX.find(sql)?.let { match ->
            return CreateIndex(name = match.groupValues[1], table = match.groupValues[2])
        }
        return null
    }

    /** The column names of a `CREATE TABLE` body, skipping the trailing table-level constraints. */
    private fun columnNames(body: String): List<String> =
        splitOnTopLevelCommas(body)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { part -> TABLE_CONSTRAINTS.any { keyword -> part.uppercase().startsWith(keyword) } }
            .map { part -> part.substringBefore(' ').substringBefore('(').trim() }

    /**
     * Splits on commas that are not inside parentheses.
     *
     * A plain `split(',')` breaks `PRIMARY KEY(planId, taskId)` in half and turns the tail into a
     * column named `taskId)`. The symptom is loud enough to spot — a column name ending in a
     * bracket — but it is worth splitting properly rather than filtering the wreckage out.
     */
    private fun splitOnTopLevelCommas(body: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        body.forEach { character ->
            when (character) {
                '(' -> { depth += 1; current.append(character) }
                ')' -> { depth -= 1; current.append(character) }
                ',' -> if (depth == 0) {
                    parts += current.toString()
                    current.clear()
                } else {
                    current.append(character)
                }
                else -> current.append(character)
            }
        }
        if (current.isNotBlank()) parts += current.toString()
        return parts
    }

    private sealed interface Statement {
        val table: String
    }

    private data class CreateTable(
        override val table: String,
        val columns: List<String>,
        val hasPrimaryKey: Boolean,
        val primaryKeyColumns: List<String>,
    ) : Statement

    private data class AddColumn(
        override val table: String,
        val column: String,
        val clause: String,
    ) : Statement

    private data class CreateIndex(val name: String, override val table: String) : Statement

    private companion object {
        val MIGRATION_HEADER =
            Regex("""val\s+(MIGRATION_\d+_\d+)\s*=\s*object\s*:\s*Migration\((\d+)\s*,\s*(\d+)\)""")

        /** Group 1 is the raw-string argument, group 2 the single-quoted one. */
        val EXEC_SQL = Regex("execSQL\\(\\s*(?:\"\"\"([\\s\\S]*?)\"\"\"|\"([^\"]*)\")")

        val ENTITY_DECLARATION =
            Regex("""@Entity\(([\s\S]*?)\)\s*data\s+class\s+\w+\s*\(([\s\S]*?)\n\)""")

        val ENTITY_TABLE_NAME = Regex("""tableName\s*=\s*"([^"]+)"""")
        val COMPOSITE_PRIMARY_KEYS = Regex("""primaryKeys\s*=\s*\[([^\]]*)\]""")
        val QUOTED_STRING = Regex(""""([^"]+)"""")
        val PRIMARY_KEY_FIELD = Regex("""@PrimaryKey(?:\s*\([^)]*\))?\s+val\s+(\w+)""")
        val COLUMN_DECLARATION =
            Regex("""^\s*(?:@\w+(?:\([^)]*\))?\s+)*val\s+(\w+)\s*:""", RegexOption.MULTILINE)
        val INDEX_DECLARATION = Regex("""Index\(([^)]*)\)""")

        val CREATE_TABLE =
            Regex("""CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\((.*)\)""", RegexOption.IGNORE_CASE)
        val ADD_COLUMN =
            Regex("""ALTER\s+TABLE\s+(\w+)\s+ADD\s+COLUMN\s+(\w+)\s*(.*)""", RegexOption.IGNORE_CASE)
        val CREATE_INDEX =
            Regex("""CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s+ON\s+(\w+)\s*\(""", RegexOption.IGNORE_CASE)
        val SQL_PRIMARY_KEY = Regex("""PRIMARY\s+KEY\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)

        val TABLE_CONSTRAINTS = listOf("PRIMARY KEY", "FOREIGN KEY", "UNIQUE", "CHECK", "CONSTRAINT")
    }
}
