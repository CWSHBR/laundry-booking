package ru.bshaykhraziev.laundryschedule

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import ru.bshaykhraziev.laundryschedule.bot.LaundryBot
import ru.bshaykhraziev.laundryschedule.db.Database
import ru.bshaykhraziev.laundryschedule.service.Services

fun main() {
    val log = LoggerFactory.getLogger("Main")
    val token = System.getenv("BOT_TOKEN") ?: System.getenv("TELEGRAM_BOT_TOKEN")
    val primaryAdminIdStr = System.getenv("PRIMARY_ADMIN_ID") ?: System.getenv("TELEGRAM_PRIMARY_ADMIN_ID")

    require(!token.isNullOrBlank()) { "Env BOT_TOKEN (или TELEGRAM_BOT_TOKEN) не задан" }
    require(!primaryAdminIdStr.isNullOrBlank()) { "Env PRIMARY_ADMIN_ID (или TELEGRAM_PRIMARY_ADMIN_ID) не задан" }

    val primaryAdminId = primaryAdminIdStr.toLongOrNull()
        ?: error("PRIMARY_ADMIN_ID должен быть числом (telegram user id)")

    // Init DB and services
    val db = Database.open("jdbc:sqlite:laundry.db")
    db.migrate()

    val services = Services.bootstrap(db, primaryAdminId)

    val bot: TelegramLongPollingBot = LaundryBot(token = token!!, services = services)

    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    botsApi.registerBot(bot)
    log.info("LaundrySchedule bot запущен")
}
