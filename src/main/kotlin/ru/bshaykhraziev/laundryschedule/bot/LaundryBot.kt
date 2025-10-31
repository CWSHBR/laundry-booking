package ru.bshaykhraziev.laundryschedule.bot

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.bshaykhraziev.laundryschedule.service.Services
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class LaundryBot(
    private val token: String,
    private val services: Services
) : TelegramLongPollingBot() {

    private val log = LoggerFactory.getLogger(LaundryBot::class.java)

    // Простейшие состояния диалогов (in-memory)
    private val pendingRegistration = ConcurrentHashMap<Long, Boolean>() // tgId -> awaiting surname room
    private val pendingAddAdmin = ConcurrentHashMap<Long, Boolean>() // admin tgId -> awaiting target tg id
    private val pendingAddMachineName = ConcurrentHashMap<Long, Boolean>()
    private val pendingAddMachineOpenHour = ConcurrentHashMap<Long, Pair<String, Int?>>() // name, open?
    private val pendingAdminAssignBooking = ConcurrentHashMap<Long, Triple<Long, LocalDate, Int>>() // machineId, date, hour

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val today: LocalDate get() = LocalDate.now()
    private val maxDate: LocalDate get() = today.plusDays(3)

    init {
        // Команды
        val commands = listOf(
            BotCommand("start", "Старт/главное меню"),
            BotCommand("help", "Справка"),
            BotCommand("menu", "Главное меню"),
            BotCommand("admin", "Админ-меню")
        )
        runCatching { execute(SetMyCommands(commands, null, null)) }
    }

    override fun getBotToken(): String = token

    override fun getBotUsername(): String = "LaundryScheduleBot"

    override fun onUpdateReceived(update: Update) {
        try {
            when {
                update.hasMessage() && update.message.hasText() -> handleMessage(update)
                update.hasCallbackQuery() -> handleCallback(update)
            }
        } catch (t: Throwable) {
            log.error("Ошибка обработки апдейта", t)
        }
    }

    private fun handleMessage(update: Update) {
        val msg = update.message
        val chatId = msg.chatId
        val tgId = msg.from.id
        val text = msg.text.trim()

        // Состояния ввода
        when {
            pendingRegistration.remove(tgId) == true -> {
                // Ожидалось: "Фамилия Комната"
                val parts = text.split(" ", limit = 2)
                if (parts.size < 2) {
                    sendMessage(chatId, "Пожалуйста, укажите: Фамилия и номер комнаты. Пример: Иванов 123")
                    pendingRegistration[tgId] = true
                    return
                }
                val surname = parts[0]
                val room = parts[1]
                services.registerUser(tgId, surname, room, System.currentTimeMillis())
                sendMessage(chatId, "Регистрация завершена: $surname, комната $room")
                showMainMenu(chatId, tgId)
                return
            }
            pendingAddAdmin.remove(tgId) == true -> {
                val id = text.toLongOrNull()
                if (id == null) {
                    sendMessage(chatId, "Введите числовой Telegram ID")
                    pendingAddAdmin[tgId] = true
                    return
                }
                services.addAdmin(id)
                sendMessage(chatId, "Администратор $id добавлен")
                showAdminMenu(chatId, tgId)
                return
            }
            pendingAddMachineName.remove(tgId) == true -> {
                val name = text.take(64)
                pendingAddMachineOpenHour[tgId] = name to null
                sendMessage(chatId, "Укажите час открытия (0-23)")
                return
            }
            pendingAddMachineOpenHour[tgId] != null -> {
                val (name, open) = pendingAddMachineOpenHour[tgId]!!
                val hour = text.toIntOrNull()
                if (open == null) {
                    if (hour == null || hour !in 0..23) {
                        sendMessage(chatId, "Час открытия должен быть числом 0-23")
                        return
                    }
                    pendingAddMachineOpenHour[tgId] = name to hour
                    sendMessage(chatId, "Укажите час закрытия (1-24)")
                    return
                } else {
                    if (hour == null || hour !in 1..24 || hour <= open) {
                        sendMessage(chatId, "Час закрытия должен быть в диапазоне 1-24 и больше часа открытия")
                        return
                    }
                    services.addMachine(name, open, hour)
                    pendingAddMachineOpenHour.remove(tgId)
                    sendMessage(chatId, "Машина '$name' добавлена: $open-$hour")
                    showAdminMenu(chatId, tgId)
                    return
                }
            }
            pendingAdminAssignBooking[tgId] != null -> {
                val (machineId, date, hour) = pendingAdminAssignBooking.remove(tgId)!!
                val targetTgId = text.toLongOrNull()
                if (targetTgId == null) {
                    sendMessage(chatId, "Введите числовой Telegram ID пользователя")
                    pendingAdminAssignBooking[tgId] = Triple(machineId, date, hour)
                    return
                }
                val user = services.getUserByTelegramId(targetTgId)
                if (user == null) {
                    sendMessage(chatId, "Пользователь с таким Telegram ID не найден или не зарегистрирован")
                    pendingAdminAssignBooking[tgId] = Triple(machineId, date, hour)
                    return
                }
                if (date.isBefore(today) || date.isAfter(maxDate)) {
                    sendMessage(chatId, "Запись возможна только с ${today} по ${maxDate}")
                    return
                }
                if (!services.canUserBookForMachineOnDate(user.id, machineId, date)) {
                    sendMessage(chatId, "У пользователя уже есть запись на эту машину в выбранный день")
                    return
                }
                services.createBooking(user.id, machineId, date, hour, System.currentTimeMillis())
                sendMessage(chatId, "Бронь создана для пользователя ${user.surname} (комн. ${user.room}) на $date $hour:00")
                return
            }
        }

        when {
            text.startsWith("/start") || text.startsWith("/menu") -> onStart(chatId, tgId)
            text.startsWith("/help") -> sendMessage(chatId, helpText())
            text.startsWith("/admin") -> {
                if (services.isAdmin(tgId)) showAdminMenu(chatId, tgId) else sendMessage(chatId, "Доступ запрещён")
            }
            else -> {
                // Если не зарегистрирован — попросим
                if (!services.ensureRegistered(tgId)) {
                    sendMessage(chatId, "Для начала работы зарегистрируйтесь. Введите фамилию и номер комнаты (пример: Иванов 123)")
                    pendingRegistration[tgId] = true
                } else {
                    showMainMenu(chatId, tgId)
                }
            }
        }
    }

    private fun onStart(chatId: Long, tgId: Long) {
        if (!services.ensureRegistered(tgId)) {
            sendMessage(chatId, "Добро пожаловать! Для регистрации укажите фамилию и номер комнаты (пример: Иванов 123)")
            pendingRegistration[tgId] = true
        } else {
            showMainMenu(chatId, tgId)
        }
    }

    private fun helpText(): String = """
        Бот для записи в прачечную.
        Пользователь:
        • Выберите машину, затем свободный час на нужный день.
        • Ограничение: 1 машина — 1 час в день.
        Администратор:
        • Управление машинами, расписанием и администраторами через /admin.
    """.trimIndent()

    private fun showMainMenu(chatId: Long, tgId: Long, messageId: Int? = null) {
        val kb = InlineKeyboardMarkup()
        val rows = mutableListOf(listOf(button("Выбрать машину", "U:MACHINES")))
        if (services.isAdmin(tgId)) {
            rows.add(listOf(button("Админ-меню", "A:MENU")))
        }
        kb.keyboard = rows
        editOrSend(chatId, messageId, "Главное меню", kb)
    }

    private fun handleCallback(update: Update) {
        val cq = update.callbackQuery
        val tgId = cq.from.id
        val chatId = cq.message.chatId
        val data = cq.data

        fun ack(text: String? = null, alert: Boolean = false) {
            runCatching { execute(AnswerCallbackQuery(cq.id).apply { this.text = text; showAlert = alert }) }
        }

        when {
            data == "noop" -> {
                ack()
                return
            }
            data == "U:BACK" -> {
                ack()
                showMainMenu(chatId, tgId, cq.message.messageId)
            }
            data == "U:MACHINES" -> {
                ack()
                showMachines(chatId, messageId = cq.message.messageId, forAdmin = false, viewerTgId = tgId)
            }
            data.startsWith("U:M:") -> {
                ack()
                val machineId = data.substringAfter("U:M:").toLong()
                showDay(machineId, today, chatId, cq.message.messageId, forAdmin = false, viewerTgId = tgId)
            }
            data.startsWith("U:D:") -> {
                ack()
                val parts = data.split(":") // U:D:<machineId>:<date>
                val machineId = parts[2].toLong()
                var date = LocalDate.parse(parts[3])
                if (date.isBefore(today) || date.isAfter(maxDate)) {
                    date = date.coerceIn(today, maxDate)
                    ack("Доступны даты с ${today} по ${maxDate}")
                }
                showDay(machineId, date, chatId, cq.message.messageId, forAdmin = false, viewerTgId = tgId)
            }
            data.startsWith("U:B:") -> {
                ack()
                val parts = data.split(":") // U:B:<machineId>:<date>:<hour>
                val machineId = parts[2].toLong()
                val date = LocalDate.parse(parts[3])
                val hour = parts[4].toInt()
                handleUserBooking(tgId, chatId, cq.message.messageId, machineId, date, hour)
            }
            data.startsWith("U:CANCEL:") -> {
                ack()
                val parts = data.split(":") // U:CANCEL:<machineId>:<date>:<hour>
                val machineId = parts[2].toLong()
                val date = LocalDate.parse(parts[3])
                val hour = parts[4].toInt()
                handleUserCancel(tgId, chatId, cq.message.messageId, machineId, date, hour)
            }

            data == "A:MENU" -> {
                ack()
                if (services.isAdmin(tgId)) showAdminMenu(chatId, tgId, cq.message.messageId) else sendMessage(chatId, "Доступ запрещён")
            }
            data == "A:MACHINES" -> {
                ack()
                if (services.isAdmin(tgId)) showMachines(chatId, cq.message.messageId, forAdmin = true, viewerTgId = null) else sendMessage(chatId, "Доступ запрещён")
            }
            data == "A:ADD_MACHINE" -> {
                ack()
                if (!services.isAdmin(tgId)) return
                sendMessage(chatId, "Введите название машины")
                pendingAddMachineName[tgId] = true
            }
            data.startsWith("A:M:") -> {
                ack()
                val machineId = data.substringAfter("A:M:").toLong()
                showDay(machineId, today, chatId, cq.message.messageId, forAdmin = true, viewerTgId = null)
            }
            data.startsWith("A:D:") -> {
                ack()
                val parts = data.split(":") // A:D:<machineId>:<date>
                val machineId = parts[2].toLong()
                var date = LocalDate.parse(parts[3])
                if (date.isBefore(today) || date.isAfter(maxDate)) {
                    date = date.coerceIn(today, maxDate)
                    ack("Доступны даты с ${today} по ${maxDate}")
                }
                showDay(machineId, date, chatId, cq.message.messageId, forAdmin = true, viewerTgId = null)
            }
            data.startsWith("A:DEL:") -> {
                ack()
                val parts = data.split(":") // A:DEL:<machineId>:<date>:<hour>
                val machineId = parts[2].toLong()
                val date = LocalDate.parse(parts[3])
                val hour = parts[4].toInt()
                val deleted = services.deleteBooking(machineId, date, hour)
                val text = if (deleted > 0) "Запись удалена" else "Запись не найдена"
                sendMessage(chatId, text)
                showDay(machineId, date, chatId, cq.message.messageId, forAdmin = true, viewerTgId = null)
            }
            data.startsWith("A:ASSIGN:") -> {
                ack()
                val parts = data.split(":") // A:ASSIGN:<machineId>:<date>:<hour>
                val machineId = parts[2].toLong()
                val date = LocalDate.parse(parts[3])
                val hour = parts[4].toInt()
                if (!services.isAdmin(tgId)) return
                if (date.isBefore(today) || date.isAfter(maxDate)) {
                    sendMessage(chatId, "Запись возможна только с ${today} по ${maxDate}")
                    return
                }
                pendingAdminAssignBooking[tgId] = Triple(machineId, date, hour)
                sendMessage(chatId, "Введите Telegram ID пользователя, для которого создать запись")
            }
            data == "A:ADMINS" -> {
                ack()
                if (!services.isAdmin(tgId)) return
                val admins = services.admins.listAdmins()
                val text = buildString {
                    appendLine("Список администраторов:")
                    if (admins.isEmpty()) appendLine("— пусто —") else admins.forEach { appendLine("• $it") }
                }
                val kb = InlineKeyboardMarkup()
                kb.keyboard = listOf(listOf(button("Добавить админа", "A:ADD_ADMIN")), listOf(button("Назад", "A:MENU")))
                editOrSend(chatId, cq.message.messageId, text.trim(), kb)
            }
            data == "A:ADD_ADMIN" -> {
                ack()
                if (!services.isAdmin(tgId)) return
                sendMessage(chatId, "Укажите Telegram ID нового администратора")
                pendingAddAdmin[tgId] = true
            }
        }
    }

    private fun handleUserBooking(tgId: Long, chatId: Long, messageId: Int, machineId: Long, date: LocalDate, hour: Int) {
        if (!services.ensureRegistered(tgId)) {
            sendMessage(chatId, "Сначала зарегистрируйтесь: укажите фамилию и номер комнаты")
            pendingRegistration[tgId] = true
            return
        }
        if (date.isBefore(today) || date.isAfter(maxDate)) {
            sendMessage(chatId, "Запись возможна только с ${today} по ${maxDate}")
            return
        }
        val user = services.getUserByTelegramId(tgId) ?: return
        val machine = services.getMachine(machineId) ?: return

        // Уже занято?
        val booked = services.listBookedHours(machineId, date)
        if (hour in booked) {
            sendMessage(chatId, "Этот час уже занят")
            showDay(machineId, date, chatId, messageId, forAdmin = false, viewerTgId = tgId)
            return
        }
        // Дневной лимит 1 слот для этой машины
        if (!services.canUserBookForMachineOnDate(user.id, machineId, date)) {
            sendMessage(chatId, "Вы уже бронировали эту машину на этот день")
            return
        }
        services.createBooking(user.id, machineId, date, hour, System.currentTimeMillis())
        sendMessage(chatId, "Бронирование создано: ${machine.name}, $date, ${hour}:00")
        showDay(machineId, date, chatId, messageId, forAdmin = false, viewerTgId = tgId)
    }

    private fun handleUserCancel(tgId: Long, chatId: Long, messageId: Int, machineId: Long, date: LocalDate, hour: Int) {
        val user = services.getUserByTelegramId(tgId) ?: return
        val booking = services.bookings.getBooking(machineId, date, hour)
        if (booking == null) {
            sendMessage(chatId, "Бронь не найдена")
            showDay(machineId, date, chatId, messageId, forAdmin = false, viewerTgId = tgId)
            return
        }
        if (booking.userId != user.id) {
            sendMessage(chatId, "Вы не можете отменить чужую бронь")
            return
        }
        services.deleteBooking(machineId, date, hour)
        sendMessage(chatId, "Ваша бронь на $date в ${hour}:00 отменена")
        showDay(machineId, date, chatId, messageId, forAdmin = false, viewerTgId = tgId)
    }

    private fun showMachines(chatId: Long, messageId: Int? = null, forAdmin: Boolean, viewerTgId: Long?) {
        val machines = services.listActiveMachines()
        val kb = InlineKeyboardMarkup()
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        if (machines.isEmpty()) {
            rows.add(listOf(button("Машин нет", "noop")))
        } else {
            machines.forEach { m ->
                val prefix = if (forAdmin) "A:M:" else "U:M:"
                rows.add(listOf(button("${m.name} (${m.openHour}-${m.closeHour})", prefix + m.id)))
            }
        }
        if (forAdmin) {
            rows.add(listOf(button("➕ Добавить машину", "A:ADD_MACHINE")))
            rows.add(listOf(button("⬅️ Назад", "A:MENU")))
        } else {
            rows.add(listOf(button("⬅️ Назад", "U:BACK")))
        }
        kb.keyboard = rows
        val text = if (forAdmin) "Админ: список машин" else "Выберите машину"
        editOrSend(chatId, messageId, text, kb)
    }

    private fun showDay(machineId: Long, date: LocalDate, chatId: Long, messageId: Int? = null, forAdmin: Boolean, viewerTgId: Long?) {
        val machine = services.getMachine(machineId) ?: run {
            sendMessage(chatId, "Машина не найдена")
            return
        }
        val clampedDate = date.coerceIn(today, maxDate)
        val bookings = services.listBookingsForMachineAndDate(machineId, clampedDate)
        val bookedHours = bookings.map { it.hour }.toSet()
        val byHour = bookings.associateBy { it.hour }
        val kb = InlineKeyboardMarkup()
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        // Навигация по дням с ограничением сегодня..+3
        val prevDate = clampedDate.minusDays(1)
        val nextDate = clampedDate.plusDays(1)
        val prevBtn = if (prevDate.isBefore(today)) button(" ", "noop") else button("◀ $prevDate", (if (forAdmin) "A:D:" else "U:D:") + "$machineId:$prevDate")
        val head = button("${machine.name} | $clampedDate", "noop")
        val nextBtn = if (nextDate.isAfter(maxDate)) button(" ", "noop") else button("$nextDate ▶", (if (forAdmin) "A:D:" else "U:D:") + "$machineId:$nextDate")
        rows.add(listOf(prevBtn, head, nextBtn))

        // Часы: верхняя граница включительно, но не выше 23
        val endHour = min(machine.closeHour, 23)
        val hourButtons = mutableListOf<InlineKeyboardButton>()
        val currentUserId = viewerTgId?.let { services.getUserByTelegramId(it)?.id }
        for (h in machine.openHour..endHour) {
            val isBooked = h in bookedHours
            val callback = when {
                forAdmin && isBooked -> "A:DEL:$machineId:$clampedDate:$h"
                forAdmin && !isBooked -> "A:ASSIGN:$machineId:$clampedDate:$h"
                !forAdmin && isBooked -> {
                    val ownerId = byHour[h]?.userId
                    if (ownerId != null && ownerId == currentUserId) "U:CANCEL:$machineId:$clampedDate:$h" else "noop"
                }
                else -> "U:B:$machineId:$clampedDate:$h"
            }
            val text = when {
                forAdmin && isBooked -> "❌ $h"
                forAdmin && !isBooked -> "✅ $h"
                !forAdmin && isBooked -> {
                    val ownerId = byHour[h]?.userId
                    if (ownerId != null && ownerId == currentUserId) "🗑 $h" else "🔒 $h"
                }
                else -> "✅ $h"
            }
            hourButtons.add(button(text, callback))
            if (hourButtons.size == 4) {
                rows.add(hourButtons.toList())
                hourButtons.clear()
            }
        }
        if (hourButtons.isNotEmpty()) rows.add(hourButtons)

        // Низ
        val back = if (forAdmin) button("⬅️ Назад к списку машин", "A:MACHINES") else button("⬅️ Назад", "U:MACHINES")
        rows.add(listOf(back))

        kb.keyboard = rows
        val info = buildString {
            appendLine("Машина: ${machine.name}")
            appendLine("Дата: $clampedDate")
            if (forAdmin) appendLine("❌ — удалить запись, ✅ — назначить пользователя")
            else appendLine("✅ — свободно, 🔒 — занято, 🗑 — отменить свою бронь")
        }.trim()
        editOrSend(chatId, messageId, info, kb)
    }

    private fun showAdminMenu(chatId: Long, tgId: Long, messageId: Int? = null) {
        val kb = InlineKeyboardMarkup()
        kb.keyboard = listOf(
            listOf(button("Машины", "A:MACHINES")),
            listOf(button("Администраторы", "A:ADMINS")),
            listOf(button("⬅️ В главное меню", "U:BACK"))
        )
        editOrSend(chatId, messageId, "Админ-меню", kb)
    }

    private fun button(text: String, data: String): InlineKeyboardButton = InlineKeyboardButton.builder()
        .text(text)
        .callbackData(data)
        .build()

    private fun sendMessage(chatId: Long, text: String, kb: InlineKeyboardMarkup? = null) {
        val sm = SendMessage(chatId.toString(), text).apply { replyMarkup = kb }
        runCatching { execute(sm) }
    }

    private fun editOrSend(chatId: Long, messageId: Int?, text: String, kb: InlineKeyboardMarkup) {
        if (messageId != null) {
            val edit = EditMessageText().apply {
                this.chatId = chatId.toString()
                this.messageId = messageId
                this.text = text
                this.replyMarkup = kb
            }
            val ok = runCatching { execute(edit) }.isSuccess
            if (!ok) sendMessage(chatId, text, kb)
        } else {
            sendMessage(chatId, text, kb)
        }
    }
}
