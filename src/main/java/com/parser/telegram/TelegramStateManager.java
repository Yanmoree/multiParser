package com.parser.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер состояний для хранения промежуточных данных пользователей
 */
public class TelegramStateManager {
    private static final Logger logger = LoggerFactory.getLogger(TelegramStateManager.class);

    // Хранилище состояний пользователей
    private final Map<Integer, UserState> userStates = new ConcurrentHashMap<>();

    /**
     * Внутренний класс для хранения состояния пользователя
     */
    private static class UserState {
        private String state;
        private Map<String, String> data;
        private long lastActivity;

        public UserState() {
            this.state = null;
            this.data = new ConcurrentHashMap<>();
            this.lastActivity = System.currentTimeMillis();
        }

        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - lastActivity > timeoutMs;
        }
    }

    // Таймаут состояния (30 минут)
    private static final long STATE_TIMEOUT = 30 * 60 * 1000;

    /**
     * Установка состояния пользователя
     */
    public void setUserState(int userId, String state) {
        UserState userState = userStates.computeIfAbsent(userId, k -> new UserState());
        userState.state = state;
        userState.updateActivity();
        logger.debug("State set for user {}: {}", userId, state);
    }

    /**
     * Получение состояния пользователя
     */
    public String getUserState(int userId) {
        UserState userState = userStates.get(userId);
        if (userState != null) {
            if (userState.isExpired(STATE_TIMEOUT)) {
                userStates.remove(userId);
                logger.debug("State expired for user {}", userId);
                return null;
            }
            userState.updateActivity();
            return userState.state;
        }
        return null;
    }

    /**
     * Очистка состояния пользователя
     */
    public void clearUserState(int userId) {
        userStates.remove(userId);
        logger.debug("State cleared for user {}", userId);
    }

    /**
     * Установка данных пользователя
     */
    public void setUserData(int userId, String key, String value) {
        UserState userState = userStates.computeIfAbsent(userId, k -> new UserState());
        userState.data.put(key, value);
        userState.updateActivity();
        logger.debug("Data set for user {}: {} = {}", userId, key, value);
    }

    /**
     * Получение данных пользователя
     */
    public String getUserData(int userId, String key) {
        UserState userState = userStates.get(userId);
        if (userState != null) {
            if (userState.isExpired(STATE_TIMEOUT)) {
                userStates.remove(userId);
                return null;
            }
            userState.updateActivity();
            return userState.data.get(key);
        }
        return null;
    }

    /**
     * Очистка всех данных пользователя
     */
    public void clearUserData(int userId) {
        UserState userState = userStates.get(userId);
        if (userState != null) {
            userState.data.clear();
            userState.state = null;
            userState.updateActivity();
            logger.debug("All data cleared for user {}", userId);
        }
    }

    /**
     * Получение всех данных пользователя
     */
    public Map<String, String> getAllUserData(int userId) {
        UserState userState = userStates.get(userId);
        if (userState != null) {
            if (userState.isExpired(STATE_TIMEOUT)) {
                userStates.remove(userId);
                return Map.of();
            }
            userState.updateActivity();
            return new ConcurrentHashMap<>(userState.data);
        }
        return Map.of();
    }

    /**
     * Проверка наличия состояния у пользователя
     */
    public boolean hasUserState(int userId) {
        return getUserState(userId) != null;
    }

    /**
     * Очистка устаревших состояний
     */
    public void cleanupExpiredStates() {
        int initialSize = userStates.size();
        userStates.entrySet().removeIf(entry -> entry.getValue().isExpired(STATE_TIMEOUT));
        int removed = initialSize - userStates.size();

        if (removed > 0) {
            logger.info("Cleaned up {} expired user states", removed);
        }
    }

    /**
     * Получение статистики менеджера состояний
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalUsers", userStates.size());
        stats.put("timeoutMs", STATE_TIMEOUT);

        // Подсчет состояний по типам
        Map<String, Integer> stateCounts = new java.util.HashMap<>();
        for (UserState state : userStates.values()) {
            if (state.state != null) {
                stateCounts.put(state.state, stateCounts.getOrDefault(state.state, 0) + 1);
            }
        }
        stats.put("stateCounts", stateCounts);

        return stats;
    }

    /**
     * Сброс всех состояний (для тестирования)
     */
    public void reset() {
        int count = userStates.size();
        userStates.clear();
        logger.info("Reset all user states ({} cleared)", count);
    }
}