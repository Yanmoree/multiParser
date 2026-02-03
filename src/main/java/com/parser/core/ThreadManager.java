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

        // 🔴 ПРОВЕРКА COOKIES ПЕРЕД АВТООБНОВЛЕНИЕМ
        if (Config.getCookieAutoUpdate() && CookieService.hasValidCookies()) {
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
        } else {
            logger.warn("⚠️ Cookie auto-update disabled: cookies not valid");
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

        // 🔴 ПРОВЕРКА COOKIES ПЕРЕД ЗАПУСКОМ
        if (!CookieService.hasValidCookies()) {
            logger.error("Cannot start parser for user {}: cookies not valid", userId);
            TelegramNotificationService.sendMessage(userId,
                    "❌ Parser cannot start: cookies not valid!\n" +
                            "Please wait for administrator to refresh cookies.");
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
                // 🔴 ПРОВЕРКА COOKIES ПЕРЕД КАЖДОЙ ИТЕРАЦИЕЙ
                if (!CookieService.hasValidCookies()) {
                    logger.warn("Cookies invalid for user {}, pausing parser", userId);
                    TelegramNotificationService.sendMessage(userId,
                            "⚠️ Parser paused: cookies need refresh\n" +
                                    "Waiting for administrator action...");

                    // Ждем восстановления cookies
                    while (!CookieService.hasValidCookies() && session.isRunning()) {
                        Thread.sleep(60000); // Проверяем каждую минуту
                    }

                    if (session.isRunning()) {
                        TelegramNotificationService.sendMessage(userId, "✅ Cookies restored, resuming parser");
                    }
                }

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

                            sendProductNotifications(userId, products, query, session.getSettings());

                            UserDataManager.addUserProducts(userId, products);
                        }

                        Thread.sleep(Config.getInt("api.goofish.delay.between.requests", 2000));

                    } catch (Exception e) {
                        logger.error("Error searching '{}' for user {}: {}", query, userId, e.getMessage());
                        session.incrementErrors();
                        session.setLastError("Search error: " + e.getMessage());

                        TelegramNotificationService.sendMessage(userId,
                                "❌ Ошибка при поиске '" + query + "': " + e.getMessage());

                        Thread.sleep(5000);
                    }
                }

                // Обновляем cookies после полного цикла
                if (session.isRunning() && Config.isDynamicCookiesEnabled() && CookieService.hasValidCookies()) {
                    try {
                        logger.info("🔄 Обновление cookies после цикла (user={})", userId);
                        CookieService.refreshCookies("www.goofish.com");
                    } catch (Exception e) {
                        logger.warn("Cookie refresh after cycle failed (user={}): {}", userId, e.getMessage());
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

    private void sendProductNotifications(long userId, List<Product> products, String query, UserSettings settings) {
        if (products == null || products.isEmpty()) {
            return;
        }

        logger.info("Отправка уведомлений: {} товаров для пользователя {}", products.size(), userId);

        Set<String> productIds = new HashSet<>();
        Map<String, Product> productMap = new HashMap<>();

        for (Product p : products) {
            productIds.add(p.getId());
            productMap.put(p.getId(), p);
        }

        Set<String> sentProductIds = UserSentProductsManager.getSentProductsForUser(userId);
        Set<String> newProductIds = new HashSet<>();

        for (String productId : productIds) {
            if (!sentProductIds.contains(productId)) {
                newProductIds.add(productId);
            }
        }

        if (newProductIds.isEmpty()) {
            logger.debug("Нет новых товаров для пользователя {}", userId);
            return;
        }

        List<Product> productsToSend = new ArrayList<>();
        for (String productId : newProductIds) {
            Product p = productMap.get(productId);
            if (p != null) {
                productsToSend.add(p);
            }
        }

        logger.info("Будет отправлено {} новых товаров пользователю {} (всего найдено {})",
                productsToSend.size(), userId, products.size());

        UserSentProductsManager.markProductsAsSent(userId, newProductIds);

        String summary = String.format("🔍 Найдено <b>%d новых товаров</b> по запросу: <i>%s</i>\n\n",
                productsToSend.size(), escapeHtml(query));
        TelegramNotificationService.sendHtmlMessage(userId, summary);

        for (int i = 0; i < productsToSend.size(); i++) {
            Product p = productsToSend.get(i);

            try {
                if (p.hasCoverImage()) {
                    sendProductWithPhoto(userId, p, i + 1, productsToSend.size());
                } else {
                    sendProductAsText(userId, p, i + 1, productsToSend.size());
                }

                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Ошибка отправки уведомления для товара {}: {}", p.getId(), e.getMessage());
            }
        }
    }

    private void sendProductWithPhoto(long userId, Product p, int number, int total) {
        try {
            String caption = formatProductCaption(p, number, total);

            boolean sent = TelegramNotificationService.sendPhotoWithHtmlCaption(userId,
                    p.getCoverImageUrl(), caption);

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

    private String formatProductCaption(Product p, int number, int total) {
        String fullTitle = p.getTitle();
        if (fullTitle == null || fullTitle.isEmpty() || "No title".equals(fullTitle)) {
            fullTitle = "Товар #" + p.getId();
        }

        String titleLink = String.format("<a href=\"%s\"><b>%s</b></a>",
                escapeHtml(p.getUrl()),
                escapeHtml(fullTitle));

        double priceYuan = p.getPrice();
        double priceRub = priceYuan * 12;
        String price = String.format("💰 <b>%.0f ¥</b> (≈%.0f руб.)",
                priceYuan, priceRub);

        String age = String.format("⏳ %s", p.getAgeDisplay());

        String location = "";
        if (p.getLocation() != null && !p.getLocation().isEmpty() && !"Не указано".equals(p.getLocation())) {
            location = String.format("\n📍 %s", escapeHtml(p.getLocation()));
        }

        return String.format("%s\n\n%s\n%s%s", titleLink, price, age, location);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br/>");
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
        stats.put("cookiesValid", CookieService.hasValidCookies());
        return stats;
    }

    private void logStatistics() {
        logger.info("Stats: users={}, products={}, requests={}, threads={}/{}, cookiesValid={}",
                userSessions.size(), totalProductsFound, totalRequestsMade,
                threadPool.getActiveCount(), threadPool.getPoolSize(),
                CookieService.hasValidCookies());
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