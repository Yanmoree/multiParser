package com.parser.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для хранения товаров, которые уже были отправлены пользователю
 */
public class UserSentProductsManager {
    private static final Logger logger = LoggerFactory.getLogger(UserSentProductsManager.class);

    // Кэш в памяти: userId -> Set<productId>
    private static final ConcurrentHashMap<Long, Set<String>> userSentProducts = new ConcurrentHashMap<>();

    // Путь к директории для хранения файлов
    private static final String SENT_PRODUCTS_DIR = "sent_products";

    static {
        // Создаем директорию при запуске
        FileStorage.createDirectory(SENT_PRODUCTS_DIR);
        logger.info("✅ Директория для отправленных товаров готова");
    }

    /**
     * Проверяет, был ли товар уже отправлен пользователю
     */
    public static boolean isProductSent(long userId, String productId) {
        Set<String> sentProducts = loadUserSentProducts(userId);
        return sentProducts.contains(productId);
    }

    /**
     * Добавляет товар в список отправленных
     */
    public static void markProductAsSent(long userId, String productId) {
        Set<String> sentProducts = loadUserSentProducts(userId);
        sentProducts.add(productId);
        saveUserSentProducts(userId, sentProducts);

        // Обновляем кэш
        userSentProducts.put(userId, sentProducts);

        logger.debug("✅ Товар {} отмечен как отправленный пользователю {}", productId, userId);
    }

    /**
     * Добавляет несколько товаров в список отправленных
     */
    public static void markProductsAsSent(long userId, Set<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        Set<String> sentProducts = loadUserSentProducts(userId);
        sentProducts.addAll(productIds);
        saveUserSentProducts(userId, sentProducts);

        userSentProducts.put(userId, sentProducts);

        logger.info("✅ {} товаров отмечены как отправленные пользователю {}", productIds.size(), userId);
    }

    /**
     * Фильтрует только новые товары (не отправленные ранее)
     */
    public static Set<String> filterNewProducts(long userId, Set<String> productIds) {
        Set<String> newProducts = new HashSet<>();
        Set<String> sentProducts = loadUserSentProducts(userId);

        for (String productId : productIds) {
            if (!sentProducts.contains(productId)) {
                newProducts.add(productId);
            }
        }

        logger.debug("Фильтр: {} всего, {} новых для пользователя {}",
                productIds.size(), newProducts.size(), userId);

        return newProducts;
    }

    /**
     * Очищает историю отправленных товаров для пользователя
     */
    public static void clearUserHistory(long userId) {
        String filename = SENT_PRODUCTS_DIR + "/user_" + userId + ".txt";
        FileStorage.deleteFile(filename);
        userSentProducts.remove(userId);
        logger.info("✅ История отправленных товаров очищена для пользователя {}", userId);
    }

    /**
     * Загружает отправленные товары пользователя (из файла или кэша)
     */
    private static Set<String> loadUserSentProducts(long userId) {
        // Проверяем кэш
        if (userSentProducts.containsKey(userId)) {
            return userSentProducts.get(userId);
        }

        // Загружаем из файла
        String filename = SENT_PRODUCTS_DIR + "/user_" + userId + ".txt";
        Set<String> sentProducts = new HashSet<>();

        try {
            for (String line : FileStorage.readLines(filename)) {
                line = line.trim();
                if (!line.isEmpty()) {
                    sentProducts.add(line);
                }
            }
            logger.debug("Загружено {} отправленных товаров для пользователя {}",
                    sentProducts.size(), userId);
        } catch (Exception e) {
            logger.debug("Файл с отправленными товарами не найден для пользователя {}, создаем новый", userId);
        }

        userSentProducts.put(userId, sentProducts);
        return sentProducts;
    }

    /**
     * Сохраняет отправленные товары пользователя в файл
     */
    private static void saveUserSentProducts(long userId, Set<String> sentProducts) {
        String filename = SENT_PRODUCTS_DIR + "/user_" + userId + ".txt";
        FileStorage.writeLines(filename, new java.util.ArrayList<>(sentProducts));
    }

    /**
     * Получает статистику по отправленным товарам
     */
    public static String getStats(long userId) {
        Set<String> sentProducts = loadUserSentProducts(userId);
        return String.format("Отправлено товаров: %d", sentProducts.size());
    }
}