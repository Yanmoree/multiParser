package com.parser.core;

import com.parser.config.Config;
import com.parser.config.ParserSettings;
import com.parser.model.Product;
import com.parser.model.UserSettings;
import com.parser.parser.ParserFactory;
import com.parser.parser.SiteParser;
import com.parser.service.CookieService;
import com.parser.storage.UserDataManager;
import com.parser.storage.WhitelistManager;
import com.parser.telegram.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * Менеджер потоков для управления парсерами пользователей
 */
public class ThreadManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadManager.class);

    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService scheduler;

    // Статистика
    private int totalProductsFound = 0;
    private int totalRequestsMade = 0;
    private long totalRuntime = 0;
    private final Date startTime = new Date();

    public ThreadManager() {
        int corePoolSize = Config.getInt("thread.pool.core.size", 5);
        int maxPoolSize = Config.getInt("thread.pool.max.size", 20);
        int keepAliveTime = Config.getInt("thread.pool.keepalive.seconds", 60);
        int queueCapacity = Config.getInt("thread.pool.queue.capacity", 100);

        // Создание пула потоков
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        threadPool = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Создание планировщика для периодических задач
        scheduler = Executors.newScheduledThreadPool(2);

        // Запуск задачи для логирования статистики
        scheduler.scheduleAtFixedRate(this::logStatistics, 5, 5, TimeUnit.MINUTES);

        // Запуск задачи для автообновления кук
        if (Config.getBoolean("cookie.auto.update", true)) {
            int intervalMinutes = Config.getInt("cookie.update.interval.minutes", 60);
            scheduler.scheduleAtFixedRate(
                    this::updateCookiesTask,
                    intervalMinutes,
                    intervalMinutes,
                    TimeUnit.MINUTES
            );
            logger.info("Cookie auto-update scheduled every {} minutes", intervalMinutes);
        }

        // Запуск задачи для очистки устаревших кук
        scheduler.scheduleAtFixedRate(
                this::cleanupExpiredCookiesTask,
                10,
                10,
                TimeUnit.MINUTES
        );

        logger.info("ThreadManager initialized. Pool: {}-{} threads, queue: {}",
                corePoolSize, maxPoolSize, queueCapacity);
        logger.info("Dynamic cookies enabled: {}", Config.isDynamicCookiesEnabled());
    }

    /**
     * Задача для автообновления кук
     */
    private void updateCookiesTask() {
        try {
            if (Config.isDynamicCookiesEnabled()) {
                logger.info("🔄 Auto-updating cookies...");
                CookieService.refreshCookies("h5api.m.goofish.com");
                logger.info("✅ Cookies auto-updated successfully");
            }
        } catch (Exception e) {
            logger.error("❌ Error in cookies auto-update task: {}", e.getMessage());
        }
    }

    /**
     * Задача для очистки устаревших кук
     */
    private void cleanupExpiredCookiesTask() {
        try {
            logger.debug("🔄 Running expired cookies cleanup task");
            // Здесь можно добавить логику очистки устаревших кук
        } catch (Exception e) {
            logger.error("❌ Error in expired cookies cleanup task: {}", e.getMessage());
        }
    }

    /**
     * Запуск парсера для пользователя
     */
    public boolean startUserParser(long userId) {
        logger.info("Attempting to start parser for user {}", userId);

        // Детальная информация о пользователе
        boolean isInWhitelist = WhitelistManager.isUserAllowed(userId);
        logger.info("User {} whitelist status: {}", userId, isInWhitelist);

        if (!isInWhitelist) {
            logger.warn("User {} NOT in whitelist. Cannot start parser.", userId);
            List<Long> allUsers = WhitelistManager.getAllUsers();
            logger.info("Current whitelist contains {} users: {}", allUsers.size(), allUsers);

            TelegramNotificationService.sendMessage(userId,
                    "⛔ Вы не авторизованы для использования парсера.\n" +
                            "Используйте команду /start для регистрации\n\n" +
                            "ℹ️ Отладочная информация:\n" +
                            "• Ваш ID: " + userId + "\n" +
                            "• Пользователей в системе: " + allUsers.size() + "\n" +
                            "• Используйте /checkwhitelist для проверки статуса");
            return false;
        }

        if (userSessions.containsKey(userId)) {
            UserSession session = userSessions.get(userId);
            if (session.isRunning()) {
                logger.warn("Parser already running for user {}", userId);
                TelegramNotificationService.sendMessage(userId,
                        "⚠️ Парсер уже запущен для вашего аккаунта");
                return false;
            }
            stopUserParser(userId);
        }

        List<String> queries = UserDataManager.getUserQueries(userId);
        if (queries.isEmpty()) {
            logger.warn("User {} has no queries", userId);
            TelegramNotificationService.sendMessage(userId,
                    "📭 У вас нет поисковых запросов.\n" +
                            "Добавьте запросы командой /addquery [текст]");
            return false;
        }

        logger.info("User {} has {} queries: {}", userId, queries.size(), queries);

        UserSettings settings = UserDataManager.getUserSettings(userId);

        UserSession session = new UserSession(userId, queries, settings);
        userSessions.put(userId, session);

        if (Config.isDynamicCookiesEnabled()) {
            try {
                logger.info("Refreshing cookies before starting parser for user {}", userId);
                CookieService.refreshCookies("h5api.m.goofish.com");
            } catch (Exception e) {
                logger.warn("Failed to refresh cookies before starting parser for user {}: {}",
                        userId, e.getMessage());
            }
        }

        try {
            String dataDir = Config.getString("storage.data.dir", "./data");
            new File(dataDir + "/user_settings").mkdirs();
            new File(dataDir + "/user_products").mkdirs();
            logger.debug("Created user directories in {}", dataDir);
        } catch (Exception e) {
            logger.error("Failed to create user directories: {}", e.getMessage());
        }

        threadPool.submit(() -> {
            try {
                runUserParser(session);
            } catch (Exception e) {
                logger.error("Error in parser for user {}: {}", userId, e.getMessage(), e);
                TelegramNotificationService.sendMessage(userId,
                        "❌ Ошибка в работе парсера: " + e.getMessage());
                userSessions.remove(userId);
            }
        });

        logger.info("Parser started for user {}", userId);
        TelegramNotificationService.sendMessage(userId,
                "✅ Парсер успешно запущен!\n\n" +
                        "📊 **Детали:**\n" +
                        "• Запросов: " + queries.size() + "\n" +
                        "• Интервал проверки: " + settings.getCheckInterval() + " сек\n" +
                        "• Макс. возраст товара: " + settings.getMaxAgeMinutes() + " мин\n" +
                        "• Страниц для парсинга: " + settings.getMaxPages() + "\n\n" +
                        "🛑 Для остановки используйте /stop_parser");

        return true;
    }

    /**
     * Основной цикл работы парсера для пользователя
     */
    private void runUserParser(UserSession session) {
        final long userId = session.getUserId();
        session.setRunning(true);
        session.setStartTime(new Date());

        logger.info("Parser loop started for user {}", userId);

        try {
            SiteParser parser = ParserFactory.createParser("goofish");

            while (session.isRunning() && !Thread.currentThread().isInterrupted()) {
                long iterationStartTime = System.currentTimeMillis();
                int productsFoundInIteration = 0;

                for (String query : session.getQueries()) {
                    if (!session.isRunning() || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    try {
                        logger.debug("Searching '{}' for user {}", query, userId);

                        List<Product> products = parser.search(
                                query,
                                session.getSettings().getMaxPages(),
                                session.getSettings().getRowsPerPage(),
                                session.getSettings().getMaxAgeMinutes()
                        );

                        totalRequestsMade++;
                        session.incrementRequestsMade();

                        if (!products.isEmpty()) {
                            productsFoundInIteration += products.size();
                            session.addProductsFound(products.size());
                            totalProductsFound += products.size();

                            logger.info("Found {} products for query '{}' (user {})",
                                    products.size(), query, userId);

                            if (shouldSendNotification(session, products)) {
                                sendProductNotifications(userId, products, query, session.getSettings());
                            }

                            UserDataManager.saveUserProducts(userId, products);
                        }

                        Thread.sleep(Config.getInt("api.goofish.delay.between.requests", 2000));

                    } catch (Exception e) {
                        logger.error("Error searching query '{}' for user {}: {}",
                                query, userId, e.getMessage(), e);
                        session.incrementErrors();

                        if (isCookieRelatedError(e)) {
                            logger.warn("Cookie-related error detected for user {}, refreshing cookies...", userId);
                            try {
                                CookieService.refreshCookies("h5api.m.goofish.com");
                                logger.info("Cookies refreshed for user {}", userId);
                            } catch (Exception cookieError) {
                                logger.error("Failed to refresh cookies for user {}: {}",
                                        userId, cookieError.getMessage());
                            }
                        }

                        Thread.sleep(5000);
                    }
                }

                if (productsFoundInIteration > 0) {
                    logger.info("Iteration completed for user {}: found {} products",
                            userId, productsFoundInIteration);
                }

                int checkInterval = session.getSettings().getCheckInterval();
                logger.debug("Waiting {} seconds for next check (user {})",
                        checkInterval, userId);

                for (int i = 0; i < checkInterval && session.isRunning(); i++) {
                    Thread.sleep(1000);
                }

                session.setLastIterationTime(new Date());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Parser thread interrupted for user {}", userId);
        } catch (Exception e) {
            logger.error("Unexpected error in parser for user {}: {}", userId, e.getMessage(), e);
        } finally {
            session.setRunning(false);
            session.setEndTime(new Date());
            userSessions.remove(userId);

            logger.info("Parser stopped for user {}", userId);
            TelegramNotificationService.sendMessage(userId,
                    "🛑 Парсер остановлен\n" +
                            "Всего найдено товаров: " + session.getTotalProductsFound());
        }
    }

    /**
     * Проверка, связана ли ошибка с куками
     */
    private boolean isCookieRelatedError(Exception e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }

        String message = e.getMessage().toLowerCase();
        return message.contains("cookie") ||
                message.contains("session") ||
                message.contains("auth") ||
                message.contains("401") ||
                message.contains("403") ||
                message.contains("unauthorized") ||
                message.contains("forbidden") ||
                message.contains("未登录") ||
                message.contains("未授权") ||
                message.contains("令牌") ||
                message.contains("非法请求");
    }

    /**
     * Проверка необходимости отправки уведомления
     */
    private boolean shouldSendNotification(UserSession session, List<Product> products) {
        UserSettings settings = session.getSettings();

        if (products.isEmpty()) {
            return false;
        }

        if (settings.isNotifyNewOnly()) {
            List<Product> newProducts = UserDataManager.filterNewProducts(
                    session.getUserId(), products);
            return !newProducts.isEmpty();
        }

        return true;
    }

    /**
     * Отправка уведомлений о товарах в Telegram с HTML форматированием и изображениями
     */
    private void sendProductNotifications(long userId, List<Product> products,
                                          String query, UserSettings settings) {
        if (products.isEmpty()) return;

        List<Product> productsToNotify = settings.isNotifyNewOnly() ?
                UserDataManager.filterNewProducts(userId, products) : products;

        if (productsToNotify.isEmpty()) return;

        logger.info("Sending notifications for {} products to user {}",
                productsToNotify.size(), userId);

        // Логируем информацию о изображениях
        int totalImages = 0;
        int productsWithImages = 0;
        for (Product product : productsToNotify) {
            if (product.hasCoverImage()) {
                totalImages++;
                productsWithImages++;
                logger.debug("Product '{}' has image: {}",
                        product.getShortTitle(), product.getCoverImageUrl());
            }
        }
        logger.info("Found {} products with images (total {} images)",
                productsWithImages, totalImages);

        // Сначала отправляем общее уведомление
        String summary = String.format(
                "🔍 Найдено товаров: %d\n📝 По запросу: \"%s\"\n📸 Товаров с фото: %d",
                productsToNotify.size(), escapeHtml(query), productsWithImages
        );
        TelegramNotificationService.sendMessage(userId, summary);

        // Затем отправляем каждый товар
        for (int i = 0; i < productsToNotify.size(); i++) {
            Product product = productsToNotify.get(i);

            try {
                // Проверяем, есть ли у товара изображение
                if (product.hasCoverImage()) {
                    String imageUrl = product.getCoverImageUrl();
                    logger.debug("Sending photo for product {}: {}", product.getShortTitle(), imageUrl);

                    // Формируем подпись с HTML
                    String caption = formatProductCaption(product, settings);

                    // Отправляем фото с подписью
                    boolean photoSent = TelegramNotificationService.sendPhotoWithHtmlCaption(
                            userId,
                            imageUrl,
                            caption
                    );

                    // Если фото не отправилось, отправляем текстовое сообщение
                    if (!photoSent) {
                        logger.warn("Failed to send photo, falling back to text message");
                        String message = formatProductMessage(product, settings, i + 1, productsToNotify.size());
                        TelegramNotificationService.sendHtmlMessage(userId, message);
                    } else {
                        logger.debug("Photo sent successfully for product {}", product.getId());
                    }
                } else {
                    // Если нет фото, отправляем только текстовое сообщение
                    logger.debug("Product {} has no image, sending text only", product.getShortTitle());
                    String message = formatProductMessage(product, settings, i + 1, productsToNotify.size());
                    TelegramNotificationService.sendHtmlMessage(userId, message);
                }

                // Задержка между сообщениями чтобы избежать блокировки
                Thread.sleep(1500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error sending notification for product {}: {}",
                        product.getId(), e.getMessage());

                // Пробуем отправить хотя бы текстовое сообщение
                try {
                    String message = formatProductMessage(product, settings, i + 1, productsToNotify.size());
                    TelegramNotificationService.sendHtmlMessage(userId, message);
                } catch (Exception ex) {
                    logger.error("Failed to send fallback message: {}", ex.getMessage());
                }
            }
        }

        logger.info("Finished sending notifications for {} products", productsToNotify.size());
    }

    /**
     * Форматирование сообщения о товаре с HTML
     */
    private String formatProductMessage(Product product, UserSettings settings,
                                        int index, int total) {
        StringBuilder message = new StringBuilder();

        if (total > 1) {
            message.append("<b>🎯 Товар ").append(index).append(" из ").append(total).append("</b>\n\n");
        }

        String title = escapeHtml(product.getTitle());
        message.append("🛍️ <a href=\"").append(product.getUrl()).append("\">").append(title).append("</a>\n\n");

        message.append("<b>💰 Цены:</b>\n");
        message.append("• ").append(product.getPriceDisplay()).append(" (юани)\n");

        if (ParserSettings.CURRENCY_RUBLES.equals(settings.getPriceCurrency())) {
            message.append("• ").append(product.getPriceDisplayRub()).append(" (рубли)\n");
        }

        message.append("\n<b>📍 Местоположение:</b> ").append(escapeHtml(product.getLocation())).append("\n");
        message.append("<b>⏳ Возраст:</b> ").append(product.getAgeDisplay()).append("\n");

        if (product.getSeller() != null && !product.getSeller().isEmpty()) {
            message.append("<b>👤 Продавец:</b> ").append(escapeHtml(product.getSeller())).append("\n");
        }

        message.append("\n🔗 <b>Ссылка:</b> <a href=\"").append(product.getUrl()).append("\">").append(product.getUrl()).append("</a>");

        return message.toString();
    }

    /**
     * Форматирование подписи для фото с HTML
     */
    private String formatProductCaption(Product product, UserSettings settings) {
        StringBuilder caption = new StringBuilder();

        String title = escapeHtml(product.getTitle());
        caption.append("<b>🛍️ ").append(title).append("</b>\n\n");

        caption.append("<b>💰 Цена:</b> ");
        caption.append(product.getPriceDisplay());

        if (ParserSettings.CURRENCY_RUBLES.equals(settings.getPriceCurrency())) {
            caption.append(" (").append(product.getPriceDisplayRub()).append(")");
        }

        caption.append("\n<b>📍 Локация:</b> ").append(escapeHtml(product.getLocation()));
        caption.append("\n<b>⏳ Возраст:</b> ").append(product.getAgeDisplay());

        if (product.getSeller() != null && !product.getSeller().isEmpty()) {
            caption.append("\n<b>👤 Продавец:</b> ").append(escapeHtml(product.getSeller()));
        }

        caption.append("\n\n<a href=\"").append(product.getUrl()).append("\">🔗 Открыть товар</a>");

        return caption.toString();
    }

    /**
     * Экранирование HTML символов
     */
    private String escapeHtml(String text) {
        if (text == null) return "";

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Остановка парсера для пользователя
     */
    public boolean stopUserParser(long userId) {
        logger.info("Attempting to stop parser for user {}", userId);

        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setRunning(false);
            userSessions.remove(userId);

            logger.info("Parser stopped for user {}", userId);
            TelegramNotificationService.sendMessage(userId,
                    "🛑 Парсер остановлен по вашему запросу");
            return true;
        }

        logger.warn("No active parser found for user {}", userId);
        TelegramNotificationService.sendMessage(userId,
                "ℹ️ Парсер не запущен");
        return false;
    }

    /**
     * Приостановка парсера для пользователя
     */
    public boolean pauseUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.isRunning()) {
            session.setPaused(true);
            logger.info("Parser paused for user {}", userId);
            TelegramNotificationService.sendMessage(userId,
                    "⏸ Парсер приостановлен");
            return true;
        }
        return false;
    }

    /**
     * Возобновление парсера для пользователя
     */
    public boolean resumeUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.isPaused()) {
            session.setPaused(false);
            logger.info("Parser resumed for user {}", userId);
            TelegramNotificationService.sendMessage(userId,
                    "▶️ Парсер возобновлен");
            return true;
        }
        return false;
    }

    /**
     * Получение статусов всех активных парсеров
     */
    public Map<Long, Map<String, Object>> getAllStatuses() {
        Map<Long, Map<String, Object>> statuses = new HashMap<>();

        for (Map.Entry<Long, UserSession> entry : userSessions.entrySet()) {
            statuses.put(entry.getKey(), entry.getValue().getDetailedStatus());
        }

        return statuses;
    }

    /**
     * Получение статуса конкретного пользователя
     */
    public Map<String, Object> getUserStatus(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            return session.getDetailedStatus();
        }
        return null;
    }

    /**
     * Получение общей статистики
     */
    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalUsers", userSessions.size());
        stats.put("totalProductsFound", totalProductsFound);
        stats.put("totalRequestsMade", totalRequestsMade);
        stats.put("uptime", System.currentTimeMillis() - startTime.getTime());
        stats.put("activeThreads", threadPool.getActiveCount());
        stats.put("poolSize", threadPool.getPoolSize());
        stats.put("queueSize", threadPool.getQueue().size());
        stats.put("startTime", startTime);

        Map<String, Object> cookieStats = CookieService.getCacheStats();
        stats.put("cookieCacheDomains", cookieStats.get("totalDomains"));
        stats.put("cookieCacheSize", cookieStats.get("totalCookies"));
        stats.put("dynamicCookiesEnabled", Config.isDynamicCookiesEnabled());

        return stats;
    }

    /**
     * Логирование статистики
     */
    private void logStatistics() {
        if (logger.isInfoEnabled()) {
            Map<String, Object> stats = getGlobalStatistics();
            logger.info("Statistics: {} active users, {} total products found, {} active threads, {} cookie cache domains",
                    stats.get("totalUsers"), stats.get("totalProductsFound"),
                    stats.get("activeThreads"), stats.get("cookieCacheDomains"));
        }
    }

    /**
     * Корректное завершение работы менеджера
     */
    public void shutdown() {
        logger.info("Shutting down ThreadManager...");

        List<Long> userIds = new ArrayList<>(userSessions.keySet());
        for (Long userId : userIds) {
            stopUserParser(userId);
        }

        threadPool.shutdown();
        scheduler.shutdown();

        try {
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                logger.warn("Thread pool did not terminate gracefully");
            }

            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            logger.info("ThreadManager shutdown complete");
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("ThreadManager shutdown interrupted");
        }
    }

    /**
     * Проверка активности парсера пользователя
     */
    public boolean isUserParserRunning(long userId) {
        UserSession session = userSessions.get(userId);
        return session != null && session.isRunning();
    }

    /**
     * Получение списка активных пользователей
     */
    public List<Long> getActiveUsers() {
        return new ArrayList<>(userSessions.keySet());
    }

    /**
     * Принудительное обновление кук для всех активных парсеров
     */
    public void refreshCookiesForAll() {
        if (!Config.isDynamicCookiesEnabled()) {
            logger.info("Dynamic cookies disabled, skipping refresh for all");
            return;
        }

        logger.info("Refreshing cookies for all active parsers...");

        try {
            CookieService.refreshCookies("h5api.m.goofish.com");
            logger.info("Cookies refreshed for all active parsers");

            // Исправляем эту строку - добавляем явное приведение типа
            long adminId = Config.getInt("telegram.admin.id", 0);
            if (adminId > 0) {
                TelegramNotificationService.sendMessage(adminId,
                        "🔄 Куки обновлены для всех активных парсеров"
                );
            }
        } catch (Exception e) {
            logger.error("Failed to refresh cookies for all parsers: {}", e.getMessage());
        }
    }


}