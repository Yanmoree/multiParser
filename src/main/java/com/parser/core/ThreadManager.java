package com.parser.core;

import com.parser.config.Config;
import com.parser.model.Product;
import com.parser.model.UserSettings;
import com.parser.parser.ParserFactory;
import com.parser.parser.SiteParser;
import com.parser.service.CookieService;
import com.parser.storage.UserDataManager;
import com.parser.storage.UserSentProductsManager;
import com.parser.storage.WhitelistManager;
import com.parser.telegram.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Управление потоками парсеров пользователей
 */
public class ThreadManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadManager.class);

    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService scheduler;

    private int totalProductsFound = 0;
    private int totalRequestsMade = 0;
    private final Date startTime = new Date();

    public ThreadManager() {
        int coreSize = Config.getThreadPoolCoreSize();
        int maxSize = Config.getThreadPoolMaxSize();
        int keepAlive = Config.getInt("thread.pool.keepalive.seconds", 60);

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(50);
        threadPool = new ThreadPoolExecutor(coreSize, maxSize, keepAlive, TimeUnit.SECONDS,
                workQueue, new ThreadPoolExecutor.CallerRunsPolicy());

        scheduler = Executors.newScheduledThreadPool(1);

        // Логирование статистики каждые 10 минут
        scheduler.scheduleAtFixedRate(this::logStatistics, 10, 10, TimeUnit.MINUTES);

        // Автообновление кук каждые 2 часа
        if (Config.getCookieAutoUpdate()) {
            int interval = Config.getCookieUpdateInterval();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (Config.isDynamicCookiesEnabled()) {
                        logger.info("Auto-updating cookies...");
                        CookieService.refreshCookies("www.goofish.com");
                    }
                } catch (Exception e) {
                    logger.error("Cookie auto-update failed: {}", e.getMessage());
                }
            }, interval, interval, TimeUnit.MINUTES);
        }

        logger.info("ThreadManager initialized: core={}, max={}", coreSize, maxSize);
    }

    public boolean startUserParser(long userId) {
        logger.info("Starting parser for user {}", userId);

        // 🔴 ПРОВЕРКА WHITELIST
        if (!WhitelistManager.isUserAllowed(userId)) {
            logger.warn("User {} not in whitelist", userId);
            TelegramNotificationService.sendMessage(userId, "❌ You are not authorized to use this bot");
            return false;
        }

        if (userSessions.containsKey(userId) && userSessions.get(userId).isRunning()) {
            TelegramNotificationService.sendMessage(userId, "⚠️ Parser already running");
            return false;
        }

        List<String> queries = UserDataManager.getUserQueries(userId);
        if (queries.isEmpty()) {
            TelegramNotificationService.sendMessage(userId, "📭 No search queries added. Use /addquery");
            return false;
        }

        UserSettings settings = UserDataManager.getUserSettings(userId);
        UserSession session = new UserSession(userId, queries, settings);
        userSessions.put(userId, session);

        threadPool.submit(() -> runUserParser(session));

        logger.info("Parser started for user {}: {} queries", userId, queries.size());
        TelegramNotificationService.sendMessage(userId,
                "✅ Parser started!\nQueries: " + queries.size() + "\nCheck interval: " + settings.getCheckInterval() + " sec");

        return true;
    }

    private void runUserParser(UserSession session) {
        long userId = session.getUserId();
        session.setRunning(true);

        logger.info("Parser loop started for user {}", userId);

        try {
            SiteParser parser = ParserFactory.createParser("goofish");

            while (session.isRunning() && !Thread.currentThread().isInterrupted()) {
                for (String query : session.getQueries()) {
                    if (!session.isRunning()) break;

                    try {
                        List<Product> products = parser.search(
                                query,
                                session.getSettings().getMaxPages(),
                                session.getSettings().getRowsPerPage(),
                                session.getSettings().getMaxAgeMinutes()
                        );

                        totalRequestsMade++;
                        session.incrementRequestsMade();

                        if (!products.isEmpty()) {
                            session.addProductsFound(products.size());
                            totalProductsFound += products.size();

                            if (shouldSendNotification(session, products)) {
                                sendProductNotifications(userId, products, query, session.getSettings());
                            }

                            // Не перезаписываем историю каждый раз: добавляем новые товары к уже сохранённым
                            UserDataManager.addUserProducts(userId, products);
                        }

                        Thread.sleep(Config.getInt("api.goofish.delay.between.requests", 2000));

                    } catch (Exception e) {
                        logger.error("Error searching '{}' for user {}: {}", query, userId, e.getMessage());
                        session.incrementErrors();
                        session.setLastError("Search error: " + e.getMessage());

                        // Отправляем уведомление об ошибке пользователю
                        TelegramNotificationService.sendMessage(userId,
                                "❌ Ошибка при поиске '" + query + "': " + e.getMessage());

                        Thread.sleep(5000);
                    }
                }

                int interval = session.getSettings().getCheckInterval();
                for (int i = 0; i < interval && session.isRunning(); i++) {
                    Thread.sleep(1000);
                }

                session.setLastIterationTime(new Date());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Parser thread interrupted for user {}", userId);
        } catch (Exception e) {
            logger.error("Parser error for user {}: {}", userId, e.getMessage(), e);
            TelegramNotificationService.sendMessage(userId,
                    "❌ Критическая ошибка парсера: " + e.getMessage());
        } finally {
            session.setRunning(false);
            userSessions.remove(userId);
            String message = String.format("🛑 Parser stopped\nTotal found: %d products\nErrors: %d",
                    session.getTotalProductsFound(), session.getErrorsCount());
            TelegramNotificationService.sendMessage(userId, message);
        }
    }

    // Остальные методы остаются без изменений...
    private boolean shouldSendNotification(UserSession session, List<Product> products) {
        if (products.isEmpty()) return false;

        if (session.getSettings().isNotifyNewOnly()) {
            // Без использования ProductDuplicateFilter, чтобы избежать рекурсии
            List<Product> existingProducts = UserDataManager.getUserProducts(session.getUserId());
            Set<String> existingIds = new HashSet<>();
            for (Product p : existingProducts) {
                existingIds.add(p.getId());
            }

            for (Product p : products) {
                if (!existingIds.contains(p.getId())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }


    private void sendProductNotifications(long userId, List<Product> products, String query, UserSettings settings) {
        if (products == null || products.isEmpty()) {
            return;
        }

        logger.info("Отправка уведомлений: {} товаров для пользователя {}", products.size(), userId);

        // 🔴 ФИЛЬТРАЦИЯ ТОЛЬКО НОВЫХ ТОВАРОВ
        Set<String> productIds = new HashSet<>();
        Map<String, Product> productMap = new HashMap<>();

        for (Product p : products) {
            productIds.add(p.getId());
            productMap.put(p.getId(), p);
        }

        // Получаем только новые товары (не отправленные ранее)
        Set<String> newProductIds = UserSentProductsManager.filterNewProducts(userId, productIds);

        if (newProductIds.isEmpty()) {
            logger.debug("Нет новых товаров для отправки пользователю {}", userId);
            return;
        }

        // 🔴 СОБИРАЕМ НОВЫЕ ТОВАРЫ
        List<Product> newProducts = new ArrayList<>();
        for (String productId : newProductIds) {
            Product p = productMap.get(productId);
            if (p != null) {
                newProducts.add(p);
            }
        }

        logger.info("Будет отправлено {} новых товаров пользователю {}", newProducts.size(), userId);

        // 🔴 СОХРАНЯЕМ ТОВАРЫ КАК ОТПРАВЛЕННЫЕ
        UserSentProductsManager.markProductsAsSent(userId, newProductIds);

        // 🟢 ОТПРАВЛЯЕМ УВЕДОМЛЕНИЯ
        String summary = String.format("🔍 Найдено <b>%d новых товаров</b> по запросу: <i>%s</i>\n\n",
                newProducts.size(), escapeHtml(query));
        TelegramNotificationService.sendHtmlMessage(userId, summary);

        // Отправляем новые товары по одному
        for (int i = 0; i < newProducts.size(); i++) {
            Product p = newProducts.get(i);

            try {
                // Проверяем название товара
                if (p.getTitle() == null || p.getTitle().isEmpty() || "No title".equals(p.getTitle())) {
                    p.setTitle("Товар #" + p.getId() + " (" + query + ")");
                }

                if (p.hasCoverImage()) {
                    sendProductWithPhoto(userId, p, i + 1, newProducts.size());
                } else {
                    sendProductAsText(userId, p, i + 1, newProducts.size());
                }

                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Ошибка отправки уведомления для товара {}: {}", p.getId(), e.getMessage());
            }
        }

        // 🔴 ОБНОВЛЯЕМ СТАТИСТИКУ
        logger.info("✅ Отправлено {} новых товаров пользователю {}. {}",
                newProducts.size(), userId, UserSentProductsManager.getStats(userId));
    }

    private void sendProductWithPhoto(long userId, Product p, int number, int total) {
        try {
            // Создаем подпись под фото
            String caption = formatProductCaption(p, number, total);

            // Отправляем фото с подписью
            boolean sent = TelegramNotificationService.sendPhotoWithHtmlCaption(userId,
                    p.getCoverImageUrl(), caption);

            // Если не удалось отправить фото, отправляем текст
            if (!sent) {
                sendProductAsText(userId, p, number, total);
            }
        } catch (Exception e) {
            logger.warn("Failed to send photo for product {}, sending as text: {}",
                    p.getId(), e.getMessage());
            sendProductAsText(userId, p, number, total);
        }
    }

    private void sendProductAsText(long userId, Product p, int number, int total) {
        try {
            String message = formatProductCaption(p, number, total);
            TelegramNotificationService.sendHtmlMessage(userId, message);
        } catch (Exception e) {
            logger.error("Failed to send product text for {}: {}", p.getId(), e.getMessage());
        }
    }


    private String formatProductMessage(Product p, UserSettings settings) {
        return String.format("🛍️ <a href=\"%s\">%s</a>\n💰 %s\n📍 %s\n⏳ %s",
                p.getUrl(), escapeHtml(p.getTitle()), p.getPriceDisplay(),
                escapeHtml(p.getLocation()), p.getAgeDisplay());
    }

    private String getNumberEmoji(int number) {
        String[] emojis = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"};
        if (number > 0 && number <= emojis.length) {
            return emojis[number - 1];
        }
        return number + ".";
    }

    private String formatProductCaption(Product p, int number, int total) {
        // Номер товара с эмодзи
        String numberEmoji = getNumberEmoji(number);

        // Получаем название товара
        String fullTitle = p.getTitle();
        if (fullTitle == null || fullTitle.isEmpty() || "No title".equals(fullTitle)) {
            fullTitle = "Товар #" + p.getId();
        }

        // Название как гиперссылка с полным текстом
        String titleLink = String.format("<a href=\"%s\"><b>%s</b></a>",
                escapeHtml(p.getUrl()),
                escapeHtml(fullTitle));

        // 🔴 Цена: только в юанях (убираем рубли и локацию)
        String price = String.format("💰 <b>%s ¥</b>",
                String.format("%.0f", p.getPrice()));

        // 🔴 ВОЗРАСТ ТОВАРА
        String age = String.format("⏳ %s", p.getAgeDisplay());

        // 🔴 УБРАЛИ ЛОКАЦИЮ, Собираем итоговое сообщение
        return String.format("%s %s\n\n%s\n%s",
                numberEmoji, titleLink, price, age);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br/>"); // Добавляем переносы строк
    }

    public boolean stopUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null) {
            session.setRunning(false);
            userSessions.remove(userId);
            logger.info("Parser stopped for user {}", userId);

            return true;
        }
        return false;
    }

    public boolean pauseUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.isRunning()) {
            session.setPaused(true);
            logger.info("Parser paused for user {}", userId);
            return true;
        }
        return false;
    }

    public boolean resumeUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.isPaused()) {
            session.setPaused(false);
            logger.info("Parser resumed for user {}", userId);
            return true;
        }
        return false;
    }

    public Map<String, Object> getUserStatus(long userId) {
        UserSession session = userSessions.get(userId);
        return session != null ? session.getDetailedStatus() : null;
    }

    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userSessions.size());
        stats.put("totalProductsFound", totalProductsFound);
        stats.put("totalRequestsMade", totalRequestsMade);
        stats.put("uptime", System.currentTimeMillis() - startTime.getTime());
        stats.put("activeThreads", threadPool.getActiveCount());
        stats.put("poolSize", threadPool.getPoolSize());
        stats.put("dynamicCookiesEnabled", Config.isDynamicCookiesEnabled());
        return stats;
    }

    private void logStatistics() {
        logger.info("Stats: users={}, products={}, requests={}, threads={}/{}",
                userSessions.size(), totalProductsFound, totalRequestsMade,
                threadPool.getActiveCount(), threadPool.getPoolSize());
    }

    public List<Long> getActiveUsers() {
        return new ArrayList<>(userSessions.keySet());
    }

    public boolean isUserParserRunning(long userId) {
        UserSession session = userSessions.get(userId);
        return session != null && session.isRunning();
    }

    public void shutdown() {
        logger.info("Shutting down ThreadManager...");

        for (long userId : new ArrayList<>(userSessions.keySet())) {
            stopUserParser(userId);
        }

        threadPool.shutdown();
        scheduler.shutdown();

        try {
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            logger.info("ThreadManager shutdown complete");
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}