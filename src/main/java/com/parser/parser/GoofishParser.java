package com.parser.parser;

import com.parser.config.Config;
import com.parser.model.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Парсер для сайта Goofish (闲鱼)
 */
public class GoofishParser extends BaseParser {
    private static final Logger logger = LoggerFactory.getLogger(GoofishParser.class);

    // API endpoints
    private static final String SEARCH_ENDPOINT = "/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/";
    private static final String ITEM_DETAIL_ENDPOINT = "/h5/mtop.taobao.detail.getdetail/1.0/";

    // Headers
    private static final String REFERER = "https://h5.m.goofish.com/";
    private static final String ACCEPT = "application/json";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";

    public GoofishParser() {
        super("goofish", Config.getString("api.goofish.base_url", "https://h5api.m.goofish.com"));
    }

    @Override
    protected String buildSearchUrl(String query, int page, int rows) {
        try {
            // Кодирование запроса
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

            // Построение URL с параметрами
            StringBuilder url = new StringBuilder(baseUrl);
            url.append(SEARCH_ENDPOINT);
            url.append("?keyword=").append(encodedQuery);
            url.append("&pageNumber=").append(page);
            url.append("&rowsPerPage=").append(Math.min(rows, 500));
            url.append("&sortType=default");
            url.append("&searchSource=search");
            url.append("&_t=").append(System.currentTimeMillis());

            logger.debug("Built search URL for query '{}', page {}: {}", query, page, url);
            return url.toString();

        } catch (Exception e) {
            logger.error("Error building search URL: {}", e.getMessage(), e);
            return "";
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
            JSONObject json = new JSONObject(response);

            // Проверка статуса ответа
            String status = json.optString("status", "error");
            if (!"SUCCESS".equals(status)) {
                logger.error("API returned error status: {}", status);
                return products;
            }

            // Получение данных
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                logger.warn("No data in response");
                return products;
            }

            // Получение списка товаров
            JSONArray resultList = data.optJSONArray("resultList");
            if (resultList == null || resultList.length() == 0) {
                logger.info("No products found in response");
                return products;
            }

            logger.debug("Found {} items in response", resultList.length());

            // Парсинг каждого товара
            for (int i = 0; i < resultList.length(); i++) {
                try {
                    JSONObject item = resultList.getJSONObject(i);
                    Product product = parseProductItem(item, query);

                    if (product != null && isValidProduct(product)) {
                        products.add(product);
                        logger.trace("Parsed product: {}", product.getShortTitle());
                    }

                } catch (Exception e) {
                    logger.warn("Error parsing item {}: {}", i, e.getMessage());
                    continue;
                }
            }

            logger.info("Successfully parsed {} products from response", products.size());

        } catch (Exception e) {
            logger.error("Error parsing Goofish response: {}", e.getMessage(), e);
        }

