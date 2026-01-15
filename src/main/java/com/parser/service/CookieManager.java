package com.parser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер для работы с куки и сессиями
 */
public class CookieManager {
    private static final Logger logger = LoggerFactory.getLogger(CookieManager.class);

    // Хранилище куки по доменам
    private static final Map<String, Map<String, String>> cookieStore = new ConcurrentHashMap<>();

    // Время жизни куки
    private static final long COOKIE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 часа
    private static final Map<String, Long> cookieExpiry = new ConcurrentHashMap<>();

    /**
     * Установка куки для домена
     */
    public static void setCookie(String domain, String name, String value) {
        cookieStore.computeIfAbsent(domain, k -> new ConcurrentHashMap<>())
                .put(name, value);

        String key = domain + ":" + name;
        cookieExpiry.put(key, System.currentTimeMillis() + COOKIE_EXPIRY_MS);

        logger.debug("Cookie set: {}={} for domain {}", name, value, domain);
    }

    /**
     * Получение куки для домена
     */
    public static String getCookie(String domain, String name) {
        cleanupExpiredCookies();

        Map<String, String> domainCookies = cookieStore.get(domain);
        if (domainCookies == null) {
            return null;
        }

        String key = domain + ":" + name;
        if (cookieExpiry.containsKey(key) &&
                cookieExpiry.get(key) < System.currentTimeMillis()) {
            // Куки истек
            domainCookies.remove(name);
            cookieExpiry.remove(key);
            return null;
        }

        return domainCookies.get(name);
    }

    /**
     * Получение всех куки для домена в виде строки заголовка
     */
    public static String getCookieHeader(String domain) {
        cleanupExpiredCookies();

        Map<String, String> domainCookies = cookieStore.get(domain);
        if (domainCookies == null || domainCookies.isEmpty()) {
            return "";
        }

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : domainCookies.entrySet()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return header.toString();
    }

    /**
     * Удаление куки
     */
    public static boolean removeCookie(String domain, String name) {
        Map<String, String> domainCookies = cookieStore.get(domain);
        if (domainCookies == null) {
            return false;
        }

        boolean removed = domainCookies.remove(name) != null;
        if (removed) {
            String key = domain + ":" + name;
            cookieExpiry.remove(key);
            logger.debug("Cookie removed: {} from domain {}", name, domain);
        }

        return removed;
    }

    /**
     * Очистка всех куки для домена
     */
    public static void clearCookies(String domain) {
        Map<String, String> domainCookies = cookieStore.remove(domain);
        if (domainCookies != null) {
            // Удаление из expiry map
            for (String name : domainCookies.keySet()) {
                String key = domain + ":" + name;
                cookieExpiry.remove(key);
            }
            logger.debug("All cookies cleared for domain {}", domain);
        }
    }

    /**
     * Очистка истекших куки
     */
    private static void cleanupExpiredCookies() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> expiryIterator = cookieExpiry.entrySet().iterator();

        int removedCount = 0;
        while (expiryIterator.hasNext()) {
            Map.Entry<String, Long> entry = expiryIterator.next();
            if (entry.getValue() < now) {
                String key = entry.getKey();
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    Map<String, String> domainCookies = cookieStore.get(parts[0]);
                    if (domainCookies != null) {
                        domainCookies.remove(parts[1]);
                    }
                }
                expiryIterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.debug("Cleaned up {} expired cookies", removedCount);
        }
    }

    /**
     * Парсинг куки из заголовка Set-Cookie
     */
    public static void parseSetCookieHeader(String domain, String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) {
            return;
        }

        String[] cookies = setCookieHeader.split(";\\s*");
        for (String cookie : cookies) {
            String[] parts = cookie.split("=", 2);
            if (parts.length == 2) {
                String name = parts[0].trim();
                String value = parts[1].trim();

                // Пропускаем специальные атрибуты
                if (name.equalsIgnoreCase("path") ||
                        name.equalsIgnoreCase("domain") ||
                        name.equalsIgnoreCase("expires") ||
                        name.equalsIgnoreCase("max-age") ||
                        name.equalsIgnoreCase("secure") ||
                        name.equalsIgnoreCase("httponly") ||
                        name.equalsIgnoreCase("samesite")) {
                    continue;
                }

                setCookie(domain, name, value);
            }
        }
    }

    /**
     * Получение статистики куки
     */
    public static Map<String, Object> getCookieStats() {
        cleanupExpiredCookies();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDomains", cookieStore.size());

        int totalCookies = 0;
        Map<String, Integer> cookiesPerDomain = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : cookieStore.entrySet()) {
            int count = entry.getValue().size();
            totalCookies += count;
            cookiesPerDomain.put(entry.getKey(), count);
        }

        stats.put("totalCookies", totalCookies);
        stats.put("cookiesPerDomain", cookiesPerDomain);
        stats.put("expiryCount", cookieExpiry.size());

        return stats;
    }

    /**
     * Сохранение куки в файл
     */
    public static void saveCookiesToFile(String filename) {
        // В реальном проекте здесь будет сохранение куки в файл
        logger.debug("Cookies saved to file: {}", filename);
    }

    /**
     * Загрузка куки из файла
     */
    public static void loadCookiesFromFile(String filename) {
        // В реальном проекте здесь будет загрузка куки из файла
        logger.debug("Cookies loaded from file: {}", filename);
    }
}