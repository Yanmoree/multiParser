package com.parser;

import com.parser.config.Config;
import com.parser.core.ThreadManager;
import com.parser.service.CookieService;
import com.parser.storage.FileStorage;
import com.parser.storage.WhitelistManager;
import com.parser.telegram.TelegramBotService;
import com.parser.telegram.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.util.Date;

/**
 * Главный класс приложения - точка входа
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static ThreadManager threadManager;
    private static TelegramBotService botService;

    public static void main(String[] args) {
        try {
            logger.info("=".repeat(60));
            logger.info("=== Product Parser with Dynamic Cookies ===");
            logger.info("=".repeat(60));
            logger.info("Starting initialization...");

            // Проверка конфигурации
            String botToken = Config.getString("telegram.bot.token", "");
            String botUsername = Config.getString("telegram.bot.username", "");

            if (botToken.isEmpty() || botToken.equals("ВАШ_ТОКЕН_БОТА")) {
                logger.error("Telegram bot token is not configured!");
                logger.error("Please set telegram.bot.token in config.properties");
                logger.error("Current token: {}", botToken);
                System.exit(1);
            }

            if (botUsername.isEmpty()) {
                logger.error("Telegram bot username is not configured!");
                logger.error("Please set telegram.bot.username in config.properties");
                System.exit(1);
            }

            logger.info("✅ Конфигурация проверена:");
            logger.info("   Bot token: {}...", botToken.substring(0, Math.min(10, botToken.length())));
            logger.info("   Bot username: @{}", botUsername);
            logger.info("   Admin ID: {}", Config.getTelegramAdminId());
            logger.info("   Data directory: {}", Config.getString("storage.data.dir", "./data"));

            // Создаем директории для данных
            logger.info("🔄 Создание директорий для данных...");
            try {
                FileStorage.ensureDataDir();
                logger.info("✅ Директории данных созданы");

                // Проверяем доступность whitelist
                checkWhitelistFile();

            } catch (Exception e) {
                logger.error("❌ Не удалось создать директории данных: {}", e.getMessage());
                // Пробуем создать вручную
                createDataDirectoryManually();
            }

            // Инициализация менеджера потоков
            logger.info("🔄 Инициализация ThreadManager...");
            threadManager = new ThreadManager();
            logger.info("✅ ThreadManager инициализирован");

            // Запуск Telegram бота
            logger.info("🔄 Инициализация Telegram бота...");
            initializeTelegramBot(botToken);

            // Установка бота для сервиса уведомлений
            if (botService != null) {
                TelegramNotificationService.setBotInstance(botService);
                logger.info("✅ TelegramNotificationService инициализирован с экземпляром бота");
            } else {
                logger.error("❌ Сервис бота равен null! Функциональность Telegram не будет работать");
                // Продолжаем без бота для отладки
            }

            // Проверка валидности кук при старте
            logger.info("🔄 Проверка cookies...");
            validateCookiesOnStart();

            logger.info("=".repeat(60));
            logger.info("✅ Последовательность запуска приложения завершена!");

            if (botService != null) {
                logger.info("🤖 Telegram бот: @{} - ГОТОВ", botUsername);
            } else {
                logger.info("🤖 Telegram бот: НЕ ИНИЦИАЛИЗИРОВАН");
            }

            logger.info("👑 Admin ID: {}", Config.getTelegramAdminId());
            logger.info("🍪 Динамические cookies: {}", Config.isDynamicCookiesEnabled() ? "ВКЛЮЧЕНЫ" : "ВЫКЛЮЧЕНЫ");
            logger.info("📋 Пользователей в whitelist: {}", WhitelistManager.getUserCount());
            logger.info("=".repeat(60));

            if (botService != null) {
                logger.info("📱 Отправьте /start боту @{} в Telegram", botUsername);
            } else {
                logger.info("⚠️ Telegram бот недоступен. Проверьте логи выше.");
            }

            logger.info("=".repeat(60));
            logger.info("🚀 Приложение успешно запущено!");
            logger.info("⏳ Поддержание работы приложения...");

            // Бесконечный цикл для поддержания работы приложения
            keepApplicationRunning();

        } catch (Exception e) {
            logger.error("❌ Критическая ошибка во время запуска: {}", e.getMessage(), e);
            shutdown();
            System.exit(1);
        }
    }

    /**
     * Проверка файла whitelist
     */
    private static void checkWhitelistFile() {
        try {
            String whitelistPath = FileStorage.getFilePath("whitelist.txt");
            File whitelistFile = new File(whitelistPath);

            if (whitelistFile.exists()) {
                long fileSize = whitelistFile.length();
                logger.info("📋 Файл whitelist найден: {}", whitelistPath);
                logger.info("   Размер файла: {} байт", fileSize);

                // Читаем содержимое
                java.nio.file.Path path = whitelistFile.toPath();
                java.util.List<String> lines = java.nio.file.Files.readAllLines(path);
                logger.info("   Строк в файле: {}", lines.size());

                // Показываем первые несколько строк
                int linesToShow = Math.min(5, lines.size());
                for (int i = 0; i < linesToShow; i++) {
                    logger.info("   [{}]: {}", i + 1, lines.get(i));
                }

                // Перезагружаем whitelist для гарантии
                WhitelistManager.reload();
                logger.info("   Пользователей загружено: {}", WhitelistManager.getUserCount());
            } else {
                logger.info("📋 Файл whitelist не существует, будет создан при первом пользователе");
                logger.info("   Путь: {}", whitelistPath);
            }
        } catch (Exception e) {
            logger.warn("⚠️ Не удалось проверить файл whitelist: {}", e.getMessage());
        }
    }

    /**
     * Ручное создание директории данных
     */
    private static void createDataDirectoryManually() {
        try {
            String dataDir = Config.getString("storage.data.dir", "./data");
            File dir = new File(dataDir);

            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    logger.info("✅ Директория создана вручную: {}", dataDir);

                    // Создаем поддиректории
                    new File(dataDir + "/user_settings").mkdirs();
                    new File(dataDir + "/user_products").mkdirs();
                    new File(dataDir + "/backups").mkdirs();
                    new File(dataDir + "/logs").mkdirs();

                    logger.info("✅ Поддиректории созданы");
                } else {
                    logger.error("❌ Не удалось создать директорию вручную: {}", dataDir);
                    throw new RuntimeException("Failed to create data directory");
                }
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка при ручном создании директорий: {}", e.getMessage());
        }
    }

    /**
     * Инициализация Telegram бота
     */
    private static void initializeTelegramBot(String botToken) {
        try {
            logger.info("🤖 Создание экземпляра TelegramBotService...");

            // Создаем экземпляр бота
            botService = new TelegramBotService(botToken, threadManager);
            logger.info("✅ Экземпляр TelegramBotService создан");

            // Пробуем получить username
            String username = botService.getBotUsername();
            logger.info("✅ Username бота получен: @{}", username);

            logger.info("🤖 Регистрация бота в Telegram API...");

            // Создаем TelegramBotsApi
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            logger.info("✅ TelegramBotsApi создан");

            // Регистрируем бота
            botsApi.registerBot(botService);

            logger.info("🎉 Telegram бот успешно зарегистрирован!");
            logger.info("✅ Бот теперь слушает сообщения...");

        } catch (TelegramApiException e) {
            logger.error("❌ TelegramApiException: {}", e.getMessage());

            if (e.getMessage() != null) {
                if (e.getMessage().contains("409") || e.getMessage().contains("terminated by other getUpdates")) {
                    logger.error("❌ Другой экземпляр бота уже запущен!");
                    logger.error("❌ Остановите предыдущий экземпляр или подождите 1 минуту");
                } else if (e.getMessage().contains("401")) {
                    logger.error("❌ Неверный токен бота!");
                    logger.error("❌ Проверьте токен бота в config.properties");
                } else if (e.getMessage().contains("timed out") || e.getMessage().contains("connect")) {
                    logger.error("❌ Не удается подключиться к Telegram API!");
                    logger.error("❌ Проверьте интернет-соединение или VPN");
                }
            }

            logger.error("❌ Полное исключение:", e);
            botService = null;

        } catch (Exception e) {
            logger.error("❌ Неожиданная ошибка при инициализации Telegram бота: {}", e.getMessage(), e);
            botService = null;
        }
    }

    /**
     * Проверка валидности кук при старте
     */
    private static void validateCookiesOnStart() {
        logger.info("🍪 Проверка cookies при запуске...");

        try {
            // Проверяем настройки
            boolean dynamicCookiesEnabled = Config.isDynamicCookiesEnabled();
            boolean autoUpdateEnabled = Config.getBoolean("cookie.auto.update", true);

            logger.info("   Динамические cookies: {}", dynamicCookiesEnabled ? "ВКЛЮЧЕНЫ" : "ВЫКЛЮЧЕНЫ");
            logger.info("   Автообновление: {}", autoUpdateEnabled ? "ВКЛЮЧЕНО" : "ВЫКЛЮЧЕНО");

            if (dynamicCookiesEnabled) {
                logger.info("🔄 Получение свежих cookies через Selenium...");
                boolean refreshed = CookieService.refreshCookies("h5api.m.goofish.com");
                if (refreshed) {
                    logger.info("✅ Свежие cookies успешно получены");
                } else {
                    logger.warn("⚠️ Не удалось получить свежие cookies, используются статические");
                }
            } else {
                logger.info("ℹ️ Динамические cookies выключены, используются статические cookies");
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при проверке cookies: {}", e.getMessage());
            logger.warn("⚠️ Проверка cookies не удалась, но продолжаем работу...");
        }
    }

    /**
     * Поддержание работы приложения
     */
    private static void keepApplicationRunning() {
        try {
            logger.info("⏳ Вход в основной цикл...");

            // Простой цикл для поддержания работы
            int counter = 0;
            while (true) {
                Thread.sleep(30000); // Спим 30 секунд
                counter++;

                // Логируем каждые 10 итераций (5 минут)
                if (counter % 10 == 0) {
                    logger.info("⏱️ Heartbeat #{} - Приложение работает", counter);

                    // Периодически проверяем состояние
                    logApplicationStatus();
                }

                // Периодическая проверка состояния бота
                if (botService == null && (counter % 4 == 0)) { // Каждые 2 минуты
                    logger.warn("⚠️ Сервис бота равен null, пробуем переинициализировать...");
                    try {
                        String botToken = Config.getString("telegram.bot.token", "");
                        if (!botToken.isEmpty()) {
                            logger.info("🔄 Переинициализация бота...");
                            initializeTelegramBot(botToken);
                            if (botService != null) {
                                TelegramNotificationService.setBotInstance(botService);
                                logger.info("✅ Бот успешно переинициализирован");
                            }
                        }
                    } catch (Exception e) {
                        logger.error("❌ Не удалось переинициализировать бота: {}", e.getMessage());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Основной цикл прерван");
        }
    }

    /**
     * Логирование статуса приложения
     */
    private static void logApplicationStatus() {
        try {
            long whitelistUsers = WhitelistManager.getUserCount();
            int activeSessions = threadManager != null ? threadManager.getActiveUsers().size() : 0;

            logger.info("📊 Статус приложения:");
            logger.info("   Пользователей в whitelist: {}", whitelistUsers);
            logger.info("   Активных сессий: {}", activeSessions);
            logger.info("   Telegram бот: {}", botService != null ? "АКТИВЕН" : "НЕ АКТИВЕН");

            // Проверяем файл whitelist каждые 30 минут
            if (whitelistUsers == 0) {
                logger.warn("   ⚠️ В whitelist нет пользователей!");
                checkWhitelistFile();
            }

        } catch (Exception e) {
            logger.warn("Не удалось получить статус приложения: {}", e.getMessage());
        }
    }

    /**
     * Корректное завершение работы приложения
     */
    private static void shutdown() {
        logger.info("🛑 Начало завершения работы приложения...");

        try {
            if (threadManager != null) {
                threadManager.shutdown();
                logger.info("✅ ThreadManager завершен");
            }

            logger.info("✅ Завершение работы приложения успешно завершено");
        } catch (Exception e) {
            logger.error("❌ Ошибка при завершении работы: {}", e.getMessage(), e);
        }
    }
}