        return products;
    }

    /**
     * Парсинг отдельного товара
     */
    private Product parseProductItem(JSONObject item, String query) {
        try {
            // Извлечение основных данных
            JSONObject data = item.optJSONObject("data");
            if (data == null) {
                logger.trace("No data object in item");
                return null;
            }

            JSONObject itemData = data.optJSONObject("item");
            if (itemData == null) {
                logger.trace("No item object in data");
                return null;
            }

            // Получение ID товара
            String id = extractItemId(itemData);
            if (id == null || id.isEmpty()) {
                logger.trace("No item ID found");
                return null;
            }

            // Получение основной информации
            JSONObject main = itemData.optJSONObject("main");
            JSONObject clickParam = main != null ? main.optJSONObject("clickParam") : null;
            JSONObject args = clickParam != null ? clickParam.optJSONObject("args") : null;

            // Получение дополнительной информации
            JSONObject extra = itemData.optJSONObject("extra");

            // Создание объекта товара
            Product product = new Product();
            product.setId(id);
            product.setSite("goofish");
            product.setQuery(query);

            // Установка названия
            String title = extractTitle(main, args, extra);
            product.setTitle(title);

            // Установка цены
            double price = extractPrice(main, args, extra);
            product.setPrice(price);

            // Установка URL
            product.setUrl(buildProductUrl(id));

            // Установка местоположения
            String location = extractLocation(args, extra);
            product.setLocation(location);

            // Установка возраста товара
            int ageMinutes = extractAgeMinutes(args, extra);
            product.setAgeMinutes(ageMinutes);

            // Установка продавца
            String seller = extractSeller(args, extra);
            product.setSeller(seller);

            // Установка рейтинга продавца
            String sellerRating = extractSellerRating(extra);
            product.setSellerRating(sellerRating);

            // Установка категории
            String category = extractCategory(args, extra);
            product.setCategory(category);

            // Установка изображений
            List<String> images = extractImages(main, extra);
            product.setImages(images);

            logger.debug("Parsed product: {} ({}¥)", product.getShortTitle(), price);
            return product;

        } catch (Exception e) {
            logger.error("Error parsing product item: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Извлечение ID товара
     */
    private String extractItemId(JSONObject itemData) {
        // Попытка получить ID из разных мест
        JSONObject main = itemData.optJSONObject("main");
        if (main != null) {
            JSONObject clickParam = main.optJSONObject("clickParam");
            if (clickParam != null) {
                JSONObject args = clickParam.optJSONObject("args");
                if (args != null) {
                    String id = args.optString("id");
                    if (id != null && !id.isEmpty()) {
                        return id;
                    }
                }
            }
        }

        // Альтернативные пути
        JSONObject extra = itemData.optJSONObject("extra");
        if (extra != null) {
            String id = extra.optString("itemId");
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }

        return null;
    }

    /**
     * Извлечение названия товара
     */
    private String extractTitle(JSONObject main, JSONObject args, JSONObject extra) {
        if (main != null) {
            String title = main.optString("title");
            if (title != null && !title.isEmpty()) {
                return cleanString(title);
            }
        }

        if (args != null) {
            String title = args.optString("title");
            if (title != null && !title.isEmpty()) {
                return cleanString(title);
            }
        }

        if (extra != null) {
            String title = extra.optString("title");
            if (title != null && !title.isEmpty()) {
                return cleanString(title);
            }
        }

        return "Без названия";
    }

    /**
     * Извлечение цены
     */
    private double extractPrice(JSONObject main, JSONObject args, JSONObject extra) {
        // Приоритет 1: из main
        if (main != null) {
            JSONObject priceInfo = main.optJSONObject("priceInfo");
            if (priceInfo != null) {
                String priceStr = priceInfo.optString("price");
                if (priceStr != null && !priceStr.isEmpty()) {
                    return extractPrice(priceStr);
                }
            }
        }

        // Приоритет 2: из args
        if (args != null) {
            String priceStr = args.optString("price");
            if (priceStr != null && !priceStr.isEmpty()) {
                return extractPrice(priceStr);
            }
        }

        // Приоритет 3: из extra
        if (extra != null) {
            String priceStr = extra.optString("price");
            if (priceStr != null && !priceStr.isEmpty()) {
                return extractPrice(priceStr);
            }

            JSONObject priceInfo = extra.optJSONObject("price");
            if (priceInfo != null) {
                priceStr = priceInfo.optString("priceText");
                if (priceStr != null && !priceStr.isEmpty()) {
                    return extractPrice(priceStr);
                }
            }
        }

        return 0.0;
    }

    /**
     * Построение URL товара
     */
    private String buildProductUrl(String id) {
        return "https://h5.m.goofish.com/item?id=" + id;
    }

    /**
     * Извлечение местоположения
     */
    private String extractLocation(JSONObject args, JSONObject extra) {
        if (args != null) {
            String location = args.optString("area");
            if (location != null && !location.isEmpty()) {
                return cleanString(location);
            }
        }

        if (extra != null) {
            String location = extra.optString("area");
            if (location != null && !location.isEmpty()) {
                return cleanString(location);
            }
        }

        return "Не указано";
    }

    /**
     * Извлечение возраста товара
     */
    private int extractAgeMinutes(JSONObject args, JSONObject extra) {
        // Попытка получить время публикации
        long publishTime = 0;

        if (args != null) {
            publishTime = args.optLong("publishTime", 0);
        }

        if (publishTime == 0 && extra != null) {
            publishTime = extra.optLong("publishTime", 0);
        }

        if (publishTime > 0) {
            return calculateAgeMinutes(publishTime);
        }

        // Альтернатива: из строки времени
        if (extra != null) {
            String timeText = extra.optString("timeText");
            if (timeText != null && !timeText.isEmpty()) {
                return parseTimeText(timeText);
            }
        }

        return 0;
    }

    /**
     * Парсинг текстового представления времени
     */
    private int parseTimeText(String timeText) {
        try {
            timeText = timeText.toLowerCase();

            if (timeText.contains("刚刚") || timeText.contains("just")) {
                return 1; // Только что
            }

            if (timeText.contains("分钟") || timeText.contains("min")) {
                String numStr = timeText.replaceAll("[^\\d]", "");
                if (!numStr.isEmpty()) {
                    return Integer.parseInt(numStr);
                }
            }

            if (timeText.contains("小时") || timeText.contains("hour")) {
                String numStr = timeText.replaceAll("[^\\d]", "");
                if (!numStr.isEmpty()) {
                    return Integer.parseInt(numStr) * 60;
                }
            }

            if (timeText.contains("天") || timeText.contains("day")) {
                String numStr = timeText.replaceAll("[^\\d]", "");
                if (!numStr.isEmpty()) {
                    return Integer.parseInt(numStr) * 1440;
                }
            }

        } catch (NumberFormatException e) {
            logger.warn("Failed to parse time text: {}", timeText);
        }

        return 0;
    }

    /**
     * Извлечение информации о продавце
     */
    private String extractSeller(JSONObject args, JSONObject extra) {
        if (args != null) {
            String seller = args.optString("nick");
            if (seller != null && !seller.isEmpty()) {
                return cleanString(seller);
            }
        }

        if (extra != null) {
            String seller = extra.optString("sellerNick");
            if (seller != null && !seller.isEmpty()) {
                return cleanString(seller);
            }
        }

        return "Не указан";
    }

    /**
     * Извлечение рейтинга продавца
     */
    private String extractSellerRating(JSONObject extra) {
        if (extra != null) {
            JSONObject sellerInfo = extra.optJSONObject("sellerInfo");
            if (sellerInfo != null) {
                String rating = sellerInfo.optString("rate");
                if (rating != null && !rating.isEmpty()) {
                    return rating;
                }
            }
        }

        return "Нет рейтинга";
    }

    /**
     * Извлечение категории товара
     */
    private String extractCategory(JSONObject args, JSONObject extra) {
        if (args != null) {
            String category = args.optString("category");
            if (category != null && !category.isEmpty()) {
                return cleanString(category);
            }
        }

        if (extra != null) {
            String category = extra.optString("categoryName");
            if (category != null && !category.isEmpty()) {
                return cleanString(category);
            }
        }

        return "Другое";
    }

    /**
     * Извлечение изображений товара
     */
    private List<String> extractImages(JSONObject main, JSONObject extra) {
        List<String> images = new ArrayList<>();

        // Попытка получить из main
        if (main != null) {
            JSONArray imageArray = main.optJSONArray("images");
            if (imageArray != null) {
                for (int i = 0; i < imageArray.length(); i++) {
                    String imageUrl = imageArray.optString(i);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        images.add(imageUrl);
                        if (images.size() >= 5) break; // Ограничение на количество
                    }
                }
            }
        }

        // Попытка получить из extra
        if (extra != null && images.isEmpty()) {
            String imageUrl = extra.optString("picUrl");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                images.add(imageUrl);
            }
        }

        return images;
    }

    /**
     * Проверка валидности товара
     */
    private boolean isValidProduct(Product product) {
        if (product == null) {
            return false;
        }

        // Проверка обязательных полей
        if (product.getId() == null || product.getId().isEmpty()) {
            logger.trace("Product rejected: missing ID");
            return false;
        }

        if (product.getTitle() == null || product.getTitle().isEmpty() ||
                product.getTitle().equals("Без названия")) {
            logger.trace("Product rejected: invalid title");
            return false;
        }

        if (product.getPrice() <= 0) {
            logger.trace("Product rejected: invalid price");
            return false;
        }

        if (product.getUrl() == null || product.getUrl().isEmpty()) {
            logger.trace("Product rejected: missing URL");
            return false;
        }

        // Дополнительные проверки
        if (product.getAgeMinutes() < 0) {
            logger.trace("Product rejected: negative age");
            return false;
        }

        return true;
    }

    @Override
    protected int getRequestDelay() {
        return Config.getInt("api.goofish.delay.between.requests", 2000);
    }

    @Override
    protected boolean shouldStopOnError(Exception e) {
        // Прекращаем поиск при определенных ошибках
        String message = e.getMessage();
        return message != null && (
                message.contains("403") || // Forbidden
                        message.contains("429") || // Too Many Requests
                        message.contains("blocked") ||
                        message.contains("captcha")
        );
    }
}