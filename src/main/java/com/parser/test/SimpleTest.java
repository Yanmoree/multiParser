package com.parser.test;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

public class SimpleTest {
    public static void main(String[] args) throws Exception {
        // Простой GET запрос на сайт
        String url = "https://www.goofish.com/search?q=stone%20island&spm=a21ybx.search.searchInput.0";

        HttpGet request = new HttpGet(url);
        request.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");

        try (var response = org.apache.http.impl.client.HttpClients.createDefault().execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            System.out.println("Статус: " + response.getStatusLine().getStatusCode());
            System.out.println("Длина ответа: " + body.length());
            System.out.println("Первые 500 символов:");
            System.out.println(body.substring(0, Math.min(500, body.length())));
        }
    }
}