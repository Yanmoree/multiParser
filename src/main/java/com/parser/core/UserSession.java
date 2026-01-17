package com.parser.core;

import com.parser.config.ParserSettings;
import com.parser.model.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Класс, представляющий сессию парсера для пользователя
 */
public class UserSession {
    private static final Logger logger = LoggerFactory.getLogger(UserSession.class);

    private final long userId;
    private final List<String> queries;
    private final UserSettings settings;

    // Состояние
    private volatile boolean running;
    private volatile boolean paused;
    private String status;
    private String lastError;

    // Статистика
    private int totalProductsFound;
    private int requestsMade;
    private int errorsCount;

    // Временные метки
    private Date startTime;
    private Date endTime;
    private Date lastIterationTime;
    private Date lastProductFoundTime;

    // История найденных товаров (последние 100)
    private final List<Map<String, Object>> recentProducts;

    public UserSession(long userId, List<String> queries, UserSettings settings) {
        this.userId = userId;
        this.queries = new ArrayList<>(queries);
        this.settings = settings;
        this.running = false;
        this.paused = false;
        this.status = ParserSettings.STATUS_STOPPED;
        this.totalProductsFound = 0;
        this.requestsMade = 0;
        this.errorsCount = 0;
        this.recentProducts = new LinkedList<>();
    }

    // Геттеры
    public long getUserId() {
        return userId;
    }

    public List<String> getQueries() {
        return new ArrayList<>(queries);
    }

    public UserSettings getSettings() {
        return settings;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public int getTotalProductsFound() {
        return totalProductsFound;
    }

    public int getRequestsMade() {
        return requestsMade;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public Date getLastIterationTime() {
        return lastIterationTime;
    }

    public Date getLastProductFoundTime() {
        return lastProductFoundTime;
    }

    public List<Map<String, Object>> getRecentProducts() {
        return new ArrayList<>(recentProducts);
    }

    // Сеттеры
    public void setRunning(boolean running) {
        this.running = running;
        this.status = running ? ParserSettings.STATUS_RUNNING : ParserSettings.STATUS_STOPPED;
        if (running) {
            this.startTime = new Date();
        } else {
            this.endTime = new Date();
        }
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        this.status = paused ? ParserSettings.STATUS_PAUSED :
                (running ? ParserSettings.STATUS_RUNNING : ParserSettings.STATUS_STOPPED);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setLastIterationTime(Date lastIterationTime) {
        this.lastIterationTime = lastIterationTime;
    }

    public void setLastProductFoundTime(Date lastProductFoundTime) {
        this.lastProductFoundTime = lastProductFoundTime;
    }

    // Методы для работы со статистикой
    public void addProductsFound(int count) {
        this.totalProductsFound += count;
        this.lastProductFoundTime = new Date();
    }

    public void incrementRequestsMade() {
        this.requestsMade++;
    }

    public void incrementErrors() {
        this.errorsCount++;
    }

    public void addRecentProduct(Map<String, Object> productInfo) {
        synchronized (recentProducts) {
            recentProducts.add(0, productInfo); // Добавляем в начало

            // Ограничиваем размер истории
            if (recentProducts.size() > 100) {
                recentProducts.remove(recentProducts.size() - 1);
            }
        }
    }

    /**
     * Получение подробного статуса сессии
     */
    public Map<String, Object> getDetailedStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("userId", userId);
        status.put("running", running);
        status.put("paused", paused);
        status.put("status", this.status);
        status.put("queriesCount", queries.size());
        status.put("totalProductsFound", totalProductsFound);
        status.put("requestsMade", requestsMade);
        status.put("errorsCount", errorsCount);
        status.put("lastError", lastError);

        // Временные метки
        status.put("startTime", startTime != null ? startTime.getTime() : null);
        status.put("endTime", endTime != null ? endTime.getTime() : null);
        status.put("lastIterationTime", lastIterationTime != null ? lastIterationTime.getTime() : null);
        status.put("lastProductFoundTime", lastProductFoundTime != null ? lastProductFoundTime.getTime() : null);

        // Uptime
        if (startTime != null) {
            long uptime = System.currentTimeMillis() - startTime.getTime();
            status.put("uptime", formatUptime(uptime));
        }

        // Настройки
        if (settings != null) {
            Map<String, Object> settingsMap = new HashMap<>();
            settingsMap.put("checkInterval", settings.getCheckInterval());
            settingsMap.put("maxAgeMinutes", settings.getMaxAgeMinutes());
            settingsMap.put("maxPages", settings.getMaxPages());
            settingsMap.put("rowsPerPage", settings.getRowsPerPage());
            settingsMap.put("priceCurrency", settings.getPriceCurrency());
            settingsMap.put("notifyNewOnly", settings.isNotifyNewOnly());
            status.put("settings", settingsMap);
        }

        // Запросы (первые 5)
        status.put("sampleQueries", queries.size() > 5 ?
                queries.subList(0, 5) : queries);

        return status;
    }

    /**
     * Форматирование времени работы
     */
    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dд %dч %dм", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dч %dм %dс", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds % 60);
        } else {
            return String.format("%dс", seconds);
        }
    }

    /**
     * Получение статистики эффективности
     */
    public Map<String, Object> getEfficiencyStats() {
        Map<String, Object> stats = new HashMap<>();

        if (requestsMade > 0) {
            double productsPerRequest = (double) totalProductsFound / requestsMade;
            double errorRate = (double) errorsCount / requestsMade;

            stats.put("productsPerRequest", Math.round(productsPerRequest * 100.0) / 100.0);
            stats.put("errorRate", Math.round(errorRate * 10000.0) / 100.0); // в процентах
            stats.put("successRate", Math.round((1 - errorRate) * 10000.0) / 100.0);
        }

        if (startTime != null && lastIterationTime != null) {
            long avgIterationTime = (lastIterationTime.getTime() - startTime.getTime()) /
                    Math.max(requestsMade, 1);
            stats.put("avgIterationTimeMs", avgIterationTime);
        }

        return stats;
    }

    /**
     * Сброс статистика
     */
    public void resetStatistics() {
        totalProductsFound = 0;
        requestsMade = 0;
        errorsCount = 0;
        recentProducts.clear();
        lastError = null;
        logger.info("Statistics reset for user {}", userId);
    }

    /**
     * Обновление списка запросов
     */
    public boolean updateQueries(List<String> newQueries) {
        if (newQueries == null || newQueries.isEmpty()) {
            logger.warn("Attempted to set empty queries for user {}", userId);
            return false;
        }

        this.queries.clear();
        this.queries.addAll(newQueries);
        logger.debug("Queries updated for user {}, now has {} queries",
                userId, queries.size());
        return true;
    }

    /**
     * Проверка, активна ли сессия
     */
    public boolean isActive() {
        return running && !paused;
    }

    @Override
    public String toString() {
        return String.format("UserSession{userId=%d, running=%s, queries=%d, found=%d}",
                userId, running, queries.size(), totalProductsFound);
    }
}