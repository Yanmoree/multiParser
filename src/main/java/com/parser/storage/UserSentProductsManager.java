package com.parser.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserSentProductsManager {
    private static final Logger logger = LoggerFactory.getLogger(UserSentProductsManager.class);

    private static final Map<Long, Set<String>> userSentProducts = new ConcurrentHashMap<>();
    private static final String SENT_PRODUCTS_DIR = "data/sent_products";

    static {
        FileStorage.createDirectory(SENT_PRODUCTS_DIR);
    }

    public static Set<String> getSentProductsForUser(long userId) {
        return userSentProducts.computeIfAbsent(userId, k -> loadUserSentProducts(userId));
    }

    public static void markProductsAsSent(long userId, Set<String> productIds) {
        Set<String> sentProducts = getSentProductsForUser(userId);
        sentProducts.addAll(productIds);
        saveUserSentProducts(userId, sentProducts);
        logger.info("Добавлено {} товаров в историю отправленных для пользователя {}",
                productIds.size(), userId);
    }

    public static void clearUserHistory(long userId) {
        userSentProducts.remove(userId);
        FileStorage.deleteFile(SENT_PRODUCTS_DIR + "/user_" + userId + ".txt");
        logger.info("История отправленных товаров очищена для пользователя {}", userId);
    }

    private static Set<String> loadUserSentProducts(long userId) {
        Set<String> products = new HashSet<>();
        String filename = SENT_PRODUCTS_DIR + "/user_" + userId + ".txt";

        for (String line : FileStorage.readLines(filename)) {
            line = line.trim();
            if (!line.isEmpty()) {
                products.add(line);
            }
        }

        logger.debug("Загружено {} отправленных товаров для пользователя {}",
                products.size(), userId);
        return products;
    }

    private static void saveUserSentProducts(long userId, Set<String> products) {
        String filename = SENT_PRODUCTS_DIR + "/user_" + userId + ".txt";
        List<String> lines = new ArrayList<>(products);
        FileStorage.writeLines(filename, lines);
    }
}