package com.parser;

import com.parser.config.Config;
import com.parser.core.ThreadManager;
import com.parser.service.CookieService;
import com.parser.storage.FileStorage;
import com.parser.telegram.TelegramBotService;
import com.parser.telegram.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Главный класс приложения - точка входа
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static ThreadManager threadManager;
    private static TelegramBotService botService;

    public static void main(String[] args) {
        try {
            logger.info("=== Product Parser with Dynamic Cookies ===");
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

            logger.info("Bot token: {}...", botToken.substring(0, Math.min(10, botToken.length())));
            logger.info("Bot username: @{}", botUsername);
            logger.info("Admin ID: {}", Config.getTelegramAdminId());

            // Создаем директории для данных
            try {
                FileStorage.ensureDataDir();
                logger.info("Data directories created");
            } catch (Exception e) {
                logger.error("Failed to create data directories: {}", e.getMessage());
            }

            // Инициализация менеджера потоков
            threadManager = new ThreadManager();
            logger.info("ThreadManager initialized");

            // Запуск Telegram бота (СНАЧАЛА бота, потом cookies)
            logger.info("🔄 Step 1: Initializing Telegram bot...");
            initializeTelegramBot(botToken);

            // Установка бота для сервиса уведомлений
            if (botService != null) {
                TelegramNotificationService.setBotInstance(botService);
                logger.info("✅ TelegramNotificationService initialized with bot instance");
            } else {
                logger.error("❌ Bot service is null! Telegram functionality will not work");
                // Продолжаем без бота для отладки
            }

            // Проверка валидности кук при старте
            logger.info("🔄 Step 2: Validating cookies...");
            validateCookiesOnStart();

            logger.info("================================================");
            logger.info("✅ Application startup sequence completed!");

            if (botService != null) {
                logger.info("🤖 Telegram bot: @{} - READY", botUsername);
            } else {
                logger.info("🤖 Telegram bot: NOT INITIALIZED");
            }

            logger.info("👑 Admin ID: {}", Config.getTelegramAdminId());
            logger.info("🍪 Dynamic cookies: {}", Config.isDynamicCookiesEnabled() ? "ENABLED" : "DISABLED");
            logger.info("================================================");

            if (botService != null) {
                logger.info("📱 Send /start to @{} in Telegram", botUsername);
            } else {
                logger.info("⚠️ Telegram bot is not available. Check logs above.");
            }

            logger.info("================================================");

            // Бесконечный цикл для поддержания работы приложения
            keepApplicationRunning();

        } catch (Exception e) {
            logger.error("❌ Fatal error during startup: {}", e.getMessage(), e);
            shutdown();
            System.exit(1);
        }
    }

    /**
     * Инициализация Telegram бота
     */
    private static void initializeTelegramBot(String botToken) {
        try {
            logger.info("🤖 Creating TelegramBotService instance...");

            // Создаем экземпляр бота
            botService = new TelegramBotService(botToken, threadManager);
            logger.info("✅ TelegramBotService instance created");

            // Пробуем получить username
            String username = botService.getBotUsername();
            logger.info("✅ Bot username retrieved: @{}", username);

            logger.info("🤖 Registering bot with Telegram API...");

            // Создаем TelegramBotsApi
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            logger.info("✅ TelegramBotsApi created");

            // Регистрируем бота
            botsApi.registerBot(botService);

            logger.info("🎉 Telegram bot registered successfully!");
            logger.info("✅ Bot is now listening for messages...");

        } catch (TelegramApiException e) {
            logger.error("❌ TelegramApiException: {}", e.getMessage());

            if (e.getMessage() != null) {
                if (e.getMessage().contains("409") || e.getMessage().contains("terminated by other getUpdates")) {
                    logger.error("❌ Another bot instance is already running!");
                    logger.error("❌ Please stop the previous instance or wait 1 minute");
                } else if (e.getMessage().contains("401")) {
                    logger.error("❌ Invalid bot token!");
                    logger.error("❌ Please check your bot token in config.properties");
                } else if (e.getMessage().contains("timed out") || e.getMessage().contains("connect")) {
                    logger.error("❌ Cannot connect to Telegram API!");
                    logger.error("❌ Check your internet connection or VPN");
                }
            }

            logger.error("❌ Full exception:", e);
            botService = null;

        } catch (Exception e) {
            logger.error("❌ Unexpected error initializing Telegram bot: {}", e.getMessage(), e);
            botService = null;
        }
    }

    /**
     * Проверка валидности кук при старте
     */
    private static void validateCookiesOnStart() {
        logger.info("🍪 Checking cookies...");

        try {
            // Получаем свежие куки при старте, если включены динамические куки
            if (Config.isDynamicCookiesEnabled()) {
                logger.info("🔄 Fetching fresh cookies via Selenium...");
                boolean refreshed = CookieService.refreshCookies("h5api.m.goofish.com");
                if (refreshed) {
                    logger.info("✅ Fresh cookies fetched successfully");
                } else {
                    logger.warn("⚠️ Failed to fetch fresh cookies, using static cookies");
                }
            } else {
                logger.info("ℹ️ Dynamic cookies disabled, using static cookies");
            }

        } catch (Exception e) {
            logger.error("❌ Error validating cookies: {}", e.getMessage());
            logger.warn("⚠️ Cookies validation failed, but continuing...");
        }
    }

    /**
     * Поддержание работы приложения
     */
    private static void keepApplicationRunning() {
        try {
            logger.info("⏳ Entering main loop...");

            // Простой цикл для поддержания работы
            int counter = 0;
            while (true) {
                Thread.sleep(30000); // Спим 30 секунд
                counter++;

                logger.debug("⏱️ Heartbeat #{}", counter);

                // Периодическая проверка состояния
                if (botService == null && (counter % 2 == 0)) { // Каждые 60 секунд
                    logger.warn("⚠️ Bot service is null, trying to reinitialize...");
                    try {
                        String botToken = Config.getString("telegram.bot.token", "");
                        if (!botToken.isEmpty()) {
                            logger.info("🔄 Reinitializing bot...");
                            initializeTelegramBot(botToken);
                            if (botService != null) {
                                TelegramNotificationService.setBotInstance(botService);
                                logger.info("✅ Bot reinitialized successfully");
                            }
                        }
                    } catch (Exception e) {
                        logger.error("❌ Failed to reinitialize bot: {}", e.getMessage());
                    }
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
        logger.info("🛑 Starting application shutdown...");

        try {
            if (threadManager != null) {
                threadManager.shutdown();
                logger.info("✅ ThreadManager shutdown complete");
            }

            logger.info("✅ Application shutdown completed successfully");
        } catch (Exception e) {
            logger.error("❌ Error during shutdown: {}", e.getMessage(), e);
        }
    }
}