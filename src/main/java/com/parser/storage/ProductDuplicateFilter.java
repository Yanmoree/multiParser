package com.parser.storage;

import com.parser.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фильтр для предотвращения отправки дубликатов товаров
 *
 * NOTE:
 * В актуальном потоке отправки уведомлений используется `UserSentProductsManager`
 * (персистентная история отправленных товаров).
 * Этот класс оставлен для обратной совместимости и может использоваться только
 * как in-memory кэш “увиденных” товаров (без гарантии персистентности).
 */
public class ProductDuplicateFilter {
    private static final Logger logger = LoggerFactory.getLogger(ProductDuplicateFilter.class);

    // Кэш: userId -> Set<productId> (найденные товары)
    private static final Map<Long, Set<String>> userFoundProducts = new ConcurrentHashMap<>();

    /**
     * Загрузить найденные товары пользователя
     */
    private static Set<String> loadUserProducts(long userId) {
        List<Product> products = UserDataManager.getUserProducts(userId);
        Set<String> ids = new HashSet<>();

        for (Product p : products) {
            ids.add(p.getId());
        }

        userFoundProducts.put(userId, ids);
        logger.debug("Loaded {} products for user {}", ids.size(), userId);
        return ids;
    }

    /**
     * Получить новые товары (не отправленные ранее)
     */
    public static List<Product> filterNew(long userId, List<Product> products) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> found = userFoundProducts.computeIfAbsent(userId,
                k -> loadUserProducts(userId));

        List<Product> newProducts = new ArrayList<>();

        for (Product p : products) {
            if (!found.contains(p.getId())) {
                newProducts.add(p);
                found.add(p.getId()); // Добавляем сразу, чтобы не отправить дважды
            }
        }

        logger.info("Filter: {} total, {} new for user {}",
                products.size(), newProducts.size(), userId);

        return newProducts;
    }

    /**
     * Добавить товары в кэш пользователя
     */
    public static void addProductsToCache(long userId, List<Product> products) {
        Set<String> found = userFoundProducts.computeIfAbsent(userId,
                k -> loadUserProducts(userId));

        for (Product p : products) {
            found.add(p.getId());
        }

        logger.debug("Added {} products to cache for user {}", products.size(), userId);
    }

    /**
     * Очистить кэш пользователя (например, при удалении его товаров)
     */
    public static void clearUserCache(long userId) {
        userFoundProducts.remove(userId);
        logger.info("Cache cleared for user {}", userId);
    }

    /**
     * Очистить весь кэш
     */
    public static void clearAllCache() {
        userFoundProducts.clear();
        logger.info("All cache cleared");
    }

    /**
     * Проверить, был ли товар отправлен пользователю
     */
    public static boolean isProductSent(long userId, String productId) {
        Set<String> found = userFoundProducts.computeIfAbsent(userId,
                k -> loadUserProducts(userId));
        return found.contains(productId);
    }
}