package com.parser.core;

import com.parser.config.Config;
import com.parser.config.ParserSettings;
import com.parser.model.Product;
import com.parser.model.UserSettings;
import com.parser.parser.ParserFactory;
import com.parser.parser.SiteParser;
import com.parser.storage.UserDataManager;
import com.parser.storage.WhitelistManager;
import com.parser.telegram.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static com.parser.config.ParserSettings.CURRENCY_RUBLES;

/**
 * Менеджер потоков для управления парсерами пользователей
 */
public class ThreadManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadManager.class);

    private final Map<Integer, UserSession> userSessions = new ConcurrentHashMap<>();
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

        logger.info("ThreadManager initialized. Pool: {}-{} threads, queue: {}",
                corePoolSize, maxPoolSize, queueCapacity);
    }

    /**
     * Запуск парсера для пользователя
     */
    public boolean startUserParser(int userId) {
        logger.info("Attempting to start parser for user {}", userId);

        // Проверка на существующую сессию
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

        // Проверка вайтлиста
        if (!WhitelistManager.isUserAllowed(userId)) {
            logger.warn("User {} not in whitelist", userId);
            TelegramNotificationService.sendMessage(userId,
                    "⛔ Вы не авторизованы для использования парсера.\n" +
                            "Используйте команду /start для регистрации");
            return false;
        }

        // Получение запросов пользователя
        List<String> queries = UserDataManager.getUserQueries(userId);
        if (queries.isEmpty()) {
            logger.warn("User {} has no queries", userId);
            TelegramNotificationService.sendMessage(userId,
                    "📭 У вас нет поисковых запросов.\n" +
                            "Добавьте запросы командой /addquery [текст]");
            return false;
        }

        // Получение настроек пользователя
        UserSettings settings = UserDataManager.getUserSettings(userId);

        // Создание сессии пользователя
        UserSession session = new UserSession(userId, queries, settings);
        userSessions.put(userId, session);

        // Запуск парсера в отдельном потоке
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
                "✅ Парсер успешно запущен!\n" +
                        "Запросов: " + queries.size() + "\n" +
                        "Интервал проверки: " + settings.getCheckInterval() + " сек\n" +
                        "Для остановки используйте /stop_parser");

        return true;
    }

    /**
     * Основной цикл работы парсера для пользователя
     */
    private void runUserParser(UserSession session) {
        final int userId = session.getUserId();
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

                            // Отправка уведомлений о найденных товарах
                            if (shouldSendNotification(session, products)) {
                                sendProductNotifications(userId, products, query, session.getSettings());
                            }

                            // Сохранение товаров
                            UserDataManager.saveUserProducts(userId, products);
                        }

                        // Задержка между запросами
                        Thread.sleep(Config.getInt("api.goofish.delay.between.requests", 2000));

                    } catch (Exception e) {
                        logger.error("Error searching query '{}' for user {}: {}",
                                query, userId, e.getMessage(), e);
                        session.incrementErrors();

                        // Задержка при ошибке
                        Thread.sleep(5000);
                    }
                }

                // Логирование результатов итерации
                if (productsFoundInIteration > 0) {
                    logger.info("Iteration completed for user {}: found {} products",
                            userId, productsFoundInIteration);
                }

                // Ожидание до следующей проверки
                int checkInterval = session.getSettings().getCheckInterval();
                logger.debug("Waiting {} seconds for next check (user {})",
                        checkInterval, userId);

                // Проверка каждую секунду на возможность прерывания
                for (int i = 0; i < checkInterval && session.isRunning(); i++) {
                    Thread.sleep(1000);
                }

                // Обновление времени выполнения
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
     * Проверка необходимости отправки уведомления
     */
    private boolean shouldSendNotification(UserSession session, List<Product> products) {
        UserSettings settings = session.getSettings();

        if (products.isEmpty()) {
            return false;
        }

        // Если включены уведомления только о новых товарах
        if (settings.isNotifyNewOnly()) {
            // Проверяем, есть ли новые товары
            List<Product> newProducts = UserDataManager.filterNewProducts(
                    session.getUserId(), products);
            return !newProducts.isEmpty();
        }

        return true;
    }

    /**
     * Отправка уведомлений о товарах
     */
    private void sendProductNotifications(int userId, List<Product> products,
                                          String query, UserSettings settings) {
        if (products.isEmpty()) return;

        // Фильтрация новых товаров, если необходимо
        List<Product> productsToNotify = settings.isNotifyNewOnly() ?
                UserDataManager.filterNewProducts(userId, products) : products;

        if (productsToNotify.isEmpty()) return;

        // Создание сообщения
        StringBuilder message = new StringBuilder();
        message.append("🎯 Найдены товары по запросу \"").append(query).append("\"\n\n");

        for (int i = 0; i < Math.min(productsToNotify.size(), 5); i++) {
            Product p = productsToNotify.get(i);
            message.append(i + 1).append(". ").append(p.getTitle()).append("\n");

            if (CURRENCY_RUBLES.equals(settings.getPriceCurrency())) {
                message.append("💰 Цена: ").append(p.getPriceDisplayRub()).append("\n");
            } else {
                message.append("💰 Цена: ").append(p.getPriceDisplay()).append("\n");
            }

            message.append("📍 Место: ").append(p.getLocation()).append("\n");
            message.append("⏳ Возраст: ").append(p.getAgeMinutes()).append(" мин\n");
            message.append("🔗 Ссылка: ").append(p.getUrl()).append("\n\n");
        }

        if (productsToNotify.size() > 5) {
            message.append("... и еще ").append(productsToNotify.size() - 5)
                    .append(" товаров\n");
        }

        message.append("\nДля управления парсером используйте команды:\n");
        message.append("/status - статус\n");
        message.append("/settings - настройки\n");
        message.append("/stop_parser - остановить\n");

        // Отправка уведомления
        TelegramNotificationService.sendMessage(userId, message.toString());

        // Отправка изображений (первые 3 товара)
        for (int i = 0; i < Math.min(productsToNotify.size(), 3); i++) {
            Product p = productsToNotify.get(i);
            if (!p.getImages().isEmpty()) {
                TelegramNotificationService.sendPhoto(userId,
                        p.getImages().get(0),
                        "📸 " + p.getTitle());
            }
        }
    }

    /**
     * Остановка парсера для пользователя
     */
    public boolean stopUserParser(int userId) {
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
    public boolean pauseUserParser(int userId) {
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
    public boolean resumeUserParser(int userId) {
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
    public Map<Integer, Map<String, Object>> getAllStatuses() {
        Map<Integer, Map<String, Object>> statuses = new HashMap<>();

        for (Map.Entry<Integer, UserSession> entry : userSessions.entrySet()) {
            statuses.put(entry.getKey(), entry.getValue().getDetailedStatus());
        }

        return statuses;
    }

    /**
     * Получение статуса конкретного пользователя
     */
    public Map<String, Object> getUserStatus(int userId) {
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

        return stats;
    }

    /**
     * Логирование статистики
     */
    private void logStatistics() {
        if (logger.isInfoEnabled()) {
            Map<String, Object> stats = getGlobalStatistics();
            logger.info("Statistics: {} active users, {} total products found, {} active threads",
                    stats.get("totalUsers"), stats.get("totalProductsFound"),
                    stats.get("activeThreads"));
        }
    }

    /**
     * Корректное завершение работы менеджера
     */
    public void shutdown() {
        logger.info("Shutting down ThreadManager...");

        // Остановка всех парсеров
        List<Integer> userIds = new ArrayList<>(userSessions.keySet());
        for (Integer userId : userIds) {
            stopUserParser(userId);
        }

        // Завершение пула потоков
        threadPool.shutdown();
        scheduler.shutdown();

        try {
            // Ожидание завершения текущих задач
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
    public boolean isUserParserRunning(int userId) {
        UserSession session = userSessions.get(userId);
        return session != null && session.isRunning();
    }

    /**
     * Получение списка активных пользователей
     */
    public List<Integer> getActiveUsers() {
        return new ArrayList<>(userSessions.keySet());
    }
}