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
            logger.info("Product Parser with Real-Time Cookies");
            logger.info("=".repeat(60));

            // Проверка конфигурации
            String botToken = Config.getTelegramBotToken();
            String botUsername = Config.getTelegramBotUsername();

            if (botToken.isEmpty() || botToken.equals("ВАШ_ТОКЕН_БОТА")) {
                logger.error("❌ Bot token not configured!");
                System.exit(1);
            }

            logger.info("✅ Configuration:");
            logger.info("   Token: {}...", botToken.substring(0, 10));
            logger.info("   Username: @{}", botUsername);
            logger.info("   Admin ID: {}", Config.getTelegramAdminId());

            // Инициализация хранилища
            FileStorage.ensureDataDir();
            logger.info("✅ Data directory ready");

            // Инициализация системы cookies
            logger.info("🍪 Инициализация системы cookies...");
            CookieService.initialize();

            // Тестирование cookies
            logger.info("🧪 Тестирование cookies...");
            if (!CookieService.testCookies()) {
                logger.warn("⚠️ Предупреждение: cookies могут быть недействительны");
            } else {
                logger.info("✅ Cookies работают корректно");
            }

            // Инициализация менеджера потоков
            threadManager = new ThreadManager();
            logger.info("✅ ThreadManager initialized");

            // Инициализация Telegram бота
            initializeTelegramBot(botToken);

            logger.info("=".repeat(60));
            logger.info("✅ Application started successfully!");
            logger.info("👑 Admin ID: {}", Config.getTelegramAdminId());
            logger.info("🍪 Dynamic cookies: {}", Config.isDynamicCookiesEnabled());
            logger.info("📋 Users: {}", WhitelistManager.getUserCount());
            logger.info("=".repeat(60));

            // Добавляем shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("🛑 Получен сигнал завершения...");
                shutdown();
            }));

            keepApplicationRunning();

        } catch (Exception e) {
            logger.error("❌ Startup error: {}", e.getMessage(), e);
            shutdown();
            System.exit(1);
        }
    }

    private static void initializeTelegramBot(String botToken) throws TelegramApiException {
        logger.info("🤖 Initializing Telegram bot...");

        botService = new TelegramBotService(botToken, threadManager);
        TelegramNotificationService.setBotInstance(botService);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(botService);

        logger.info("✅ Telegram bot registered and running");
    }

    private static void keepApplicationRunning() {
        try {
            logger.info("⏳ Application is running...");

            long lastStatusLog = System.currentTimeMillis();
            long lastCookieCheck = System.currentTimeMillis();

            while (true) {
                Thread.sleep(30000); // Проверка каждые 30 секунд

                long now = System.currentTimeMillis();

                // Логирование статуса каждые 5 минут
                if (now - lastStatusLog > 5 * 60 * 1000) {
                    if (threadManager.getActiveUsers().size() > 0) {
                        logger.info("📊 Active users: {}", threadManager.getActiveUsers().size());
                    }
                    lastStatusLog = now;
                }

                // Проверка cookies каждые 30 минут
                if (now - lastCookieCheck > 30 * 60 * 1000) {
                    logger.info("🔄 Проверка состояния cookies...");
                    if (!CookieService.hasValidCookies()) {
                        logger.warn("⚠️ Cookies недействительны, обновление...");
                        CookieService.refreshCookies("www.goofish.com");
                    }
                    lastCookieCheck = now;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Application interrupted");
        }
    }

    private static void shutdown() {
        logger.info("🛑 Shutting down...");

        if (threadManager != null) {
            threadManager.shutdown();
        }

        // Останавливаем CookieService
        CookieService.shutdown();

        logger.info("✅ Shutdown complete");
    }
}