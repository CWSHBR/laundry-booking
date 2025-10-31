# syntax=docker/dockerfile:1.5

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Копируем Gradle wrapper и конфиги
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
# Копируем исходники
COPY src/ src/

# Собираем дистрибутив приложения
RUN ./gradlew --no-daemon clean installDist

# --- Runtime stage ---
FROM eclipse-temurin:21-jre

# Переменные окружения для бота
ENV BOT_TOKEN="" \
    PRIMARY_ADMIN_ID="" \
    TELEGRAM_BOT_TOKEN="" \
    TELEGRAM_PRIMARY_ADMIN_ID=""

# Каталоги приложения и данных
RUN mkdir -p /opt/app && mkdir -p /data

# Копируем установочный дистрибутив Gradle
COPY --from=build /src/build/install/laundry-schedule/ /opt/app/

# Объявляем volume для данных: здесь будет храниться SQLite-файл
VOLUME ["/data"]

# Рабочая директория — /data. Приложение открывает `jdbc:sqlite:laundry.db`,
# поэтому файл окажется в каталоге маунта хоста.
WORKDIR /data

# Запуск бота
ENTRYPOINT ["/opt/app/bin/laundry-schedule"]
