package com.parser.config;

/**
 * Класс с константами для настроек парсера
 */
public class ParserSettings {
    // Минимальные и максимальные значения для валидации

    public static final int MIN_CHECK_INTERVAL = 10; // секунд
    public static final int MAX_CHECK_INTERVAL = 3600; // секунд

    public static final int MIN_MAX_AGE_MINUTES = 1;
    public static final int MAX_MAX_AGE_MINUTES = 10080; // 7 дней

    public static final int MIN_MAX_PAGES = 1;
    public static final int MAX_MAX_PAGES = 50;

    public static final int MIN_ROWS_PER_PAGE = 10;
    public static final int MAX_ROWS_PER_PAGE = 1000;

    // Коды валют
    public static final String CURRENCY_YUAN = "yuan";
    public static final String CURRENCY_RUBLES = "rubles";

    // Поддерживаемые сайты
    public static final String SITE_GOOFISH = "goofish";
    public static final String SITE_TAOBAO = "taobao";
    public static final String SITE_JD = "jd";

    // Статусы парсера
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_ERROR = "error";

    // Типы уведомлений
    public static final String NOTIFY_TELEGRAM = "telegram";
    public static final String NOTIFY_EMAIL = "email";
    public static final String NOTIFY_WEBHOOK = "webhook";

    /**
     * Валидация интервала проверки
     */
    public static boolean isValidCheckInterval(int interval) {
        return interval >= MIN_CHECK_INTERVAL && interval <= MAX_CHECK_INTERVAL;
    }

    /**
     * Валидация максимального возраста товара
     */
    public static boolean isValidMaxAge(int maxAge) {
        return maxAge >= MIN_MAX_AGE_MINUTES && maxAge <= MAX_MAX_AGE_MINUTES;
    }

    /**
     * Нормализация интервала проверки
     */
    public static int normalizeCheckInterval(int interval) {
        return Math.max(MIN_CHECK_INTERVAL, Math.min(interval, MAX_CHECK_INTERVAL));
    }

    /**
     * Нормализация максимального возраста
     */
    public static int normalizeMaxAge(int maxAge) {
        return Math.max(MIN_MAX_AGE_MINUTES, Math.min(maxAge, MAX_MAX_AGE_MINUTES));
    }

    /**
     * Проверка поддержки сайта
     */
    public static boolean isSiteSupported(String site) {
        return SITE_GOOFISH.equalsIgnoreCase(site) ||
                SITE_TAOBAO.equalsIgnoreCase(site) ||
                SITE_JD.equalsIgnoreCase(site);
    }

    /**
     * Получение курса валюты
     */
    public static double getCurrencyRate(String fromCurrency, String toCurrency) {
        if (CURRENCY_YUAN.equals(fromCurrency) && CURRENCY_RUBLES.equals(toCurrency)) {
            return 12.5; // Примерный курс юаня к рублю
        } else if (CURRENCY_RUBLES.equals(fromCurrency) && CURRENCY_YUAN.equals(toCurrency)) {
            return 0.08; // Обратный курс
        }
        return 1.0;
    }
}