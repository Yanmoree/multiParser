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

import java.util.*;
import java.util.concurrent.*;

public class ThreadManager {
    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService scheduler;

    private int totalProductsFound = 0;
    private int totalRequestsMade = 0;
    private final Date startTime = new Date();

    // Список User-Agent для ротации
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1"
    };

    public ThreadManager() {
        int coreSize = Config.getThreadPoolCoreSize();
        int maxSize = Config.getThreadPoolMaxSize();
        int keepAlive = Config.getInt("thread.pool.keepalive.seconds", 60);

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(50);
        threadPool = new ThreadPoolExecutor(coreSize, maxSize, keepAlive, TimeUnit.SECONDS,
                workQueue, new ThreadPoolExecutor.CallerRunsPolicy());

        scheduler = Executors.newScheduledThreadPool(1);
    }

    public boolean startUserParser(long userId) {
        if (!WhitelistManager.isUserAllowed(userId)) {
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

        TelegramNotificationService.sendMessage(userId,
                "✅ Parser started!\nQueries: " + queries.size() + "\nCheck interval: " + settings.getCheckInterval() + " sec");

        return true;
    }

    private void runUserParser(UserSession session) {
        long userId = session.getUserId();
        session.setRunning(true);

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
                            sendProductNotifications(userId, products, query, session.getSettings());
                            UserDataManager.addUserProducts(userId, products);
                        }

                        Thread.sleep(Config.getInt("api.goofish.delay.between.requests", 2000));

                    } catch (Exception e) {
                        session.incrementErrors();
                        session.setLastError("Search error: " + e.getMessage());
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
        } catch (Exception e) {
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
            return;
        }

        List<Product> productsToSend = new ArrayList<>();
        for (String productId : newProductIds) {
            Product p = productMap.get(productId);
            if (p != null) {
                productsToSend.add(p);
            }
        }

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
                // Не логируем ошибки отправки
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
            sendProductAsText(userId, p, number, total);
        }
    }

    private void sendProductAsText(long userId, Product p, int number, int total) {
        try {
            String message = formatProductCaption(p, number, total);
            TelegramNotificationService.sendHtmlMessage(userId, message);
        } catch (Exception e) {
            // Не логируем ошибки
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
            return true;
        }
        return false;
    }

    public boolean pauseUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.isRunning()) {
            session.setPaused(true);
            return true;
        }
        return false;
    }

    public boolean resumeUserParser(long userId) {
        UserSession session = userSessions.get(userId);
        if (session != null && session.isPaused()) {
            session.setPaused(false);
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
        return stats;
    }

    public List<Long> getActiveUsers() {
        return new ArrayList<>(userSessions.keySet());
    }

    public boolean isUserParserRunning(long userId) {
        UserSession session = userSessions.get(userId);
        return session != null && session.isRunning();
    }

    public void shutdown() {
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
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}