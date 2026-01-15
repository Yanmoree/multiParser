package com.parser.storage;

import com.parser.model.Product;
import com.parser.model.UserSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Менеджер для работы с данными пользователей
 */
public class UserDataManager {
    private static final Logger logger = LoggerFactory.getLogger(UserDataManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Получение поисковых запросов пользователя
     */
    public static List<String> getUserQueries(int userId) {
        String filename = "user_" + userId + "_queries.txt";
        return FileStorage.readLines(filename);
    }

    /**
     * Сохранение поисковых запросов пользователя
     */
    public static void saveUserQueries(int userId, List<String> queries) {
        String filename = "user_" + userId + "_queries.txt";
        FileStorage.writeLines(filename, queries);
        logger.debug("Saved {} queries for user {}", queries.size(), userId);
    }

    /**
     * Добавление поискового запроса
     */
    public static boolean addUserQuery(int userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Attempted to add empty query for user {}", userId);
            return false;
        }

        String trimmedQuery = query.trim();
        List<String> queries = getUserQueries(userId);

        // Проверка на дубликаты
        for (String existingQuery : queries) {
            if (existingQuery.equalsIgnoreCase(trimmedQuery)) {
                logger.debug("Query already exists for user {}: {}", userId, trimmedQuery);
                return false;
            }
        }

        // Ограничение на количество запросов
        if (queries.size() >= 50) {
            logger.warn("User {} reached query limit (50)", userId);
            return false;
        }

        queries.add(trimmedQuery);
        saveUserQueries(userId, queries);
        logger.info("Query added for user {}: {}", userId, trimmedQuery);
        return true;
    }

    /**
     * Удаление поискового запроса
     */
    public static boolean removeUserQuery(int userId, String query) {
        List<String> queries = getUserQueries(userId);
        boolean removed = queries.remove(query);

        if (removed) {
            saveUserQueries(userId, queries);
            logger.info("Query removed for user {}: {}", userId, query);
        } else {
            logger.debug("Query not found for user {}: {}", userId, query);
        }

        return removed;
    }

    /**
     * Очистка всех поисковых запросов пользователя
     */
    public static void clearUserQueries(int userId) {
        saveUserQueries(userId, new ArrayList<>());
        logger.info("All queries cleared for user {}", userId);
    }

    /**
     * Получение настроек пользователя
     */
    public static UserSettings getUserSettings(int userId) {
        String filename = "user_settings/" + userId + ".json";
        String json = FileStorage.readJson(filename);

        try {
            if (json == null || json.isEmpty() || json.equals("{}")) {
                // Возвращаем настройки по умолчанию
                UserSettings defaultSettings = new UserSettings();
                saveUserSettings(userId, defaultSettings);
                return defaultSettings;
            }

            return objectMapper.readValue(json, UserSettings.class);

        } catch (Exception e) {
            logger.error("Error parsing settings for user {}: {}", userId, e.getMessage());
            return new UserSettings(); // Настройки по умолчанию при ошибке
        }
    }

