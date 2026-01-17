package com.parser.parser;

import com.parser.model.Product;
import com.parser.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Абстрактный базовый класс для всех парсеров сайтов
 */
public abstract class BaseParser implements SiteParser {
    protected static final Logger logger = LoggerFactory.getLogger(BaseParser.class);

    protected String siteName;
    protected String baseUrl;
    protected String userAgent;

    // Статистика
    protected int totalRequests = 0;
    protected int failedRequests = 0;
    protected long totalParseTime = 0;

    public BaseParser(String siteName, String baseUrl) {
        this.siteName = siteName;
        this.baseUrl = baseUrl;
        this.userAgent = HttpUtils.getDefaultUserAgent();
    }

    @Override
    public String getSiteName() {
        return siteName;
    }

    /**
     * Абстрактный метод для парсинга ответа
     */
    protected abstract List<Product> parseResponse(String response, String query);

    /**
     * Абстрактный метод для построения URL поиска
     */
    protected abstract String buildSearchUrl(String query, int page, int rows);

    /**
     * Основной метод поиска товаров
     */
    @Override
    public List<Product> search(String query, int maxPages, int rowsPerPage, int maxAgeMinutes) {
        List<Product> allProducts = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        logger.info("Starting search for '{}' on {}, pages: {}, rows: {}, max age: {}min",
                query, siteName, maxPages, rowsPerPage, maxAgeMinutes);

        for (int page = 1; page <= maxPages; page++) {
            try {
                // Построение URL
                String url = buildSearchUrl(query, page, rowsPerPage);
                logger.debug("Fetching page {}: {}", page, url);

                // Выполнение запроса
                long requestStartTime = System.currentTimeMillis();
                String response = HttpUtils.sendGetRequest(url, userAgent);
                totalRequests++;

                long requestTime = System.currentTimeMillis() - requestStartTime;
                logger.debug("Page {} fetched in {}ms", page, requestTime);

                // Парсинг ответа
                long parseStartTime = System.currentTimeMillis();
                List<Product> products = parseResponse(response, query);
                totalParseTime += System.currentTimeMillis() - parseStartTime;

                if (products.isEmpty()) {
                    logger.debug("No products found on page {}", page);
                    break; // Если на странице нет товаров, прекращаем поиск
                }

                // Фильтрация по возрасту
                List<Product> filtered = new ArrayList<>();
                for (Product product : products) {
                    if (product.getAgeMinutes() <= maxAgeMinutes) {
                        filtered.add(product);
                    } else {
                        logger.trace("Product filtered by age: {}min > {}min",
                                product.getAgeMinutes(), maxAgeMinutes);
                    }
                }

                allProducts.addAll(filtered);
                logger.info("Page {}: found {} products ({} after age filter)",
                        page, products.size(), filtered.size());

                // Проверка, нужно ли продолжать
                if (filtered.size() < rowsPerPage) {
                    logger.debug("Last page reached (fewer products than rows per page)");
                    break;
                }

                // Задержка между запросами для избежания блокировки
                int delay = getRequestDelay();
                if (delay > 0 && page < maxPages) {
                    logger.trace("Waiting {}ms before next request", delay);
                    Thread.sleep(delay);
                }

            } catch (Exception e) {
                failedRequests++;
                logger.error("Error parsing page {}: {}", page, e.getMessage(), e);

                // Если произошла критическая ошибка, прекращаем поиск
                if (shouldStopOnError(e)) {
                    break;
                }

                // Задержка при ошибке
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Search completed for '{}': found {} products in {}ms ({} requests)",
                query, allProducts.size(), totalTime, totalRequests);

        return allProducts;
    }

    /**
     * Получение задержки между запросами (можно переопределить)
     */
    protected int getRequestDelay() {
        return 2000; // 2 секунды по умолчанию
    }

    /**
     * Определение, нужно ли прекращать поиск при ошибке (можно переопределить)
     */
    protected boolean shouldStopOnError(Exception e) {
        // По умолчанию продолжаем при любых ошибках
        return false;
    }

    /**
     * Получение статистики парсера
     */
    public ParserStats getStats() {
        ParserStats stats = new ParserStats();
        stats.siteName = siteName;
        stats.totalRequests = totalRequests;
        stats.failedRequests = failedRequests;
        stats.successRate = totalRequests > 0 ?
                (double)(totalRequests - failedRequests) / totalRequests * 100 : 0;
        stats.avgParseTime = totalRequests > 0 ? totalParseTime / totalRequests : 0;
        return stats;
    }

    /**
     * Вспомогательный класс для статистики
     */
    public static class ParserStats {
        public String siteName;
        public int totalRequests;
        public int failedRequests;
        public double successRate;
        public long avgParseTime;

        @Override
        public String toString() {
            return String.format(
                    "ParserStats{site=%s, requests=%d, failed=%d, success=%.1f%%, avgTime=%dms}",
                    siteName, totalRequests, failedRequests, successRate, avgParseTime
            );
        }
    }

    /**
     * Очистка строки от лишних пробелов и специальных символов
     */
    protected String cleanString(String str) {
        if (str == null) return "";

        // Удаление лишних пробелов
        str = str.trim();

        // Замена нескольких пробелов на один
        str = str.replaceAll("\\s+", " ");

        // Удаление непечатаемых символов
        str = str.replaceAll("[\\p{C}]", "");

        return str;
    }

    /**
     * Извлечение числа из строки
     */
    protected double extractPrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return 0.0;
        }

        try {
            // Удаление всех нецифровых символов, кроме точки
            String clean = priceStr.replaceAll("[^\\d.,]", "");

            // Замена запятой на точку, если необходимо
            clean = clean.replace(',', '.');

            // Удаление лишних точек
            int firstDot = clean.indexOf('.');
            if (firstDot != -1) {
                int lastDot = clean.lastIndexOf('.');
                if (firstDot != lastDot) {
                    clean = clean.substring(0, firstDot + 1) +
                            clean.substring(firstDot + 1).replace(".", "");
                }
            }

            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse price: {}", priceStr);
            return 0.0;
        }
    }

    /**
     * Расчет возраста товара в минутах на основе временной метки
     */
    protected int calculateAgeMinutes(long publishTime) {
        if (publishTime <= 0) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long ageMillis = currentTime - publishTime;

        // Преобразование в минуты
        return (int)(ageMillis / (1000 * 60));
    }

    /**
     * Поиск с параметрами по умолчанию (реализация по умолчанию из интерфейса)
     */
    @Override
    public List<Product> search(String query) {
        return search(query, 3, 100, 1440);
    }
}