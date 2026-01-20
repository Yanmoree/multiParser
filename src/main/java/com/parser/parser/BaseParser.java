package com.parser.parser;

import com.parser.config.Config;
import com.parser.model.Product;
import com.parser.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Абстрактный базовый класс для всех парсеров
 */
public abstract class BaseParser implements SiteParser {
    protected static final Logger logger = LoggerFactory.getLogger(BaseParser.class);

    protected String siteName;
    protected String baseUrl;
    protected String userAgent;

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

    protected abstract List<Product> parseResponse(String response, String query);
    protected abstract String buildSearchUrl(String query, int page, int rows);

    @Override
    public List<Product> search(String query, int maxPages, int rowsPerPage, int maxAgeMinutes) {
        List<Product> allProducts = new ArrayList<>();

        logger.info("Starting search: site={}, query='{}', pages={}, rows={}, maxAge={}min",
                siteName, query, maxPages, rowsPerPage, maxAgeMinutes);

        for (int page = 1; page <= maxPages; page++) {
            if (Thread.currentThread().isInterrupted()) break;

            try {
                String url = buildSearchUrl(query, page, rowsPerPage);
                long requestStart = System.currentTimeMillis();
                String response = HttpUtils.sendGetRequest(url, userAgent);
                totalRequests++;

                long parseStart = System.currentTimeMillis();
                List<Product> products = parseResponse(response, query);
                totalParseTime += System.currentTimeMillis() - parseStart;

                if (products.isEmpty()) {
                    logger.debug("No products on page {}", page);
                    break;
                }

                // Фильтруем по возрасту
                List<Product> filtered = new ArrayList<>();
                for (Product p : products) {
                    if (p.getAgeMinutes() <= maxAgeMinutes) {
                        filtered.add(p);
                    }
                }

                allProducts.addAll(filtered);
                logger.info("Page {}: found {} products ({} after age filter)", page, products.size(), filtered.size());

                if (filtered.size() < rowsPerPage) break;

                int delay = getRequestDelay();
                if (delay > 0 && page < maxPages) {
                    Thread.sleep(delay);
                }

            } catch (Exception e) {
                failedRequests++;
                logger.error("Error on page {}: {}", page, e.getMessage());

                if (shouldStopOnError(e)) break;

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.info("Search completed: found {} products in {} requests", allProducts.size(), totalRequests);
        return allProducts;
    }

    @Override
    public List<Product> search(String query) {
        return search(query, 3, 100, 1440);
    }

    /**
     * Метод для выполнения запроса (может быть переопределен для POST)
     */
    protected String executeSearchRequest(String url, String query, int page, int rows) throws Exception {
        return HttpUtils.sendGetRequest(url, userAgent);
    }

    protected int getRequestDelay() {
        return Config.getInt("api.goofish.delay.between.requests", 2000);
    }

    protected boolean shouldStopOnError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("403") || msg.contains("429") || msg.contains("blocked") ||
                msg.contains("被挤爆啦") || msg.contains("FAIL_SYS_ILLEGAL_ACCESS"));
    }

    protected String cleanString(String str) {
        if (str == null) return "";
        return str.trim().replaceAll("\\s+", " ").replaceAll("[\\p{C}]", "");
    }

    protected double extractPrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) return 0.0;
        try {
            String clean = priceStr.replaceAll("[^\\d.,]", "").replace(',', '.');
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public ParserStats getStats() {
        return new ParserStats(siteName, totalRequests, failedRequests,
                totalRequests > 0 ? (double) (totalRequests - failedRequests) / totalRequests * 100 : 0,
                totalRequests > 0 ? totalParseTime / totalRequests : 0);
    }

    public static class ParserStats {
        public String siteName;
        public int totalRequests;
        public int failedRequests;
        public double successRate;
        public long avgParseTime;

        public ParserStats(String siteName, int totalRequests, int failedRequests, double successRate, long avgParseTime) {
            this.siteName = siteName;
            this.totalRequests = totalRequests;
            this.failedRequests = failedRequests;
            this.successRate = successRate;
            this.avgParseTime = avgParseTime;
        }

        @Override
        public String toString() {
            return String.format("ParserStats{site=%s, req=%d, failed=%d, success=%.1f%%, avgTime=%dms}",
                    siteName, totalRequests, failedRequests, successRate, avgParseTime);
        }
    }
}