    /**
     * Сохранение настроек пользователя
     */
    public static void saveUserSettings(int userId, UserSettings settings) {
        String filename = "user_settings/" + userId + ".json";

        try {
            String json = objectMapper.writeValueAsString(settings);
            FileStorage.writeJson(filename, json);
            logger.debug("Settings saved for user {}", userId);

        } catch (Exception e) {
            logger.error("Error saving settings for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Получение сохраненных товаров пользователя
     */
    public static List<Product> getUserProducts(int userId) {
        String filename = "user_products/" + userId + ".json";
        String json = FileStorage.readJson(filename);

        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new ArrayList<>();
        }

        try {
            Product[] products = objectMapper.readValue(json, Product[].class);
            return new ArrayList<>(Arrays.asList(products));

        } catch (Exception e) {
            logger.error("Error parsing products for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Сохранение товаров пользователя
     */
    public static void saveUserProducts(int userId, List<Product> products) {
        String filename = "user_products/" + userId + ".json";

        try {
            String json = objectMapper.writeValueAsString(products);
            FileStorage.writeJson(filename, json);
            logger.debug("Saved {} products for user {}", products.size(), userId);

        } catch (Exception e) {
            logger.error("Error saving products for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Добавление товаров к существующим
     */
    public static void addUserProducts(int userId, List<Product> newProducts) {
        if (newProducts == null || newProducts.isEmpty()) {
            return;
        }

        List<Product> existingProducts = getUserProducts(userId);
        Set<String> existingIds = new HashSet<>();

        // Собираем ID существующих товаров
        for (Product product : existingProducts) {
            existingIds.add(product.getId());
        }

        // Добавляем только новые товары
        for (Product product : newProducts) {
            if (!existingIds.contains(product.getId())) {
                existingProducts.add(product);
                existingIds.add(product.getId());
            }
        }

        // Ограничиваем количество хранимых товаров
        if (existingProducts.size() > 1000) {
            existingProducts = existingProducts.subList(
                    existingProducts.size() - 1000, existingProducts.size());
        }

        saveUserProducts(userId, existingProducts);
    }

    /**
     * Очистка товаров пользователя
     */
    public static void clearUserProducts(int userId) {
        String filename = "user_products/" + userId + ".json";
        FileStorage.writeJson(filename, "[]");
        logger.info("Products cleared for user {}", userId);
    }

    /**
     * Фильтрация новых товаров
     */
    public static List<Product> filterNewProducts(int userId, List<Product> products) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }

        List<Product> existingProducts = getUserProducts(userId);
        Set<String> existingIds = new HashSet<>();

        for (Product product : existingProducts) {
            existingIds.add(product.getId());
        }

        List<Product> newProducts = new ArrayList<>();
        for (Product product : products) {
            if (!existingIds.contains(product.getId())) {
                newProducts.add(product);
            }
        }

        return newProducts;
    }

    /**
     * Получение статистики пользователя
     */
    public static Map<String, Object> getUserStats(int userId) {
        Map<String, Object> stats = new HashMap<>();

        // Количество запросов
        List<String> queries = getUserQueries(userId);
        stats.put("queryCount", queries.size());

        // Количество товаров
        List<Product> products = getUserProducts(userId);
        stats.put("productCount", products.size());

        // Настройки
        UserSettings settings = getUserSettings(userId);
        stats.put("settings", settings.getSummary());

        // Дата последнего изменения
        String queriesFile = "user_" + userId + "_queries.txt";
        String productsFile = "user_products/" + userId + ".json";
        String settingsFile = "user_settings/" + userId + ".json";

        stats.put("queriesLastModified", FileStorage.getLastModified(queriesFile));
        stats.put("productsLastModified", FileStorage.getLastModified(productsFile));
        stats.put("settingsLastModified", FileStorage.getLastModified(settingsFile));

        return stats;
    }

    /**
     * Удаление всех данных пользователя
     */
    public static boolean deleteUserData(int userId) {
        try {
            String queriesFile = "user_" + userId + "_queries.txt";
            String productsFile = "user_products/" + userId + ".json";
            String settingsFile = "user_settings/" + userId + ".json";

            boolean deleted = true;
            deleted &= FileStorage.deleteFile(queriesFile);
            deleted &= FileStorage.deleteFile(productsFile);
            deleted &= FileStorage.deleteFile(settingsFile);

            if (deleted) {
                logger.info("All data deleted for user {}", userId);
            } else {
                logger.warn("Some files could not be deleted for user {}", userId);
            }

            return deleted;

        } catch (Exception e) {
            logger.error("Error deleting data for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Экспорт данных пользователя
     */
    public static boolean exportUserData(int userId, String exportFilename) {
        try {
            Map<String, Object> exportData = new HashMap<>();

            exportData.put("userId", userId);
            exportData.put("exportDate", new Date());

            // Запросы
            exportData.put("queries", getUserQueries(userId));

            // Настройки
            UserSettings settings = getUserSettings(userId);
            exportData.put("settings", objectMapper.convertValue(settings, Map.class));

            // Товары (только последние 100)
            List<Product> products = getUserProducts(userId);
            int startIndex = Math.max(0, products.size() - 100);
            exportData.put("recentProducts", products.subList(startIndex, products.size()));

            // Запись в файл
            String json = objectMapper.writeValueAsString(exportData);
            FileStorage.writeJson(exportFilename, json);

            logger.info("Data exported for user {} to {}", userId, exportFilename);
            return true;

        } catch (Exception e) {
            logger.error("Error exporting data for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Поиск товаров пользователя по критериям
     */
    public static List<Product> searchUserProducts(int userId, String searchTerm,
                                                   double minPrice, double maxPrice) {
        List<Product> allProducts = getUserProducts(userId);
        List<Product> results = new ArrayList<>();

        String searchLower = searchTerm.toLowerCase();

        for (Product product : allProducts) {
            boolean matches = true;

            // Поиск по тексту
            if (searchTerm != null && !searchTerm.isEmpty()) {
                String title = product.getTitle().toLowerCase();
                String query = product.getQuery().toLowerCase();
                matches = title.contains(searchLower) || query.contains(searchLower);
            }

            // Фильтр по цене
            if (matches && minPrice > 0) {
                matches = product.getPrice() >= minPrice;
            }

            if (matches && maxPrice > 0) {
                matches = product.getPrice() <= maxPrice;
            }

            if (matches) {
                results.add(product);
            }
        }

        return results;
    }
}