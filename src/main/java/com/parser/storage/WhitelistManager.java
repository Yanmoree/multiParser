package com.parser.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер белого списка пользователей - ОПТИМИЗИРОВАННЫЙ
 */
public class WhitelistManager {
    private static final Logger logger = LoggerFactory.getLogger(WhitelistManager.class);
    /**
     * ВАЖНО:
     * FileStorage сам добавляет префикс storage.data.dir (по умолчанию ./data).
     * Поэтому здесь НЕ должно быть "data/..." иначе получится ./data/data/...
     *
     * Для обратной совместимости поддерживаем старый путь.
     */
    private static final String WHITELIST_FILE = "whitelist.txt";
    private static final String LEGACY_WHITELIST_FILE = "data/whitelist.txt";
    private static final Set<Long> whitelist = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean isInitialized = false;

    static {
        synchronized (WhitelistManager.class) {
            if (!isInitialized) {
                loadWhitelist();
                isInitialized = true;
            }
        }
    }

    /**
     * Загрузка белого списка из файла
     */
    private static void loadWhitelist() {
        List<String> lines = FileStorage.readLines(WHITELIST_FILE);
        boolean loadedFromLegacy = false;

        // Если нового файла нет/пустой, но есть legacy — читаем legacy
        if ((lines == null || lines.isEmpty()) && FileStorage.fileExists(LEGACY_WHITELIST_FILE)) {
            lines = FileStorage.readLines(LEGACY_WHITELIST_FILE);
            loadedFromLegacy = true;
        }

        logger.info("Loading whitelist from {}. Found {} lines",
                loadedFromLegacy ? LEGACY_WHITELIST_FILE : WHITELIST_FILE,
                lines != null ? lines.size() : 0);

        for (String line : lines) {
            try {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                long userId = Long.parseLong(line);
                if (userId > 0) {
                    whitelist.add(userId);
                } else {
                    logger.warn("Invalid user ID in whitelist (must be > 0): {}", userId);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid user ID format in whitelist: '{}'. Error: {}", line, e.getMessage());
            }
        }

        logger.info("✅ Loaded {} users from whitelist", whitelist.size());

        // Мягкая миграция: если загрузили из legacy и новый файл отсутствует — сохраняем в новый путь
        if (loadedFromLegacy && !FileStorage.fileExists(WHITELIST_FILE) && !whitelist.isEmpty()) {
            logger.info("Migrating whitelist from legacy path to {}", WHITELIST_FILE);
            saveWhitelist();
        }
    }

    /**
     * Сохранение белого списка в файл
     */
    private static void saveWhitelist() {
        List<String> lines = new ArrayList<>();
        lines.add("# Whitelist - authorized users");
        lines.add("# Format: one user ID per line");
        lines.add("# Created: " + new Date());
        lines.add("");

        List<Long> sortedUsers = new ArrayList<>(whitelist);
        Collections.sort(sortedUsers);

        for (Long userId : sortedUsers) {
            lines.add(String.valueOf(userId));
        }

        try {
            FileStorage.writeLines(WHITELIST_FILE, lines);
            logger.info("✅ Whitelist saved: {} users", whitelist.size());
        } catch (Exception e) {
            logger.error("❌ Failed to save whitelist: {}", e.getMessage());
        }
    }

    /**
     * Проверка авторизации пользователя
     */
    public static boolean isUserAllowed(long userId) {
        boolean allowed = whitelist.contains(userId);
        if (!allowed) {
            logger.warn("Unauthorized access attempt from user {}", userId);
        }
        return allowed;
    }

    /**
     * Добавление пользователя
     */
    public static boolean addUser(long userId) {
        if (userId <= 0) {
            logger.warn("Invalid user ID: {}", userId);
            return false;
        }

        if (whitelist.add(userId)) {
            saveWhitelist();
            logger.info("✅ User {} added to whitelist", userId);
            return true;
        }

        return false;
    }

    /**
     * Удаление пользователя
     */
    public static boolean removeUser(long userId) {
        if (whitelist.remove(userId)) {
            saveWhitelist();
            logger.info("✅ User {} removed from whitelist", userId);
            return true;
        }
        return false;
    }

    /**
     * Получение всех пользователей
     */
    public static List<Long> getAllUsers() {
        return new ArrayList<>(whitelist);
    }

    /**
     * Количество пользователей
     */
    public static long getUserCount() {
        return whitelist.size();
    }

    /**
     * Проверка существования пользователя
     */
    public static boolean userExists(long userId) {
        return whitelist.contains(userId);
    }

    /**
     * Переинициализация (для тестов)
     */
    public static void reload() {
        whitelist.clear();
        loadWhitelist();
        logger.info("Whitelist reloaded");
    }
}