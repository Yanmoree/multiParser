package com.parser;

import com.parser.config.Config;
import com.parser.core.ThreadManager;
import com.parser.storage.WhitelistManager;
import com.parser.storage.UserDataManager;
import com.parser.telegram.TelegramBotService;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный класс приложения - точка входа
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static ThreadManager threadManager;
    private static TelegramBotService botService;

    public static void main(String[] args) {
        try {
            logger.info("=== Product Parser with Telegram Bot ===");
            logger.info("Starting initialization...");

            // Проверка конфигурации
            String botToken = Config.getString("telegram.bot.token", "");
            if (botToken.isEmpty() || botToken.equals("ВАШ_ТОКЕН_БОТА")) {
                logger.error("Telegram bot token is not configured!");
                logger.error("Please set telegram.bot.token in config.properties");
                System.exit(1);
            }

            // Инициализация менеджера потоков
            threadManager = new ThreadManager();
            logger.info("ThreadManager initialized with {} max threads",
                    Config.getInt("thread.pool.max.size", 20));

            // Запуск Telegram бота
            initializeTelegramBot(botToken);

            // Автозапуск парсеров для активных пользователей
            autostartUserParsers();

            // Добавление shutdown hook
            addShutdownHook();

            logger.info("Application started successfully!");
            logger.info("Bot username: @{}", Config.getString("telegram.bot.username", ""));

            // Бесконечный цикл для поддержания работы приложения
            runMainLoop();

        } catch (Exception e) {
            logger.error("Fatal error during startup: {}", e.getMessage(), e);
            shutdown();
            System.exit(1);
        }
    }

    /**
     * Инициализация Telegram бота
     */
    private static void initializeTelegramBot(String botToken) {
        try {
            logger.info("Initializing Telegram bot...");
            botService = new TelegramBotService(botToken, threadManager);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(botService);
            logger.info("Telegram bot registered successfully");
        } catch (TelegramApiException e) {
            logger.error("Failed to initialize Telegram bot: {}", e.getMessage(), e);
            throw new RuntimeException("Telegram bot initialization failed", e);
        }
    }

    /**
     * Автозапуск парсеров для пользователей с активными запросами
     */
    private static void autostartUserParsers() {
        logger.info("Checking for users to auto-start...");
        int startedCount = 0;

        for (int userId : WhitelistManager.getAllUsers()) {
            if (!UserDataManager.getUserQueries(userId).isEmpty()) {
                if (threadManager.startUserParser(userId)) {
                    startedCount++;
                    logger.debug("Auto-started parser for user {}", userId);
                }
            }
        }

        logger.info("Auto-started parsers for {} users", startedCount);
    }

    /**
     * Добавление обработчика завершения работы
     */
    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            shutdown();
        }));
    }

    /**
     * Основной цикл приложения
     */
    private static void runMainLoop() {
        try {
            // Бесконечный цикл с периодической проверкой состояния
            while (true) {
                Thread.sleep(60000); // Проверка каждую минуту

                // Логирование статистики
                if (logger.isDebugEnabled()) {
                    var statuses = threadManager.getAllStatuses();
                    logger.debug("Active sessions: {}", statuses.size());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Main loop interrupted");
        }
    }

    /**
     * Корректное завершение работы приложения
     */
    private static void shutdown() {
        logger.info("Starting application shutdown...");

        try {
            if (threadManager != null) {
                threadManager.shutdown();
                logger.info("ThreadManager shutdown complete");
            }

            logger.info("Application shutdown completed successfully");
        } catch (Exception e) {
            logger.error("Error during shutdown: {}", e.getMessage(), e);
        }
    }

    /**
     * Получение экземпляра ThreadManager (для тестирования)
     */
    public static ThreadManager getThreadManager() {
        return threadManager;
    }

    /**
     * Получение экземпляра TelegramBotService (для тестирования)
     */
    public static TelegramBotService getBotService() {
        return botService;
    }
}