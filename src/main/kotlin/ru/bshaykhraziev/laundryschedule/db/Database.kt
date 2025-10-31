package ru.bshaykhraziev.laundryschedule.db

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

class Database private constructor(private val url: String) {
    private val log = LoggerFactory.getLogger(Database::class.java)

    fun getConnection(): Connection {
        val conn = DriverManager.getConnection(url)
        conn.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        return conn
    }

    fun migrate() {
        getConnection().use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    PRAGMA foreign_keys = ON;
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        telegram_id INTEGER NOT NULL UNIQUE,
                        surname TEXT NOT NULL,
                        room TEXT NOT NULL,
                        registered_at INTEGER NOT NULL
                    );

                    CREATE TABLE IF NOT EXISTS admins (
                        telegram_id INTEGER PRIMARY KEY
                    );

                    CREATE TABLE IF NOT EXISTS machines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        open_hour INTEGER NOT NULL,
                        close_hour INTEGER NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1
                    );

                    CREATE TABLE IF NOT EXISTS bookings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        machine_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        UNIQUE(machine_id, date, hour),
                        UNIQUE(user_id, machine_id, date),
                        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE
                    );

                    CREATE INDEX IF NOT EXISTS idx_bookings_date ON bookings(date);
                    CREATE INDEX IF NOT EXISTS idx_bookings_user ON bookings(user_id);
                    """.trimIndent()
                )
            }
            log.info("Миграции БД выполнены")
        }
    }

    companion object {
        fun open(url: String): Database {
            Class.forName("org.sqlite.JDBC")
            return Database(url)
        }
    }
}
