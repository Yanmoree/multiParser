package com.parser.util;

import com.parser.config.Config;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.apache.http.client.methods.HttpHead;  // Добавьте эту строку

/**
 * Утилиты для работы с HTTP запросами
 */
public class HttpUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private static final Random random = new Random();

    // Конфигурация HTTP клиента
    private static final RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Config.getInt("http.connect.timeout", 10000))
            .setSocketTimeout(Config.getInt("http.read.timeout", 15000))
            .setConnectionRequestTimeout(5000)
            .setRedirectsEnabled(true)
            .build();

    // Пул HTTP клиентов
    private static CloseableHttpClient httpClient = null;

    /**
     * Получение HTTP клиента
     */
    private static synchronized CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setUserAgent(getRandomUserAgent())
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(20)
                    .build();
            logger.debug("HTTP client initialized");
        }
        return httpClient;
    }

    /**
     * Отправка GET запроса
     */
    public static String sendGetRequest(String url) throws Exception {
        return sendGetRequest(url, getDefaultUserAgent());
    }

    /**
     * Отправка GET запроса с указанием User-Agent
     */
    public static String sendGetRequest(String url, String userAgent) throws Exception {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        logger.debug("Sending GET request to: {}", url);

        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", userAgent);
        request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.addHeader("Accept-Language", "en-US,en;q=0.5");
        request.addHeader("Accept-Encoding", "gzip, deflate");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("Upgrade-Insecure-Requests", "1");

        // Добавляем случайные заголовки для имитации браузера
        addRandomHeaders(request);

        try (CloseableHttpResponse response = getHttpClient().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.debug("Response status: {}", statusCode);

            if (statusCode == 200) {
                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.trace("Response content length: {} chars", content.length());
                return content;
            } else if (statusCode == 429) {
                logger.warn("Rate limit exceeded for URL: {}", url);
                throw new Exception("Rate limit exceeded (429)");
            } else if (statusCode == 403) {
                logger.warn("Access forbidden for URL: {}", url);
                throw new Exception("Access forbidden (403)");
            } else if (statusCode == 404) {
                logger.warn("URL not found: {}", url);
                throw new Exception("Page not found (404)");
            } else {
                logger.warn("HTTP error {} for URL: {}", statusCode, url);
                throw new Exception("HTTP error: " + statusCode);
            }
        } catch (Exception e) {
            logger.error("Error sending request to {}: {}", url, e.getMessage());
            throw e;
        }
    }

    /**
     * Получение стандартного User-Agent
     */
    public static String getDefaultUserAgent() {
        return Config.getString("http.user.agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    }

    /**
     * Получение случайного User-Agent
     */
    public static String getRandomUserAgent() {
        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1"
        };

        return userAgents[random.nextInt(userAgents.length)];
    }

    /**
     * Добавление случайных заголовков для имитации браузера
     */
    private static void addRandomHeaders(HttpGet request) {
        // Случайные заголовки для обхода простых ботов
        if (random.nextBoolean()) {
            request.addHeader("Sec-Fetch-Dest", "document");
            request.addHeader("Sec-Fetch-Mode", "navigate");
            request.addHeader("Sec-Fetch-Site", "none");
            request.addHeader("Sec-Fetch-User", "?1");
        }

        if (random.nextBoolean()) {
            request.addHeader("Cache-Control", "max-age=0");
        }

        // Случайный Referer
        String[] referers = {
                "https://www.google.com/",
                "https://www.bing.com/",
                "https://www.yahoo.com/",
                "https://www.baidu.com/",
                ""
        };
        String referer = referers[random.nextInt(referers.length)];
        if (!referer.isEmpty()) {
            request.addHeader("Referer", referer);
        }
    }

    /**
     * Кодирование URL параметров
     */
    public static String encodeUrl(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error encoding URL value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Декодирование URL параметров
     */
    public static String decodeUrl(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error decoding URL value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Создание случайной задержки для имитации человеческого поведения
     */
    public static void randomDelay(int minMs, int maxMs) {
        try {
            int delay = minMs + random.nextInt(maxMs - minMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Delay interrupted");
        }
    }

    /**
     * Проверка доступности URL
     */
    public static boolean isUrlAccessible(String url) {
        try {
            HttpHead request = new HttpHead(url);
            request.addHeader("User-Agent", getDefaultUserAgent());

            try (CloseableHttpResponse response = getHttpClient().execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                return statusCode == 200;
            }
        } catch (Exception e) {
            logger.debug("URL not accessible {}: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Получение размера содержимого по URL
     */
    public static long getContentLength(String url) throws Exception {
        try {
            HttpHead request = new HttpHead(url);
            request.addHeader("User-Agent", getDefaultUserAgent());

            try (CloseableHttpResponse response = getHttpClient().execute(request)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    org.apache.http.Header[] headers = response.getHeaders("Content-Length");
                    if (headers.length > 0) {
                        return Long.parseLong(headers[0].getValue());
                    }
                }
                return -1;
            }
        } catch (Exception e) {
            logger.error("Error getting content length for {}: {}", url, e.getMessage());
            throw e;
        }
    }

    /**
     * Закрытие HTTP клиента
     */
    public static synchronized void closeHttpClient() {
        if (httpClient != null) {
            try {
                httpClient.close();
                httpClient = null;
                logger.debug("HTTP client closed");
            } catch (Exception e) {
                logger.error("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }
}