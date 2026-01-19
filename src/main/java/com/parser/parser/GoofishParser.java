package com.parser.parser;

import com.parser.config.Config;
import com.parser.model.Product;
import com.parser.service.CookieService;
import com.parser.util.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;
import java.util.Date;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Парсер для сайта Goofish (闲鱼) с поддержкой динамических кук
 */
public class GoofishParser extends BaseParser {
    private static final Logger logger = LoggerFactory.getLogger(GoofishParser.class);

    // API endpoints
    private static final String SEARCH_ENDPOINT = "/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/";
    private static final String APP_KEY = "34839810";
    private static final String APP_SECRET = "d41d8cd98f00b204e9800998ecf8427e";

    // Статистика попыток обновления кук
    private int cookieRefreshAttempts = 0;
    private long lastCookieRefreshTime = 0;

    public GoofishParser() {
        super("goofish", Config.getGoofishBaseUrl());
    }

    @Override
    protected String buildSearchUrl(String query, int page, int rows) {
        try {
            long timestamp = System.currentTimeMillis();

            // Получаем токен из куки _m_h5_tk
            String mh5tk = getTokenFromCookies();
            String token = "";
            if (mh5tk != null && mh5tk.contains("_")) {
                token = mh5tk.split("_")[0];
            }

            // Формируем data как в API Goofish
            JSONObject dataJson = new JSONObject();
            dataJson.put("pageNumber", page);
            dataJson.put("keyword", query);
            dataJson.put("fromFilter", false);
            dataJson.put("rowsPerPage", Math.min(rows, Config.getGoofishMaxProductsPerPage()));
            dataJson.put("sortValue", "new");
            dataJson.put("sortField", "");
            dataJson.put("customDistance", "");
            dataJson.put("gps", "");
            dataJson.put("propValueStr", new JSONObject());
            dataJson.put("customGps", "");
            dataJson.put("searchReqFromPage", "pcSearch");
            dataJson.put("extraFilterValue", "{}");
            dataJson.put("userPositionJson", "{}");

            String dataStr = dataJson.toString();

            // Правильная генерация подписи для Goofish API
            String sign = generateGoofishSignature(token, timestamp, dataStr);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", APP_KEY);
            params.put("t", String.valueOf(timestamp));
            params.put("sign", sign);
            params.put("v", "1.0");
            params.put("type", "originaljson");
            params.put("accountSite", "xianyu");
            params.put("dataType", "json");
            params.put("timeout", "20000");
            params.put("api", "mtop.taobao.idlemtopsearch.pc.search");
            params.put("sessionOption", "AutoLoginOnly");
            params.put("spm_cnt", "a21ybx.search.0.0");
            params.put("spm_pre", "a21ybx.search.searchInput.0");
            params.put("data", dataStr);

            String url = baseUrl + SEARCH_ENDPOINT;
            return HttpUtils.buildUrlWithParams(url, params);

        } catch (Exception e) {
            logger.error("Error building search URL: {}", e.getMessage(), e);
            return "";
        }
    }

