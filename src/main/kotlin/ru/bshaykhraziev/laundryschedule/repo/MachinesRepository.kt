package ru.bshaykhraziev.laundryschedule.repo

import ru.bshaykhraziev.laundryschedule.db.Database
import ru.bshaykhraziev.laundryschedule.model.Machine

class MachinesRepository(private val db: Database) {
    fun insert(name: String, openHour: Int, closeHour: Int, active: Boolean = true): Machine = db.getConnection().use { conn ->
        conn.prepareStatement(
            "INSERT INTO machines(name, open_hour, close_hour, active) VALUES (?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setString(1, name)
            ps.setInt(2, openHour)
            ps.setInt(3, closeHour)
            ps.setInt(4, if (active) 1 else 0)
            ps.executeUpdate()
            ps.generatedKeys.use { rs ->
                rs.next()
                Machine(
                    id = rs.getLong(1),
                    name = name,
                    openHour = openHour,
                    closeHour = closeHour,
                    active = active
                )
            }
        }
    }

    fun listActive(): List<Machine> = db.getConnection().use { conn ->
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, name, open_hour, close_hour, active FROM machines WHERE active = 1 ORDER BY id").use { rs ->
                val list = mutableListOf<Machine>()
                while (rs.next()) {
                    list.add(
                        Machine(
                            id = rs.getLong("id"),
                            name = rs.getString("name"),
                            openHour = rs.getInt("open_hour"),
                            closeHour = rs.getInt("close_hour"),
                            active = rs.getInt("active") == 1
                        )
                    )
                }
                list
            }
        }
    }

    fun getById(id: Long): Machine? = db.getConnection().use { conn ->
        conn.prepareStatement("SELECT id, name, open_hour, close_hour, active FROM machines WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) Machine(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    openHour = rs.getInt("open_hour"),
                    closeHour = rs.getInt("close_hour"),
                    active = rs.getInt("active") == 1
                ) else null
            }
        }
    }
}

