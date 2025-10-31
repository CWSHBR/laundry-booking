package ru.bshaykhraziev.laundryschedule.repo

import ru.bshaykhraziev.laundryschedule.db.Database
import ru.bshaykhraziev.laundryschedule.model.User

class UsersRepository(private val db: Database) {
    fun findByTelegramId(tgId: Long): User? = db.getConnection().use { conn ->
        conn.prepareStatement("SELECT id, telegram_id, surname, room, registered_at FROM users WHERE telegram_id = ?").use { ps ->
            ps.setLong(1, tgId)
            ps.executeQuery().use { rs ->
                if (rs.next()) User(
                    id = rs.getLong("id"),
                    telegramId = rs.getLong("telegram_id"),
                    surname = rs.getString("surname"),
                    room = rs.getString("room"),
                    registeredAt = rs.getLong("registered_at")
                ) else null
            }
        }
    }

    fun insert(telegramId: Long, surname: String, room: String, registeredAt: Long): User = db.getConnection().use { conn ->
        conn.prepareStatement(
            "INSERT INTO users(telegram_id, surname, room, registered_at) VALUES (?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setLong(1, telegramId)
            ps.setString(2, surname)
            ps.setString(3, room)
            ps.setLong(4, registeredAt)
            ps.executeUpdate()
            ps.generatedKeys.use { rs ->
                rs.next()
                val id = rs.getLong(1)
                User(id, telegramId, surname, room, registeredAt)
            }
        }
    }

    fun listAll(): List<User> = db.getConnection().use { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, telegram_id, surname, room, registered_at FROM users ORDER BY surname, room").use { rs ->
                val list = mutableListOf<User>()
                while (rs.next()) {
                    list.add(
                        User(
                            id = rs.getLong("id"),
                            telegramId = rs.getLong("telegram_id"),
                            surname = rs.getString("surname"),
                            room = rs.getString("room"),
                            registeredAt = rs.getLong("registered_at")
                        )
                    )
                }
                list
            }
        }
    }
}

