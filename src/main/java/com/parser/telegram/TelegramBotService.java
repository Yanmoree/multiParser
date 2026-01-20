package com.parser.telegram;

import com.parser.config.Config;
import com.parser.core.ThreadManager;
import com.parser.model.UserSettings;
import com.parser.service.CookieService;
import com.parser.storage.WhitelistManager;
import com.parser.storage.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

/**
 * Telegram бот сервис - рефакторированный и оптимизированный
 */
public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private final ThreadManager threadManager;
    private final long adminId;
    private final Map<Long, String> userStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Map<String, String>> userData = new java.util.concurrent.ConcurrentHashMap<>();

    public TelegramBotService(String token, ThreadManager threadManager) {
        super(token);
        this.threadManager = threadManager;
        this.adminId = Config.getTelegramAdminId();
        logger.info("TelegramBotService initialized");
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            }
        } catch (Exception e) {
            logger.error("Error processing update: {}", e.getMessage());
        }
    }

    private void handleMessage(org.telegram.telegrambots.meta.api.objects.Message msg) {
        long userId = msg.getChatId();
        String text = msg.getText();

        logger.debug("Message from {}: {}", userId, text);

        if (text.startsWith("/")) {
            handleCommand(userId, text);
        } else {
            handleTextResponse(userId, text);
        }
    }

    private void handleCommand(long userId, String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "/start":
                handleStart(userId);
                break;
            case "/help":
                sendHelpMessage(userId);
                break;
            case "/status":
                sendStatus(userId);
                break;
            case "/addquery":
                handleAddQuery(userId, args);
                break;
            case "/listqueries":
                handleListQueries(userId);
                break;
            case "/removequery":
                handleRemoveQuery(userId, args);
                break;
            case "/settings":
                sendSettingsMenu(userId);
                break;
            case "/start_parser":
                threadManager.startUserParser(userId);
                break;
            case "/stop_parser":
                threadManager.stopUserParser(userId);
                break;
            case "/stats":
                sendStats(userId);
                break;
            case "/cookies":
                handleCookiesCommand(userId, args);
                break;
            case "/admin":
                handleAdminCommand(userId, args);
                break;
            case "/getid":
                sendMessage(userId, "🆔 Your ID: `" + userId + "`");
                break;
            default:
                sendMessage(userId, "❓ Unknown command. Use /help");
        }
    }

    private void handleTextResponse(long userId, String text) {
        String state = userStates.get(userId);
        if (state == null) {
            sendMessage(userId, "Use commands. /help - command list");
            return;
        }

        switch (state) {
            case "AWAITING_QUERY":
                if (UserDataManager.addUserQuery(userId, text.trim())) {
                    sendMessage(userId, "✅ Query added: " + text);
                } else {
                    sendMessage(userId, "⚠️ Query already exists");
                }
                userStates.remove(userId);
                break;
            case "AWAITING_SETTING_VALUE":
                handleSettingUpdate(userId, text);
                break;
        }
    }

    private void handleCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery callback) {
        long userId = callback.getMessage().getChatId();
        String data = callback.getData();

        logger.debug("Callback from {}: {}", userId, data);

        try {
            execute(new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery(callback.getId()));
        } catch (TelegramApiException e) {
            logger.warn("Failed to answer callback: {}", e.getMessage());
        }
    }

    private void handleStart(long userId) {
        // 🔴 ДОБАВЛЯЕМ В WHITELIST
        boolean isNew = WhitelistManager.addUser(userId);

        String msg;
        if (isNew) {
            msg = "🎉 Welcome! Your ID: " + userId + "\n\n" +
                    "Bot features:\n" +
                    "• Add search queries with /addquery\n" +
                    "• Start parser with /start_parser\n" +
                    "• View results with /stats\n\n" +
                    "Use /help for all commands";
        } else {
            msg = "👋 Welcome back!\n\nUse /help for commands";
        }

        sendMessage(userId, msg);
    }

    private void handleAddQuery(long userId, String query) {
        // 🔴 ПРОВЕРКА WHITELIST
        if (!WhitelistManager.isUserAllowed(userId)) {
            sendMessage(userId, "❌ You are not authorized");
            return;
        }

        if (query.trim().isEmpty()) {
            userStates.put(userId, "AWAITING_QUERY");
            sendMessage(userId, "Enter search query:");
            return;
        }

        if (UserDataManager.addUserQuery(userId, query.trim())) {
            sendMessage(userId, "✅ Query added: " + query);
        } else {
            sendMessage(userId, "⚠️ Query already exists or limit reached");
        }
    }

    private void handleListQueries(long userId) {
        // 🔴 ПРОВЕРКА WHITELIST
        if (!WhitelistManager.isUserAllowed(userId)) {
            sendMessage(userId, "❌ You are not authorized");
            return;
        }

        List<String> queries = UserDataManager.getUserQueries(userId);
        if (queries.isEmpty()) {
            sendMessage(userId, "📭 No queries. Use /addquery");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 Your queries:\n\n");
        for (int i = 0; i < queries.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, queries.get(i)));
        }

        sendMessage(userId, sb.toString());
    }

    private void handleRemoveQuery(long userId, String arg) {
        // 🔴 ПРОВЕРКА WHITELIST
        if (!WhitelistManager.isUserAllowed(userId)) {
            sendMessage(userId, "❌ You are not authorized");
            return;
        }

        try {
            if (arg.trim().isEmpty()) {
                sendMessage(userId, "Use: /removequery [number]");
                return;
            }

            int idx = Integer.parseInt(arg.trim()) - 1;
            List<String> queries = UserDataManager.getUserQueries(userId);

            if (idx < 0 || idx >= queries.size()) {
                sendMessage(userId, "❌ Invalid number");
                return;
            }

            UserDataManager.removeUserQuery(userId, queries.get(idx));
            sendMessage(userId, "✅ Query removed");
        } catch (NumberFormatException e) {
            sendMessage(userId, "❌ Invalid format. Use: /removequery [number]");
        }
    }


    private void handleSettingUpdate(long userId, String value) {
        try {
            int intVal = Integer.parseInt(value.trim());
            UserSettings settings = UserDataManager.getUserSettings(userId);
            Map<String, String> data = userData.get(userId);

            if (data != null && data.containsKey("setting_key")) {
                String key = data.get("setting_key");
                switch (key) {
                    case "check_interval":
                        settings.setCheckInterval(intVal);
                        break;
                    case "max_age":
                        settings.setMaxAgeMinutes(intVal);
                        break;
                    case "max_pages":
                        settings.setMaxPages(intVal);
                        break;
                    case "rows_per_page":
                        settings.setRowsPerPage(intVal);
                        break;
                }
                UserDataManager.saveUserSettings(userId, settings);
                sendMessage(userId, "✅ Setting saved");
            }
        } catch (NumberFormatException e) {
            sendMessage(userId, "❌ Invalid number");
        } finally {
            userStates.remove(userId);
            userData.remove(userId);
        }
    }

    private void sendHelpMessage(long userId) {
        String help = """
                📚 Commands:
                
                🎯 Queries:
                /addquery [text] - add query
                /listqueries - list queries
                /removequery [number] - remove query
                
                ⚙️ Settings:
                /settings - parser settings
                /stats - statistics
                
                ▶️ Control:
                /start_parser - start
                /stop_parser - stop
                /status - check status
                
                🍪 Admin:
                /cookies - manage cookies
                /admin - admin menu
                
                ℹ️ Info:
                /getid - your ID
                /help - this message
                """;
        sendMessage(userId, help);
    }

    private void sendSettingsMenu(long userId) {
        UserSettings s = UserDataManager.getUserSettings(userId);
        String msg = String.format("""
                ⚙️ Settings:
                • Interval: %d sec
                • Max age: %d min
                • Pages: %d
                • Rows/page: %d
                • Currency: %s
                
                Edit with: /settings [param] [value]
                """, s.getCheckInterval(), s.getMaxAgeMinutes(), s.getMaxPages(),
                s.getRowsPerPage(), s.getPriceCurrency());
        sendMessage(userId, msg);
    }

    private void sendStatus(long userId) {
        if (!WhitelistManager.isUserAllowed(userId)) {
            sendMessage(userId, "❌ You are not authorized");
            return;
        }

        Map<String, Object> status = threadManager.getUserStatus(userId);
        if (status == null) {
            sendMessage(userId, "🔴 Parser is not running");
            return;
        }

        String msg = String.format(
                "📊 Parser Status:\n\n" +
                        "Status: %s\n" +
                        "Products found: %d\n" +
                        "Requests made: %d\n" +
                        "Errors: %d\n" +
                        "Uptime: %s",
                status.get("status"),
                status.get("totalProductsFound"),
                status.get("requestsMade"),
                status.get("errorsCount"),
                status.getOrDefault("uptime", "N/A")
        );

        sendMessage(userId, msg);
    }

    private void sendStats(long userId) {
        Map<String, Object> global = threadManager.getGlobalStatistics();
        Map<String, Object> user = threadManager.getUserStatus(userId);

        String msg = String.format("""
                📊 Statistics:
                
                Global:
                • Users: %d
                • Products: %d
                • Requests: %d
                • Threads: %d
                
                Your parser: %s
                """, global.get("totalUsers"), global.get("totalProductsFound"),
                global.get("totalRequestsMade"), global.get("activeThreads"),
                user == null ? "Not running" : "Running");
        sendMessage(userId, msg);
    }

    private void handleCookiesCommand(long userId, String args) {
        if (userId != adminId) {
            sendMessage(userId, "❌ Admin only");
            return;
        }

        if (args.isEmpty()) {
            String msg = """
                    🍪 Cookie Management:
                    /cookies refresh - update cookies
                    /cookies status - cookie status
                    /cookies dynamic - toggle dynamic cookies
                    """;
            sendMessage(userId, msg);
            return;
        }

        if (args.equals("refresh")) {
            sendMessage(userId, "🔄 Updating cookies...");
            try {
                CookieService.refreshCookies("h5api.m.goofish.com");
                sendMessage(userId, "✅ Cookies updated");
            } catch (Exception e) {
                sendMessage(userId, "❌ Error: " + e.getMessage());
            }
        } else if (args.equals("status")) {
            Map<String, Object> stats = CookieService.getCacheStats();
            sendMessage(userId, "🍪 Dynamic: " + Config.isDynamicCookiesEnabled());
        } else if (args.equals("dynamic")) {
            boolean current = Config.isDynamicCookiesEnabled();
            Config.setProperty("cookie.dynamic.enabled", String.valueOf(!current));
            Config.saveConfig();
            sendMessage(userId, "✅ Dynamic cookies: " + (!current ? "ON" : "OFF"));
        }
    }

    private void handleAdminCommand(long userId, String args) {
        if (userId != adminId) {
            sendMessage(userId, "❌ Admin only");
            return;
        }

        if (args.isEmpty()) {
            String menu = """
                    👑 Admin:
                    /admin stats - statistics
                    /admin users - user list
                    /admin adduser [id] - add user
                    /admin removeuser [id] - remove user
                    """;
            sendMessage(userId, menu);
            return;
        }

        String[] parts = args.split(" ", 2);
        switch (parts[0]) {
            case "stats":
                sendStats(userId);
                break;
            case "users":
                List<Long> users = WhitelistManager.getAllUsers();
                sendMessage(userId, "👥 Users: " + users.size() + "\n" + users);
                break;
            case "adduser":
                if (parts.length > 1) {
                    try {
                        long uid = Long.parseLong(parts[1]);
                        WhitelistManager.addUser(uid);
                        sendMessage(userId, "✅ User added");
                    } catch (NumberFormatException e) {
                        sendMessage(userId, "❌ Invalid ID");
                    }
                }
                break;
            case "removeuser":
                if (parts.length > 1) {
                    try {
                        long uid = Long.parseLong(parts[1]);
                        WhitelistManager.removeUser(uid);
                        threadManager.stopUserParser(uid);
                        sendMessage(userId, "✅ User removed");
                    } catch (NumberFormatException e) {
                        sendMessage(userId, "❌ Invalid ID");
                    }
                }
                break;
        }
    }

    protected void sendMessage(long userId, String text) {
        SendMessage msg = new SendMessage(String.valueOf(userId), text);
        msg.enableMarkdown(true);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Failed to send message: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return Config.getTelegramBotUsername();
    }
}