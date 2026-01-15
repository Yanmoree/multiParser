package com.parser.parser;

import com.parser.config.ParserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для создания парсеров различных сайтов
 */
public class ParserFactory {
    private static final Logger logger = LoggerFactory.getLogger(ParserFactory.class);

    private static final Map<String, Class<? extends SiteParser>> parsers = new HashMap<>();
    private static final Map<String, SiteParser> parserInstances = new HashMap<>();

    static {
        registerParser(ParserSettings.SITE_GOOFISH, GoofishParser.class);
        // Здесь можно зарегистрировать парсеры для других сайтов
        // registerParser(ParserSettings.SITE_TAOBAO, TaobaoParser.class);
        // registerParser(ParserSettings.SITE_JD, JdParser.class);
    }

    /**
     * Регистрация парсера
     */
    public static void registerParser(String siteName, Class<? extends SiteParser> parserClass) {
        String key = siteName.toLowerCase();
        parsers.put(key, parserClass);
        logger.info("Registered parser for site: {}", siteName);
    }

    /**
     * Создание парсера для указанного сайта
     */
    public static SiteParser createParser(String siteName) {
        String key = siteName.toLowerCase();

        // Проверка поддержки сайта
        if (!parsers.containsKey(key)) {
            logger.error("No parser registered for site: {}", siteName);
            throw new IllegalArgumentException("Unsupported site: " + siteName);
        }

        try {
            // Используем кэширование экземпляров
            if (!parserInstances.containsKey(key)) {
                Class<? extends SiteParser> parserClass = parsers.get(key);
                SiteParser parser = parserClass.getDeclaredConstructor().newInstance();
                parserInstances.put(key, parser);
                logger.debug("Created new parser instance for site: {}", siteName);
            }

            return parserInstances.get(key);

        } catch (Exception e) {
            logger.error("Failed to create parser for site {}: {}", siteName, e.getMessage(), e);
            throw new RuntimeException("Failed to create parser for site: " + siteName, e);
        }
    }

    /**
     * Проверка наличия парсера для сайта
     */
    public static boolean hasParser(String siteName) {
        return parsers.containsKey(siteName.toLowerCase());
    }

    /**
     * Получение списка поддерживаемых сайтов
     */
    public static String[] getSupportedSites() {
        return parsers.keySet().toArray(new String[0]);
    }

    /**
     * Получение информации о всех зарегистрированных парсерах
     */
    public static Map<String, String> getParserInfo() {
        Map<String, String> info = new HashMap<>();

        for (Map.Entry<String, Class<? extends SiteParser>> entry : parsers.entrySet()) {
            info.put(entry.getKey(), entry.getValue().getSimpleName());
        }

        return info;
    }

    /**
     * Очистка кэша парсеров
     */
    public static void clearCache() {
        int count = parserInstances.size();
        parserInstances.clear();
        logger.info("Cleared parser cache ({} instances)", count);
    }

    /**
     * Получение статистики по всем парсерам
     */
    public static Map<String, Object> getParsersStats() {
        Map<String, Object> stats = new HashMap<>();

        for (Map.Entry<String, SiteParser> entry : parserInstances.entrySet()) {
            if (entry.getValue() instanceof BaseParser) {
                BaseParser parser = (BaseParser) entry.getValue();
                stats.put(entry.getKey(), parser.getStats());
            }
        }

        return stats;
    }
}