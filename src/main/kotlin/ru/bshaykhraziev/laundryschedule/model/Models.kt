package ru.bshaykhraziev.laundryschedule.model

import java.time.LocalDate

data class User(
    val id: Long,
    val telegramId: Long,
    val surname: String,
    val room: String,
    val registeredAt: Long
)

data class Machine(
    val id: Long,
    val name: String,
    val openHour: Int,
    val closeHour: Int,
    val active: Boolean
)

data class Booking(
    val id: Long,
    val userId: Long,
    val machineId: Long,
    val date: LocalDate,
    val hour: Int,
    val createdAt: Long
)

