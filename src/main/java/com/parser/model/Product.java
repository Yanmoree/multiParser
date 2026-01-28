package com.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.parser.config.ParserSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс, представляющий товар
 */
public class Product {
    private String id;
    private String title;
    private double price;
    private String url;
    private String site;
    private String location;
    private int ageMinutes;
    private String query;
    private List<String> images;
    private String seller;
    private String sellerRating;
    private String category;
    private boolean isNew;
    private long foundTimestamp;

    public Product() {
        this.images = new ArrayList<>();
        this.isNew = true;
        this.foundTimestamp = System.currentTimeMillis();
    }

    public Product(String id, String title, double price, String url, String site,
                   String location, int ageMinutes, String query) {
        this();
        this.id = id;
        this.title = title;
        this.price = price;
        this.url = url;
        this.site = site;
        this.location = location;
        this.ageMinutes = ageMinutes;
        this.query = query;
    }

    // Геттеры и сеттеры

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null) {
            this.title = "Без названия";
        } else if (title.length() > 200) {
            this.title = title.substring(0, 197) + "...";
        } else {
            this.title = title.trim();
        }
    }

    @JsonProperty("price")
    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = Math.max(0, price);
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("site")
    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    @JsonProperty("location")
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @JsonProperty("ageMinutes")
    public int getAgeMinutes() {
        return ageMinutes;
    }

    public void setAgeMinutes(int ageMinutes) {
        this.ageMinutes = Math.max(0, ageMinutes);
    }

    @JsonProperty("query")
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @JsonProperty("images")
    public List<String> getImages() {
        return new ArrayList<>(images);
    }

    public void setImages(List<String> images) {
        this.images = new ArrayList<>(images);
    }

    public void addImage(String image) {
        if (image != null && !image.trim().isEmpty()) {
            this.images.add(image.trim());
        }
    }

    @JsonProperty("seller")
    public String getSeller() {
        return seller;
    }

    public void setSeller(String seller) {
        this.seller = seller;
    }

    @JsonProperty("sellerRating")
    public String getSellerRating() {
        return sellerRating;
    }

    public void setSellerRating(String sellerRating) {
        this.sellerRating = sellerRating;
    }

    @JsonProperty("category")
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @JsonProperty("isNew")
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    @JsonProperty("foundTimestamp")
    public long getFoundTimestamp() {
        return foundTimestamp;
    }

    public void setFoundTimestamp(long foundTimestamp) {
        this.foundTimestamp = foundTimestamp;
    }

    // Методы для конвертации цены

    @JsonIgnore
    public double getPriceRubles() {
        return Math.round(price * ParserSettings.getCurrencyRate(
                ParserSettings.CURRENCY_YUAN, ParserSettings.CURRENCY_RUBLES) * 100.0) / 100.0;
    }

    @JsonIgnore
    public String getPriceDisplay() {
        return String.format("%.2f ¥", price);
    }

    @JsonIgnore
    public String getPriceDisplayRub() {
        return String.format("%.2f руб.", getPriceRubles());
    }

    @JsonIgnore
    public String getFormattedPrice(String currency) {
        if (ParserSettings.CURRENCY_RUBLES.equals(currency)) {
            return getPriceDisplayRub();
        } else {
            return getPriceDisplay();
        }
    }

    // Методы для удобства

    @JsonIgnore
    public String getShortTitle() {
        if (title == null || title.isEmpty() || "No title".equals(title)) {
            return "Товар #" + id;
        }

        if (title.length() <= 80) { // Уменьшаем лимит для более аккуратного отображения
            return title;
        }
        return title.substring(0, 77) + "...";
    }

    @JsonIgnore
    public String getAgeDisplay() {
        if (ageMinutes < 60) {
            return ageMinutes + " мин";
        } else if (ageMinutes < 1440) {
            return (ageMinutes / 60) + " ч";
        } else {
            return (ageMinutes / 1440) + " дн";
        }
    }

    @JsonIgnore
    public boolean hasImages() {
        return !images.isEmpty();
    }

    @JsonIgnore
    public String getMainImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    // НОВЫЙ МЕТОД: Получение URL обложки (первого изображения)
    @JsonIgnore
    public String getCoverImageUrl() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return null;
    }

    // НОВЫЙ МЕТОД: Проверка наличия обложки
    @JsonIgnore
    public boolean hasCoverImage() {
        return getCoverImageUrl() != null;
    }

    // Методы для сериализации

    @JsonIgnore
    public String toJsonString() {
        return String.format(
                "{\"id\":\"%s\",\"title\":\"%s\",\"price\":%.2f,\"url\":\"%s\",\"age\":%d}",
                id, title.replace("\"", "\\\""), price, url, ageMinutes
        );
    }

    @JsonIgnore
    public String toCsvString() {
        return String.format("%s,\"%s\",%.2f,%s,%s,%d",
                id, title.replace("\"", "\"\""), price, url, location, ageMinutes);
    }

    @Override
    public String toString() {
        return String.format("Product{id='%s', title='%s', price=%.2f¥, age=%dmin, location=%s}",
                id, getShortTitle(), price, ageMinutes, location);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Product product = (Product) obj;
        return id != null && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}