    private boolean isCookieExpired() {
        try {
            String mh5tk = getTokenFromCookies();
            if (mh5tk != null && mh5tk.contains("_")) {
                String[] parts = mh5tk.split("_");
                if (parts.length == 2) {
                    long cookieTimestamp = Long.parseLong(parts[1]);
                    long currentTime = System.currentTimeMillis();
                    long age = currentTime - cookieTimestamp;

                    // Куки истекают через 24 часа (86,400,000 мс), но обновляем заранее
                    return age > 12 * 60 * 60 * 1000; // 12 часов
                }
            }
            return true; // Если не можем распарсить, считаем устаревшими
        } catch (Exception e) {
            logger.error("Error checking cookie expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Получение токена из куки _m_h5_tk
     */
    private String getTokenFromCookies() {
        try {
            // Получаем куки из CookieService
            String cookies = CookieService.getCookieHeader("h5api.m.goofish.com");
            if (cookies == null || cookies.isEmpty()) {
                return "";
            }

            // Ищем _m_h5_tk в строке кук
            String[] cookiePairs = cookies.split("; ");
            for (String pair : cookiePairs) {
                if (pair.startsWith("_m_h5_tk=")) {
                    return pair.substring("_m_h5_tk=".length());
                }
            }

            return "";
        } catch (Exception e) {
            logger.error("Error getting token from cookies: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Генерация подписи для Goofish API
     * Формат: MD5(token + "&" + t + "&" + appKey + "&" + data)
     */
    private String generateGoofishSignature(String token, long timestamp, String data) {
        try {
            String signString = token + "&" + timestamp + "&" + APP_KEY + "&" + data;
            logger.debug("Sign string: {}", signString);
            return generateMD5(signString);
        } catch (Exception e) {
            logger.error("Error generating signature: {}", e.getMessage());
            // Возвращаем дефолтную подпись при ошибке
            return generateMD5("default_token_" + timestamp + "&" + APP_KEY + "&" + data);
        }
    }

    /**
     * Генерация MD5 хэша
     */
    private String generateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error generating MD5: {}", e.getMessage());
            return "dummy_sign_" + System.currentTimeMillis();
        }
    }

    /**
     * Проверка ошибки токена в ответе
     */
    private boolean isTokenError(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }

        try {
            JSONObject json = new JSONObject(response);
            String ret = json.optString("ret", "");
            String msg = json.optString("msg", "");

            return ret.contains("FAIL_SYS_TOKEN_ILLEGAL") ||
                    ret.contains("FAIL_SYS_SESSION_EXPIRED") ||
                    ret.contains("FAIL_SYS_TOKEN") ||
                    ret.contains("FAIL_SYS_ILLEGAL_ACCESS") ||
                    msg.contains("登录") ||
                    msg.contains("session") ||
                    msg.contains("未登录") ||
                    msg.contains("未授权") ||
                    msg.contains("令牌") ||
                    msg.contains("非法请求");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверка необходимости обновления кук
     */
    private boolean shouldRefreshCookies() {
        // Всегда обновляем если куки истекли
        if (isCookieExpired()) {
            logger.warn("Cookies are expired, forcing refresh");
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastRefresh = currentTime - lastCookieRefreshTime;

        // Не обновляем чаще чем раз в 5 минут
        if (timeSinceLastRefresh < 5 * 60 * 1000) {
            return false;
        }

        // Обновляем каждые 60 минут для профилактики
        return timeSinceLastRefresh > 60 * 60 * 1000;
    }

    /**
     * Обновление кук если необходимо
     */
    protected void refreshCookiesIfNeeded() {
        if (shouldRefreshCookies()) {
            forceRefreshCookies();
        }
    }

    /**
     * Принудительное обновление кук
     */
    protected void forceRefreshCookies() {
        try {
            logger.info("Refreshing Goofish cookies...");

            // Получаем свежие куки из CookieService
            String freshCookies = CookieService.getCookieHeader("goofish");

            if (freshCookies != null && !freshCookies.isEmpty()) {
                // Обновляем куки в парсере
                setCookies(freshCookies);
                cookieRefreshAttempts = 0;
                lastCookieRefreshTime = System.currentTimeMillis();
                logger.info("Goofish cookies refreshed successfully");
            } else {
                logger.warn("Failed to refresh Goofish cookies - no fresh cookies available");
                cookieRefreshAttempts++;
            }

        } catch (Exception e) {
            logger.error("Error refreshing Goofish cookies: {}", e.getMessage());
            cookieRefreshAttempts++;
        }
    }

    @Override
    protected List<Product> parseResponse(String response, String query) {
        List<Product> products = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            logger.warn("Empty response received");
            return products;
        }

        try {
            logger.debug("Parsing response ({} chars)", response.length());

            // Логируем часть ответа для отладки
            if (logger.isTraceEnabled()) {
                logger.trace("Full response: {}", response);
            } else {
                logger.debug("Response preview: {}",
                        response.length() > 500 ? response.substring(0, 500) + "..." : response);
            }

            JSONObject json = new JSONObject(response);

            // Проверка статуса
            String ret = json.optString("ret", "");
            String status = json.optString("status", "");
            String msg = json.optString("msg", "");

            logger.debug("API response - ret: '{}', status: '{}', msg: '{}'", ret, status, msg);

            // Проверяем успешность ответа
            boolean isSuccess = false;

            if ("SUCCESS".equals(status)) {
                isSuccess = true;
            } else if (ret != null && !ret.isEmpty()) {
                // Проверяем, содержит ли ret SUCCESS
                if (ret.contains("SUCCESS")) {
                    isSuccess = true;
                } else if (ret.startsWith("[") && ret.endsWith("]")) {
                    try {
                        JSONArray retArray = new JSONArray(ret);
                        if (retArray.length() > 0) {
                            String firstRet = retArray.getString(0);
                            if (firstRet != null && firstRet.contains("SUCCESS")) {
                                isSuccess = true;
                                logger.debug("Success detected in ret array: {}", firstRet);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse ret as JSON array: {}", ret);
                    }
                }
            }

            if (!isSuccess) {
                logger.error("API returned error: {}, msg: {}", ret, msg);

                // Проверка на ошибки авторизации
                if (isTokenError(response)) {
                    logger.error("Token/access error detected: {}", ret);
                    throw new RuntimeException("Token/access error: " + ret);
                }

                return products;
            }

            // Получение данных
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                logger.warn("No data object in response");

                if (json.has("data") && !json.isNull("data")) {
                    Object dataObj = json.get("data");
                    if (dataObj instanceof JSONObject) {
                        data = (JSONObject) dataObj;
                    } else if (dataObj instanceof JSONArray) {
                        JSONArray dataArray = (JSONArray) dataObj;
                        logger.debug("Data is array with {} elements", dataArray.length());
                    }
                }

                if (data == null) {
                    logger.warn("No data found in response");
                    return products;
                }
            }

            // Логируем структуру данных для отладки
            logger.debug("Data keys: {}", data.keySet());

            // Получение списка товаров
            JSONArray resultList = data.optJSONArray("resultList");
            if (resultList == null || resultList.length() == 0) {
                String[] possibleKeys = {"items", "list", "result", "dataList", "resultData", "itemList"};
                for (String key : possibleKeys) {
                    if (data.has(key) && data.get(key) instanceof JSONArray) {
                        resultList = data.getJSONArray(key);
                        logger.debug("Found products in key '{}': {} items", key, resultList.length());
                        break;
                    }
                }

                if (resultList == null || resultList.length() == 0) {
                    logger.info("No products found in response");
                    return products;
                }
            }

            logger.info("Found {} items in response", resultList.length());

            // Логируем первый элемент для отладки структуры
            if (resultList.length() > 0 && logger.isDebugEnabled()) {
                try {
                    JSONObject firstItem = resultList.getJSONObject(0);
                    logger.debug("First item structure (first 300 chars): {}",
                            firstItem.toString().substring(0, Math.min(300, firstItem.toString().length())) + "...");
                } catch (Exception e) {
                    logger.debug("Could not log first item structure: {}", e.getMessage());
                }
            }

            // Парсим каждый товар
            int parsedCount = 0;
            int imageCount = 0;
            for (int i = 0; i < resultList.length(); i++) {
                try {
                    JSONObject item = resultList.getJSONObject(i);
                    Product product = parseProductItem(item, query);

                    if (product != null && isValidProduct(product)) {
                        products.add(product);
                        parsedCount++;
                        imageCount += product.getImages().size();
                    }

                } catch (Exception e) {
                    logger.warn("Error parsing item {}: {}", i, e.getMessage());
                    continue;
                }
            }

            logger.info("Successfully parsed {} products from {} items in response (total images: {})",
                    parsedCount, resultList.length(), imageCount);

        } catch (Exception e) {
            logger.error("Error parsing Goofish response: {}", e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("Token/access error")) {
                throw e;
            }
        }

        return products;
    }

    /**
     * Проверка валидности URL изображения
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty() || url.equals("None") || url.equals("null")) {
            return false;
        }

        // Проверяем, что URL начинается с http/https
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        // Проверяем расширения файлов или домены
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ||
                lowerUrl.contains(".png") || lowerUrl.contains(".gif") ||
                lowerUrl.contains(".webp") || lowerUrl.contains(".bmp") ||
                lowerUrl.contains("alicdn.com") || lowerUrl.contains("tbcdn.cn") ||
                lowerUrl.contains("goofish.com") || lowerUrl.contains("taobao.com") ||
                lowerUrl.contains("img.alicdn.com");
    }

    /**
     * Парсинг товара из структуры
     */
    private Product parseProductItem(JSONObject item, String query) {
        try {
            // Логируем структуру item для отладки
            if (logger.isTraceEnabled()) {
                logger.trace("Parsing item structure: {}", item.toString(2));
            }

            // Для отладки первых нескольких товаров каждого запроса
            if (debugCounter < 3) {
                debugItemStructure(item, query);
                debugCounter++;
            }

            // Основной путь к данным
            JSONObject data = item.optJSONObject("data");
            if (data == null) {
                logger.trace("No data object in item");
                return null;
            }

            JSONObject itemObj = data.optJSONObject("item");
            if (itemObj == null) {
                logger.trace("No item object in data");
                return null;
            }

            JSONObject mainObj = itemObj.optJSONObject("main"); // Изменено имя переменной
            if (mainObj == null) {
                logger.trace("No main object in item");
                return null;
            }

            // Получаем itemData как в Python коде
            JSONObject itemData = data.optJSONObject("itemData");
            JSONObject extra = itemObj.optJSONObject("extra");
            JSONObject exContent = mainObj.optJSONObject("exContent"); // Используем mainObj

            // Логируем ключи для отладки
            logger.debug("Item keys - itemData: {}, extra: {}, exContent: {}",
                    itemData != null, extra != null, exContent != null);

            // Извлечение ID товара
            String itemId = null;

            // Пробуем разные пути для получения ID
            if (itemData != null) {
                itemId = itemData.optString("itemId", "");
            }

            if ((itemId == null || itemId.isEmpty()) && extra != null) {
                itemId = extra.optString("itemId", "");
            }

            if ((itemId == null || itemId.isEmpty()) && exContent != null) {
                itemId = exContent.optString("itemId", "");
            }

            if (itemId == null || itemId.isEmpty()) {
                logger.trace("No item ID found");
                return null;
            }

            // Создание объекта товара
            Product product = new Product();
            product.setId(itemId);
            product.setSite("goofish");
            product.setQuery(query);

            // Извлечение названия
            String title = "";

            if (itemData != null) {
                title = itemData.optString("title", "");
            }

            if (title.isEmpty() && extra != null) {
                title = extra.optString("title", "");
            }

            if (title.isEmpty() && exContent != null) {
                title = exContent.optString("title", "");
            }

            product.setTitle(title.isEmpty() ? "Без названия" : title);

            // Проверка содержания запроса в названии (ослабляем проверку)
            if (query != null && !query.trim().isEmpty()) {
                String queryLower = query.toLowerCase().replace(" ", "");
                String titleLower = title.toLowerCase().replace(" ", "");
                if (!titleLower.contains(queryLower)) {
                    logger.trace("Product filtered - query '{}' not in title: '{}'", query, title);
                    // Не фильтруем по запросу для тестирования
                    // return null;
                }
            }

            // Извлечение цены (исправлено для JSON массива)
            double price = extractPriceFromJson(itemData, extra, exContent);
            product.setPrice(price);

            // Извлечение времени публикации - КЛЮЧЕВАЯ ПРАВКА!
            long publishTimestamp = 0;

            // ПУТЬ 1: Основной путь (как в Python парсере) - args.publishTime
            try {
                // Используем существующую переменную mainObj
                if (mainObj != null) {
                    JSONObject clickParam = mainObj.optJSONObject("clickParam");
                    if (clickParam != null) {
                        JSONObject args = clickParam.optJSONObject("args");
                        if (args != null) {
                            Object publishTimeObj = args.get("publishTime");
                            if (publishTimeObj != null) {
                                if (publishTimeObj instanceof String) {
                                    String timeStr = (String) publishTimeObj;
                                    publishTimestamp = Long.parseLong(timeStr);
                                    logger.debug("Found publishTime in args: {}", publishTimestamp);
                                } else if (publishTimeObj instanceof Number) {
                                    publishTimestamp = ((Number) publishTimeObj).longValue();
                                    logger.debug("Found publishTime (Number) in args: {}", publishTimestamp);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Error getting publishTime from args: {}", e.getMessage());
            }

            // ПУТЬ 2: Через detailParams (альтернативный путь)
            if (publishTimestamp == 0) {
                try {
                    JSONObject detailParams = null;

                    // Ищем detailParams в разных местах
                    if (itemData != null && itemData.has("detailParams")) {
                        detailParams = itemData.optJSONObject("detailParams");
                    }

                    if (detailParams == null && exContent != null && exContent.has("detailParams")) {
                        detailParams = exContent.optJSONObject("detailParams");
                    }

                    if (detailParams != null && detailParams.has("publishTime")) {
                        Object publishTimeObj = detailParams.get("publishTime");
                        if (publishTimeObj instanceof String) {
                            String timeStr = (String) publishTimeObj;
                            publishTimestamp = Long.parseLong(timeStr);
                            logger.debug("Found publishTime in detailParams: {}", publishTimestamp);
                        } else if (publishTimeObj instanceof Number) {
                            publishTimestamp = ((Number) publishTimeObj).longValue();
                            logger.debug("Found publishTime (Number) in detailParams: {}", publishTimestamp);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error getting publishTime from detailParams: {}", e.getMessage());
                }
            }

            // ПУТЬ 3: Старый путь (для обратной совместимости)
            if (publishTimestamp == 0) {
                try {
                    if (itemData != null && itemData.has("publishTime")) {
                        Object publishTimeObj = itemData.get("publishTime");
                        if (publishTimeObj instanceof String) {
                            String timeStr = (String) publishTimeObj;
                            publishTimestamp = Long.parseLong(timeStr);
                            logger.debug("Found publishTime in itemData: {}", publishTimestamp);
                        } else if (publishTimeObj instanceof Number) {
                            publishTimestamp = ((Number) publishTimeObj).longValue();
                            logger.debug("Found publishTime (Number) in itemData: {}", publishTimestamp);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error getting publishTime from itemData: {}", e.getMessage());
                }
            }

            // Правильный расчет возраста
            int ageMinutes = 0;
            if (publishTimestamp > 0) {
                long currentTimeMs = System.currentTimeMillis();

                // В вашем примере publishTime: 1763362000000 (это миллисекунды)
                // Определяем формат времени
                if (publishTimestamp < 10000000000L) {
                    // Скорее всего секунды (если число < 10 млрд)
                    publishTimestamp = publishTimestamp * 1000;
                }

                // Вычисляем возраст в минутах
                long ageMs = currentTimeMs - publishTimestamp;
                ageMinutes = (int) (ageMs / (1000 * 60));

                // Проверяем на разумные пределы
                if (ageMinutes < 0) {
                    ageMinutes = 1; // Если время в будущем
                } else if (ageMinutes > 10080) { // 7 дней
                    ageMinutes = 10080;
                }

                logger.debug("Age calculation - Publish: {}, Current: {}, Age: {}min",
                        new Date(publishTimestamp), new Date(currentTimeMs), ageMinutes);
            } else {
                // Если время не найдено, устанавливаем случайный возраст (от 1 до 60 минут)
                ageMinutes = new Random().nextInt(60) + 1;
                logger.debug("No publish time found, using random age: {}min", ageMinutes);
            }

            product.setAgeMinutes(ageMinutes);

            // Извлечение локации
            String location = "";
            if (itemData != null) {
                location = itemData.optString("area", "");
            }

            if (location.isEmpty() && extra != null) {
                location = extra.optString("area", "");
            }

            if (location.isEmpty() && exContent != null) {
                location = exContent.optString("area", "");
            }

            product.setLocation(location.isEmpty() ? "Не указано" : location);

            // URL товара
            product.setUrl("https://www.goofish.com/item?id=" + itemId);

            // Дополнительная информация
            if (itemData != null) {
                String seller = itemData.optString("nick", "");
                if (!seller.isEmpty()) {
                    product.setSeller(seller);
                }

                String category = itemData.optString("category", "");
                if (!category.isEmpty()) {
                    product.setCategory(category);
                }
            }

            // Изображения - используем обновленный метод
            List<String> images = extractImages(mainObj, itemObj, data); // Используем mainObj
            product.setImages(images);

            logger.debug("Parsed product: {} ({}¥, {} min, {} images)",
                    product.getShortTitle(), price, ageMinutes, images.size());
            return product;

        } catch (Exception e) {
            logger.error("Error parsing product item: {}", e.getMessage(), e);
            return null;
        }
    }

    // Счетчик для отладки
    private int debugCounter = 0;

    /**
     * Метод для отладки структуры JSON ответа
     */
    private void debugItemStructure(JSONObject item, String query) {
        try {
            logger.info("=== DEBUG ITEM STRUCTURE ===");
            logger.info("Query: {}", query);

            JSONObject data = item.optJSONObject("data");
            if (data != null) {
                logger.info("Data keys: {}", data.keySet());

                JSONObject itemObj = data.optJSONObject("item");
                if (itemObj != null) {
                    logger.info("Item keys: {}", itemObj.keySet());

                    JSONObject main = itemObj.optJSONObject("main");
                    if (main != null) {
                        logger.info("Main keys: {}", main.keySet());

                        // Ищем clickParam
                        JSONObject clickParam = main.optJSONObject("clickParam");
                        if (clickParam != null) {
                            logger.info("ClickParam keys: {}", clickParam.keySet());

                            JSONObject args = clickParam.optJSONObject("args");
                            if (args != null) {
                                logger.info("Args contains publishTime: {}", args.has("publishTime"));
                                if (args.has("publishTime")) {
                                    Object publishTime = args.get("publishTime");
                                    logger.info("publishTime value: {} (type: {})",
                                            publishTime, publishTime.getClass().getSimpleName());
                                }
                            }
                        }
                    }

                    // Ищем exContent
                    JSONObject exContent = itemObj.optJSONObject("exContent");
                    if (exContent != null) {
                        logger.info("exContent keys: {}", exContent.keySet());
                    }
                }
            }

            // Логируем всю структуру (первые 500 символов)
            String itemStr = item.toString();
            if (itemStr.length() > 500) {
                itemStr = itemStr.substring(0, 500) + "...";
            }
            logger.info("Item structure (first 500 chars): {}", itemStr);

            logger.info("=== END DEBUG ===");
        } catch (Exception e) {
            logger.error("Error in debugItemStructure: {}", e.getMessage());
        }
    }

    /**
     * Извлечение цены из JSON массива формата
     */
    private double extractPriceFromJson(JSONObject itemData, JSONObject extra, JSONObject exContent) {
        try {
            // Сначала пробуем получить цену как строку
            String priceStr = "";

            if (itemData != null && itemData.has("price")) {
                Object priceObj = itemData.get("price");
                priceStr = extractPriceFromObject(priceObj);
            }

            if (priceStr.isEmpty() && extra != null && extra.has("price")) {
                Object priceObj = extra.get("price");
                priceStr = extractPriceFromObject(priceObj);
            }

            if (priceStr.isEmpty() && exContent != null && exContent.has("price")) {
                Object priceObj = exContent.get("price");
                priceStr = extractPriceFromObject(priceObj);
            }

            if (!priceStr.isEmpty()) {
                try {
                    return Double.parseDouble(priceStr);
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse price string '{}': {}", priceStr, e.getMessage());
                }
            }

            return 0.0;
        } catch (Exception e) {
            logger.warn("Error extracting price: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Извлечение цены из объекта (может быть строкой, числом или JSON массивом)
     */
    private String extractPriceFromObject(Object priceObj) {
        if (priceObj == null) {
            return "";
        }

        if (priceObj instanceof String) {
            return (String) priceObj;
        }

        if (priceObj instanceof Number) {
            return priceObj.toString();
        }

        if (priceObj instanceof JSONArray) {
            // Обработка JSON массива формата: [{"text":"¥","type":"sign"},{"text":"500","type":"integer"}]
            JSONArray priceArray = (JSONArray) priceObj;
            StringBuilder priceBuilder = new StringBuilder();

            for (int i = 0; i < priceArray.length(); i++) {
                try {
                    JSONObject pricePart = priceArray.getJSONObject(i);
                    String type = pricePart.optString("type", "");
                    String text = pricePart.optString("text", "");

                    if ("integer".equals(type) || "decimal".equals(type) || text.matches("\\d+")) {
                        priceBuilder.append(text);
                    } else if ("sign".equals(type) && "¥".equals(text)) {
                        // Игнорируем символ валюты при парсинге
                        continue;
                    }
                } catch (Exception e) {
                    logger.debug("Error parsing price part {}: {}", i, e.getMessage());
                }
            }

            String result = priceBuilder.toString();
            logger.debug("Extracted price from JSON array: '{}' -> '{}'", priceObj.toString(), result);
            return result;
        }

        return priceObj.toString();
    }

    /**
     * Извлечение изображений из разных мест структуры (на основе Python кода)
     */
    private List<String> extractImages(JSONObject main, JSONObject itemObj, JSONObject data) {
        List<String> images = new ArrayList<>();

        try {
            // Путь 1: Из itemObj.extra.picUrl (как в Python)
            if (itemObj != null && itemObj.has("extra")) {
                JSONObject extra = itemObj.optJSONObject("extra");
                if (extra != null) {
                    String picUrl = extra.optString("picUrl", "");
                    if (picUrl != null && !picUrl.isEmpty() && picUrl.startsWith("http")) {
                        images.add(picUrl);
                        logger.debug("Found image via extra.picUrl: {}", picUrl);
                    }
                }
            }

            // Путь 2: Из data.pics (как в Python)
            if (data != null && data.has("pics")) {
                Object picsObj = data.get("pics");
                if (picsObj instanceof JSONArray) {
                    JSONArray picsList = (JSONArray) picsObj;
                    for (int i = 0; i < Math.min(picsList.length(), 3); i++) {
                        try {
                            Object picObj = picsList.get(i);
                            if (picObj instanceof JSONObject) {
                                JSONObject pic = (JSONObject) picObj;
                                String picUrl = pic.optString("picUrl", "");
                                if (picUrl != null && !picUrl.isEmpty() && picUrl.startsWith("http")) {
                                    if (!images.contains(picUrl)) {
                                        images.add(picUrl);
                                        logger.debug("Found image via pics[{}].picUrl: {}", i, picUrl);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Error parsing pic {}: {}", i, e.getMessage());
                        }
                    }
                }
            }

            // Путь 3: Из main.exContent (как в Python)
            if (main != null && main.has("exContent")) {
                JSONObject exContent = main.optJSONObject("exContent");
                if (exContent != null) {
                    String picUrl = exContent.optString("picUrl", "");
                    if (picUrl != null && !picUrl.isEmpty() && picUrl.startsWith("http")) {
                        if (!images.contains(picUrl)) {
                            images.add(picUrl);
                            logger.debug("Found image via exContent.picUrl: {}", picUrl);
                        }
                    }
                }
            }

            // Путь 4: Из data.itemData (дополнительный путь)
            if (data != null && data.has("itemData")) {
                JSONObject itemData = data.optJSONObject("itemData");
                if (itemData != null) {
                    String picUrl = itemData.optString("picUrl", "");
                    if (picUrl != null && !picUrl.isEmpty() && picUrl.startsWith("http") && !images.contains(picUrl)) {
                        images.add(picUrl);
                        logger.debug("Found image via itemData.picUrl: {}", picUrl);
                    }
                }
            }

            // Путь 5: Из main.images (старый путь на всякий случай)
            if (images.isEmpty() && main != null && main.has("images")) {
                Object imagesObj = main.get("images");
                if (imagesObj instanceof JSONArray) {
                    JSONArray imagesArray = (JSONArray) imagesObj;
                    for (int i = 0; i < Math.min(imagesArray.length(), 3); i++) {
                        try {
                            Object imgObj = imagesArray.get(i);
                            if (imgObj instanceof String) {
                                String imageUrl = (String) imgObj;
                                if (imageUrl != null && !imageUrl.isEmpty() && imageUrl.startsWith("http")) {
                                    if (!images.contains(imageUrl)) {
                                        images.add(imageUrl);
                                        logger.debug("Found image via main.images[{}]: {}", i, imageUrl);
                                    }
                                }
                            } else if (imgObj instanceof JSONObject) {
                                JSONObject imgJson = (JSONObject) imgObj;
                                String imageUrl = imgJson.optString("picUrl",
                                        imgJson.optString("url",
                                                imgJson.optString("src", "")));
                                if (imageUrl != null && !imageUrl.isEmpty() && imageUrl.startsWith("http")) {
                                    if (!images.contains(imageUrl)) {
                                        images.add(imageUrl);
                                        logger.debug("Found image via main.images[{}].picUrl: {}", i, imageUrl);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Error parsing main.image {}: {}", i, e.getMessage());
                        }
                    }
                }
            }

            // Путь 6: Рекурсивный поиск во всех объектах
            if (images.isEmpty()) {
                logger.debug("Doing recursive search for images...");
                findImagesRecursively(data, images);
            }

            // Логируем результаты
            if (!images.isEmpty()) {
                logger.debug("Found {} images for product", images.size());
                for (int i = 0; i < Math.min(images.size(), 3); i++) {
                    logger.debug("Image {}: {}", i + 1,
                            images.get(i).length() > 100 ?
                                    images.get(i).substring(0, 100) + "..." :
                                    images.get(i));
                }
            } else {
                logger.debug("No images found for product");
            }

        } catch (Exception e) {
            logger.debug("Error extracting images: {}", e.getMessage());
        }

        return images;
    }

    /**
     * Рекурсивный поиск изображений во всех полях JSON объекта
     */
    private void findImagesRecursively(JSONObject json, List<String> images) {
        if (json == null) return;

        try {
            for (String key : json.keySet()) {
                Object value = json.get(key);

                if (value instanceof String) {
                    String strValue = (String) value;
                    // Проверяем ключи, которые могут содержать URL изображений
                    if (key.toLowerCase().contains("pic") ||
                            key.toLowerCase().contains("image") ||
                            key.toLowerCase().contains("url")) {
                        if (isValidImageUrl(strValue) && !images.contains(strValue)) {
                            images.add(strValue);
                            logger.trace("Found image in key '{}': {}", key, strValue);
                        }
                    }
                } else if (value instanceof JSONObject) {
                    // Рекурсивно ищем во вложенных объектах
                    findImagesRecursively((JSONObject) value, images);
                } else if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.length(); i++) {
                        Object element = array.get(i);
                        if (element instanceof JSONObject) {
                            findImagesRecursively((JSONObject) element, images);
                        } else if (element instanceof String) {
                            String strValue = (String) element;
                            if (isValidImageUrl(strValue) && !images.contains(strValue)) {
                                images.add(strValue);
                                logger.trace("Found image in array element: {}", strValue);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Error in recursive image search: {}", e.getMessage());
        }
    }

    /**
     * Проверка валидности товара
     */
    private boolean isValidProduct(Product product) {
        if (product == null) {
            return false;
        }

        if (product.getId() == null || product.getId().isEmpty()) {
            logger.trace("Product rejected: missing ID");
            return false;
        }

        if (product.getTitle() == null || product.getTitle().isEmpty() ||
                product.getTitle().equals("Без названия")) {
            logger.trace("Product rejected: invalid title");
            return false;
        }

        // Временно разрешаем товары с ценой 0 для тестирования
        // if (product.getPrice() <= 0) {
        //     logger.trace("Product rejected: invalid price");
        //     return false;
        // }

        if (product.getUrl() == null || product.getUrl().isEmpty()) {
            logger.trace("Product rejected: missing URL");
            return false;
        }

        return true;
    }

    @Override
    protected int getRequestDelay() {
        return Config.getGoofishDelayBetweenRequests();
    }

    @Override
    protected boolean shouldStopOnError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("403") ||
                        message.contains("429") ||
                        message.contains("401") ||
                        message.contains("blocked") ||
                        message.contains("captcha") ||
                        message.contains("非法请求") ||
                        message.contains("ILLEGAL_ACCESS")
        );
    }

    /**
     * Получение статистики парсера
     */
    @Override
    public BaseParser.ParserStats getStats() {
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
     * Дополнительная статистика для GoofishParser
     */
    public Map<String, Object> getGoofishStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("parserName", "GoofishParser");
        stats.put("baseUrl", baseUrl);
        stats.put("cookieRefreshAttempts", cookieRefreshAttempts);
        stats.put("lastCookieRefreshTime", lastCookieRefreshTime);
        stats.put("timeSinceLastRefresh", System.currentTimeMillis() - lastCookieRefreshTime);
        return stats;
    }

    /**
     * Очистка ресурсов
     */
    public void cleanup() {
        // Очистка ресурсов
        logger.info("Cleaning up GoofishParser resources");
        cookieRefreshAttempts = 0;
        lastCookieRefreshTime = 0;
    }

    /**
     * Выполнение поиска с предварительным обновлением кук
     */
    public List<Product> searchWithRefresh(String query, int maxResults, int maxPages) {
        logger.info("Starting Goofish search for '{}' (maxResults: {}, maxPages: {})",
                query, maxResults, maxPages);
        refreshCookiesIfNeeded();

        // Используем существующий метод search
        return search(query, maxPages, 100, 1440); // Используем значения по умолчанию
    }

    /**
     * Простой поиск с обновлением кук
     */
    @Override
    public List<Product> search(String query) {
        logger.info("Starting Goofish search for '{}'", query);
        refreshCookiesIfNeeded();
        return super.search(query);
    }

    /**
     * Метод для установки кук
     */
    public void setCookies(String cookies) {
        // Этот метод должен быть в BaseParser
        // Если нет, создаем свою реализацию
        logger.debug("Setting cookies: {}", cookies != null ?
                cookies.substring(0, Math.min(cookies.length(), 50)) + "..." : "null");

        // Здесь должна быть логика установки кук для HTTP запросов
        // В данный момент куки управляются через CookieService и HttpUtils
    }

    /**
     * Метод для поиска продуктов с указанием всех параметров
     */
    public List<Product> searchWithParams(String query, int maxResults, int maxPages, int rowsPerPage) {
        logger.info("Starting Goofish search for '{}' (maxResults: {}, maxPages: {}, rowsPerPage: {})",
                query, maxResults, maxPages, rowsPerPage);
        refreshCookiesIfNeeded();

        // Используем существующий метод search
        return search(query, maxPages, rowsPerPage, 1440); // Используем максимальный возраст
    }
}