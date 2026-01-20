package com.parser.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для создания парсеров различных сайтов
 * Упрощена для легкого добавления новых сайтов
 */
public class ParserFactory {
    private static final Logger logger = LoggerFactory.getLogger(ParserFactory.class);
    private static final Map<String, Class<? extends SiteParser>> parsers = new HashMap<>();
    private static final Map<String, SiteParser> instances = new HashMap<>();

    static {
        // Регистрируем доступные парсеры
        registerParser("goofish", GoofishParser.class);
        // Здесь легко добавить новые парсеры:
        // registerParser("taobao", TaobaoParser.class);
        // registerParser("jd", JdParser.class);
    }

    public static void registerParser(String siteName, Class<? extends SiteParser> parserClass) {
        String key = siteName.toLowerCase();
        parsers.put(key, parserClass);
        logger.info("Registered parser for: {}", siteName);
    }

    public static SiteParser createParser(String siteName) {
        String key = siteName.toLowerCase();

        if (!parsers.containsKey(key)) {
            throw new IllegalArgumentException("Unsupported site: " + siteName);
        }

        if (!instances.containsKey(key)) {
            try {
                SiteParser parser = parsers.get(key).getDeclaredConstructor().newInstance();
                instances.put(key, parser);
                logger.debug("Created parser instance for: {}", siteName);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create parser for: " + siteName, e);
            }
        }

        return instances.get(key);
    }

    public static boolean hasParser(String siteName) {
        return parsers.containsKey(siteName.toLowerCase());
    }

    public static String[] getSupportedSites() {
        return parsers.keySet().toArray(new String[0]);
    }

    public static void clearCache() {
        instances.clear();
        logger.info("Parser cache cleared");
    }
}