package com.parser.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Менеджер белого списка пользователей
 */
public class WhitelistManager {
    private static final Logger logger = LoggerFactory.getLogger(WhitelistManager.class);
    private static final String WHITELIST_FILE = "whitelist.txt";
    private static final Set<Long> whitelist = loadWhitelist();

    /**
     * Загрузка белого списка из файла
     */
    private static Set<Long> loadWhitelist() {
        Set<Long> set = new HashSet<>();
        List<String> lines = FileStorage.readLines(WHITELIST_FILE);

        logger.info("Loading whitelist from file. Found {} lines", lines.size());

        for (String line : lines) {
            try {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Пропускаем пустые строки и комментарии
                }

                long userId = Long.parseLong(line);
                if (userId > 0) {
                    set.add(userId);
                    logger.debug("Added user {} to whitelist from file", userId);
                } else {
                    logger.warn("Invalid user ID in whitelist (must be > 0): {}", userId);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid user ID format in whitelist: '{}'. Error: {}", line, e.getMessage());
            }
        }

        logger.info("Loaded {} users from whitelist file", set.size());
        return Collections.synchronizedSet(set);
    }

    /**
     * Сохранение белого списка в файл
     */
    private static void saveWhitelist() {
        List<String> lines = new ArrayList<>();

        // Добавляем заголовок
        lines.add("# Whitelist - список авторизованных пользователей");
        lines.add("# Формат: один ID пользователя на строке");
        lines.add("# Создан: " + new Date());
        lines.add("");

        // Сортируем ID для удобства чтения
        List<Long> sortedUsers = new ArrayList<>(whitelist);
        Collections.sort(sortedUsers);

        for (Long userId : sortedUsers) {
            lines.add(String.valueOf(userId));
        }

        String filePath = FileStorage.getFilePath(WHITELIST_FILE);
        logger.info("Saving whitelist with {} users to: {}", whitelist.size(), filePath);

        try {
            FileStorage.writeLines(WHITELIST_FILE, lines);
            logger.info("✅ Whitelist saved successfully with {} users", whitelist.size());
        } catch (Exception e) {
            logger.error("❌ Failed to save whitelist: {}", e.getMessage(), e);
        }
    }

    /**
     * Проверка, разрешен ли пользователь
     */
    public static boolean isUserAllowed(long userId) { // long вместо int
        boolean allowed = whitelist.contains(userId);
        logger.debug("Checking whitelist for user {}: {}", userId, allowed);
        return allowed;
    }


    /**
     * Добавление пользователя в белый список
     */
    public static boolean addUser(long userId) { // long вместо int
        if (userId <= 0) {
            logger.warn("Attempted to add invalid user ID: {}", userId);
            return false;
        }

        logger.info("Adding user {} to whitelist. Current whitelist size: {}", userId, whitelist.size());

        if (whitelist.add(userId)) {
            try {
                saveWhitelist();
                logger.info("✅ User {} successfully added to whitelist", userId);
                logger.info("📊 Whitelist now contains {} users", whitelist.size());
                return true;
            } catch (Exception e) {
                logger.error("❌ Failed to save whitelist after adding user {}: {}", userId, e.getMessage());
                whitelist.remove(userId);
                return false;
            }
        }

        logger.debug("User {} already exists in whitelist", userId);
        return false;
    }

    /**
     * Удаление пользователя из белого списка
     */
    public static boolean removeUser(long userId) {
        logger.info("Removing user {} from whitelist", userId);

        if (whitelist.remove(userId)) {
            try {
                saveWhitelist();
                logger.info("✅ User {} removed from whitelist", userId);
                return true;
            } catch (Exception e) {
                logger.error("❌ Failed to save whitelist after removing user {}: {}", userId, e.getMessage());
                return false;
            }
        }

        logger.debug("User {} not found in whitelist", userId);
        return false;
    }

    /**
     * Получение всех пользователей из белого списка
     */
    public static List<Long> getAllUsers() { // Изменяем на List<Long>
        return new ArrayList<>(whitelist);
    }

    /**
     * Получение количества пользователей в белом списке
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
     * Очистка белого списка
     */
    public static void clearWhitelist() {
        long count = whitelist.size();
        whitelist.clear();
        saveWhitelist();
        logger.info("Whitelist cleared ({} users removed)", count);
    }

    /**
     * Добавление нескольких пользователей
     */
    public static long addUsers(List<Long> userIds) {
        long added = 0;
        for (Long userId : userIds) {
            if (userId != null && userId > 0 && whitelist.add(userId)) {
                added++;
            }
        }

        if (added > 0) {
            saveWhitelist();
            logger.info("Added {} users to whitelist", added);
        }

        return added;
    }

    /**
     * Получение статистики белого списка
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", whitelist.size());
        stats.put("filePath", FileStorage.getFilePath(WHITELIST_FILE));
        stats.put("fileExists", FileStorage.fileExists(WHITELIST_FILE));
        stats.put("fileSize", FileStorage.getFileSize(WHITELIST_FILE));
        stats.put("lastModified", FileStorage.getLastModified(WHITELIST_FILE));
        stats.put("users", new ArrayList<>(whitelist));
        return stats;
    }

    /**
     * Поиск пользователей по шаблону (по ID)
     */
    public static List<Long> searchUsers(String pattern) {
        List<Long> result = new ArrayList<>();
        String searchPattern = pattern.toLowerCase();

        for (Long userId : whitelist) {
            if (String.valueOf(userId).contains(searchPattern)) {
                result.add(userId);
            }
        }

        return result;
    }

    /**
     * Экспорт белого списка в текстовый файл
     */
    public static boolean exportWhitelist(String exportFilename) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# Whitelist export - " + new Date());
            lines.add("# Total users: " + whitelist.size());
            lines.add("");

            // Сортируем для удобства
            List<Long> sortedUsers = new ArrayList<>(whitelist);
            Collections.sort(sortedUsers);

            for (Long userId : sortedUsers) {
                lines.add(String.valueOf(userId));
            }

            FileStorage.writeLines(exportFilename, lines);
            logger.info("Whitelist exported to {}", exportFilename);
            return true;

        } catch (Exception e) {
            logger.error("Error exporting whitelist: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Импорт белого списка из файла
     */
    public static int importWhitelist(String importFilename) {
        try {
            List<String> lines = FileStorage.readLines(importFilename);
            int imported = 0;

            for (String line : lines) {
                try {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    long userId = Long.parseLong(line);
                    if (userId > 0 && whitelist.add(userId)) {
                        imported++;
                    }
                } catch (NumberFormatException e) {
                    // Пропускаем некорректные строки
                }
            }

            if (imported > 0) {
                saveWhitelist();
                logger.info("Imported {} users from {}", imported, importFilename);
            }

            return imported;

        } catch (Exception e) {
            logger.error("Error importing whitelist: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Перезагрузка whitelist из файла (для отладки)
     */
    public static void reload() {
        logger.info("Reloading whitelist from file...");
        Set<Long> newWhitelist = loadWhitelist();
        whitelist.clear();
        whitelist.addAll(newWhitelist);
        logger.info("Whitelist reloaded. Now contains {} users", whitelist.size());
    }
}