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
    private static final Set<Integer> whitelist = loadWhitelist();

    /**
     * Загрузка белого списка из файла
     */
    private static Set<Integer> loadWhitelist() {
        Set<Integer> set = new HashSet<>();
        List<String> lines = FileStorage.readLines(WHITELIST_FILE);

        for (String line : lines) {
            try {
                int userId = Integer.parseInt(line.trim());
                if (userId > 0) {
                    set.add(userId);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid user ID in whitelist: {}", line);
            }
        }

        logger.info("Loaded {} users from whitelist", set.size());
        return Collections.synchronizedSet(set);
    }

    /**
     * Сохранение белого списка в файл
     */
    private static void saveWhitelist() {
        List<String> lines = new ArrayList<>();
        for (Integer userId : whitelist) {
            lines.add(userId.toString());
        }
        FileStorage.writeLines(WHITELIST_FILE, lines);
        logger.debug("Whitelist saved with {} users", whitelist.size());
    }

    /**
     * Проверка, разрешен ли пользователь
     */
    public static boolean isUserAllowed(int userId) {
        return whitelist.contains(userId);
    }

    /**
     * Добавление пользователя в белый список
     */
    public static boolean addUser(int userId) {
        if (userId <= 0) {
            logger.warn("Attempted to add invalid user ID: {}", userId);
            return false;
        }

        if (whitelist.add(userId)) {
            saveWhitelist();
            logger.info("User {} added to whitelist", userId);
            return true;
        }

        logger.debug("User {} already in whitelist", userId);
        return false;
    }

    /**
     * Удаление пользователя из белого списка
     */
    public static boolean removeUser(int userId) {
        if (whitelist.remove(userId)) {
            saveWhitelist();
            logger.info("User {} removed from whitelist", userId);
            return true;
        }

        logger.debug("User {} not found in whitelist", userId);
        return false;
    }

    /**
     * Получение всех пользователей из белого списка
     */
    public static List<Integer> getAllUsers() {
        return new ArrayList<>(whitelist);
    }

    /**
     * Получение количества пользователей в белом списке
     */
    public static int getUserCount() {
        return whitelist.size();
    }

    /**
     * Проверка существования пользователя
     */
    public static boolean userExists(int userId) {
        return whitelist.contains(userId);
    }

    /**
     * Очистка белого списка
     */
    public static void clearWhitelist() {
        int count = whitelist.size();
        whitelist.clear();
        saveWhitelist();
        logger.info("Whitelist cleared ({} users removed)", count);
    }

    /**
     * Добавление нескольких пользователей
     */
    public static int addUsers(List<Integer> userIds) {
        int added = 0;
        for (Integer userId : userIds) {
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
        return stats;
    }

    /**
     * Поиск пользователей по шаблону (по ID)
     */
    public static List<Integer> searchUsers(String pattern) {
        List<Integer> result = new ArrayList<>();
        String searchPattern = pattern.toLowerCase();

        for (Integer userId : whitelist) {
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

            for (Integer userId : whitelist) {
                lines.add(userId.toString());
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
                    int userId = Integer.parseInt(line.trim());
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
}