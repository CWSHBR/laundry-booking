package ru.bshaykhraziev.laundryschedule.repo

import ru.bshaykhraziev.laundryschedule.db.Database

class AdminsRepository(private val db: Database) {
    fun isAdmin(telegramId: Long): Boolean = db.getConnection().use { conn ->
        conn.prepareStatement("SELECT 1 FROM admins WHERE telegram_id = ?").use { ps ->
            ps.setLong(1, telegramId)
            ps.executeQuery().use { rs -> rs.next() }
        }
    }

    fun addAdmin(telegramId: Long) = db.getConnection().use { conn ->
        conn.prepareStatement("INSERT OR IGNORE INTO admins(telegram_id) VALUES (?)").use { ps ->
            ps.setLong(1, telegramId)
            ps.executeUpdate()
        }
    }

    fun listAdmins(): List<Long> = db.getConnection().use { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("SELECT telegram_id FROM admins ORDER BY telegram_id").use { rs ->
                val list = mutableListOf<Long>()
                while (rs.next()) list.add(rs.getLong(1))
                list
            }
        }
    }
}

