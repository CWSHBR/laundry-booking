package ru.bshaykhraziev.laundryschedule.repo

import ru.bshaykhraziev.laundryschedule.db.Database
import ru.bshaykhraziev.laundryschedule.model.Booking
import java.time.LocalDate

class BookingsRepository(private val db: Database) {
    fun getBooking(machineId: Long, date: LocalDate, hour: Int): Booking? = db.getConnection().use { conn ->
        conn.prepareStatement(
            "SELECT id, user_id, machine_id, date, hour, created_at FROM bookings WHERE machine_id = ? AND date = ? AND hour = ?"
        ).use { ps ->
            ps.setLong(1, machineId)
            ps.setString(2, date.toString())
            ps.setInt(3, hour)
            ps.executeQuery().use { rs ->
                if (rs.next()) Booking(
                    id = rs.getLong("id"),
                    userId = rs.getLong("user_id"),
                    machineId = rs.getLong("machine_id"),
                    date = LocalDate.parse(rs.getString("date")),
                    hour = rs.getInt("hour"),
                    createdAt = rs.getLong("created_at")
                ) else null
            }
        }
    }

    fun userHasBookingForMachineOnDate(userId: Long, machineId: Long, date: LocalDate): Boolean = db.getConnection().use { conn ->
        conn.prepareStatement(
            "SELECT 1 FROM bookings WHERE user_id = ? AND machine_id = ? AND date = ?"
        ).use { ps ->
            ps.setLong(1, userId)
            ps.setLong(2, machineId)
            ps.setString(3, date.toString())
            ps.executeQuery().use { rs -> rs.next() }
        }
    }

    fun listBookedHours(machineId: Long, date: LocalDate): Set<Int> = db.getConnection().use { conn ->
        conn.prepareStatement(
            "SELECT hour FROM bookings WHERE machine_id = ? AND date = ?"
        ).use { ps ->
            ps.setLong(1, machineId)
            ps.setString(2, date.toString())
            ps.executeQuery().use { rs ->
                val set = mutableSetOf<Int>()
                while (rs.next()) set.add(rs.getInt(1))
                set
            }
        }
    }

    fun listBookingsForMachineAndDate(machineId: Long, date: LocalDate): List<Booking> = db.getConnection().use { conn ->
        conn.prepareStatement(
            "SELECT id, user_id, machine_id, date, hour, created_at FROM bookings WHERE machine_id = ? AND date = ?"
        ).use { ps ->
            ps.setLong(1, machineId)
            ps.setString(2, date.toString())
            ps.executeQuery().use { rs ->
                val list = mutableListOf<Booking>()
                while (rs.next()) {
                    list.add(
                        Booking(
                            id = rs.getLong("id"),
                            userId = rs.getLong("user_id"),
                            machineId = rs.getLong("machine_id"),
                            date = LocalDate.parse(rs.getString("date")),
                            hour = rs.getInt("hour"),
                            createdAt = rs.getLong("created_at")
                        )
                    )
                }
                list
            }
        }
    }

    fun insert(userId: Long, machineId: Long, date: LocalDate, hour: Int, createdAt: Long): Booking = db.getConnection().use { conn ->
        conn.prepareStatement(
            "INSERT INTO bookings(user_id, machine_id, date, hour, created_at) VALUES (?, ?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setLong(1, userId)
            ps.setLong(2, machineId)
            ps.setString(3, date.toString())
            ps.setInt(4, hour)
            ps.setLong(5, createdAt)
            ps.executeUpdate()
            ps.generatedKeys.use { rs ->
                rs.next()
                Booking(
                    id = rs.getLong(1),
                    userId = userId,
                    machineId = machineId,
                    date = date,
                    hour = hour,
                    createdAt = createdAt
                )
            }
        }
    }

    fun delete(machineId: Long, date: LocalDate, hour: Int): Int = db.getConnection().use { conn ->
        conn.prepareStatement("DELETE FROM bookings WHERE machine_id = ? AND date = ? AND hour = ?").use { ps ->
            ps.setLong(1, machineId)
            ps.setString(2, date.toString())
            ps.setInt(3, hour)
            ps.executeUpdate()
        }
    }
}
