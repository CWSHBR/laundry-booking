package ru.bshaykhraziev.laundryschedule.service

import ru.bshaykhraziev.laundryschedule.db.Database
import ru.bshaykhraziev.laundryschedule.model.Machine
import ru.bshaykhraziev.laundryschedule.model.User
import ru.bshaykhraziev.laundryschedule.repo.*
import java.time.LocalDate

class Services(
    val db: Database,
    val users: UsersRepository,
    val admins: AdminsRepository,
    val machines: MachinesRepository,
    val bookings: BookingsRepository,
) {
    companion object {
        fun bootstrap(db: Database, primaryAdminTgId: Long): Services {
            val services = Services(
                db = db,
                users = UsersRepository(db),
                admins = AdminsRepository(db),
                machines = MachinesRepository(db),
                bookings = BookingsRepository(db)
            )
            services.admins.addAdmin(primaryAdminTgId)
            return services
        }
    }

    // Users
    fun ensureRegistered(tgId: Long): Boolean = users.findByTelegramId(tgId) != null

    fun registerUser(tgId: Long, surname: String, room: String, now: Long): User =
        users.insert(tgId, surname.trim(), room.trim(), now)

    fun getUserByTelegramId(tgId: Long): User? = users.findByTelegramId(tgId)

    // Admins
    fun isAdmin(tgId: Long): Boolean = admins.isAdmin(tgId)

    fun addAdmin(tgId: Long) = admins.addAdmin(tgId)

    // Machines
    fun addMachine(name: String, openHour: Int, closeHour: Int, active: Boolean = true): Machine =
        machines.insert(name.trim(), openHour, closeHour, active)

    fun listActiveMachines(): List<Machine> = machines.listActive()

    fun getMachine(id: Long): Machine? = machines.getById(id)

    // Bookings
    fun listBookedHours(machineId: Long, date: LocalDate): Set<Int> = bookings.listBookedHours(machineId, date)

    fun listBookingsForMachineAndDate(machineId: Long, date: LocalDate) = bookings.listBookingsForMachineAndDate(machineId, date)

    fun canUserBookForMachineOnDate(userId: Long, machineId: Long, date: LocalDate): Boolean =
        !bookings.userHasBookingForMachineOnDate(userId, machineId, date)

    fun createBooking(userId: Long, machineId: Long, date: LocalDate, hour: Int, now: Long) =
        bookings.insert(userId, machineId, date, hour, now)

    fun deleteBooking(machineId: Long, date: LocalDate, hour: Int): Int = bookings.delete(machineId, date, hour)

    fun listUserBookingsFromDate(userId: Long, fromDateInclusive: LocalDate) =
        bookings.listUserBookingsFromDate(userId, fromDateInclusive)
}
