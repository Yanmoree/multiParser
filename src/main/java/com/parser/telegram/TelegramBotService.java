package com.parser.telegram;

import com.parser.config.Config;
import com.parser.core.ThreadManager;
import com.parser.model.UserSettings;
import com.parser.service.CookieService;
import com.parser.storage.AccessRequestManager;
import com.parser.storage.UserSentProductsManager;
import com.parser.storage.WhitelistManager;
import com.parser.storage.UserDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
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

    /**
     * Настраивает меню команд (кнопка слева от поля ввода).
     * Вызывать после старта/регистрации бота.
     */
    public void configureCommandMenu() {
        try {
            List<BotCommand> userCommands = List.of(
                    new BotCommand("/start", "Запуск / проверка доступа"),
                    new BotCommand("/help", "Справка"),
                    new BotCommand("/getid", "Показать мой ID"),
                    new BotCommand("/addquery", "Добавить запрос"),
                    new BotCommand("/listqueries", "Список запросов"),
                    new BotCommand("/removequery", "Удалить запрос"),
                    new BotCommand("/clearqueries", "Очистить запросы"),
                    new BotCommand("/settings", "Настройки"),
                    new BotCommand("/start_parser", "Запустить парсер"),
                    new BotCommand("/stop_parser", "Остановить парсер"),
                    new BotCommand("/status", "Статус"),
                    new BotCommand("/stats", "Статистика"),
                    new BotCommand("/clearhistory", "Очистить историю отправленных")
            );

            execute(new SetMyCommands(userCommands, new BotCommandScopeDefault(), "ru"));
            logger.info("✅ User command menu configured");

            if (adminId != 0) {
                List<BotCommand> adminCommands = new ArrayList<>(userCommands);
                adminCommands.add(new BotCommand("/admin", "Админ-меню"));
                adminCommands.add(new BotCommand("/cookies", "Cookies"));
                adminCommands.add(new BotCommand("/help", "Справка (используй: /help admin)"));

                execute(new SetMyCommands(adminCommands, new BotCommandScopeChat(String.valueOf(adminId)), "ru"));
                logger.info("✅ Admin command menu configured for {}", adminId);
            }
        } catch (Exception e) {
            logger.warn("Failed to configure command menu: {}", e.getMessage());
        }
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
                handleHelpCommand(userId, args);
                break;
            case "/status":
                if (requireAuthorized(userId)) {
                    sendStatus(userId);
                }
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
                handleSettingsCommand(userId, args);
                break;
            case "/clearqueries":
                if (requireAuthorized(userId)) {
                    UserDataManager.clearUserQueries(userId);
                    sendMessage(userId, "✅ Запросы очищены. Добавить: /addquery");
                }
                break;
            case "/clearhistory":
                if (requireAuthorized(userId)) {
                    UserSentProductsManager.clearUserHistory(userId);
                    sendMessage(userId, "✅ История отправленных товаров очищена.");
                }
                break;
            case "/start_parser":
                if (requireAuthorized(userId)) {
                    threadManager.startUserParser(userId);
                }
                break;
            case "/stop_parser":
                if (requireAuthorized(userId)) {
                    threadManager.stopUserParser(userId);
                }
                break;
            case "/stats":
                if (requireAuthorized(userId)) {
                    sendStats(userId);
                }
                break;
            case "/cookies":
                handleCookiesCommand(userId, args);
                break;
            case "/admin":
                handleAdminCommand(userId, args);
                break;
            case "/getid":
                sendMessage(userId, "Ваш ID: " + userId);
                break;
            default:
                sendMessage(userId, "Неизвестная команда. Используй /help");
        }
    }

    private void handleTextResponse(long userId, String text) {
        String state = userStates.get(userId);
        if (state == null) {
            sendMessage(userId, "Используй команды. /help — список команд");
            return;
        }

        switch (state) {
            case "AWAITING_QUERY":
                if (!requireAuthorized(userId)) {
                    userStates.remove(userId);
                    userData.remove(userId);
                    return;
                }
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
        // ❗️Пользователи попадают в whitelist ТОЛЬКО через админа (см. /admin adduser)
        if (!WhitelistManager.isUserAllowed(userId)) {
            AccessRequestManager.recordAccessRequest(userId, "/start");
            String msg = "👋 Привет!\n\n" +
                    "❌ У вас нет доступа к боту.\n" +
                    "Отправьте администратору ваш ID: " + userId + "\n\n" +
                    "Команда: /getid";
            sendMessage(userId, msg);
            return;
        }

        sendMessage(userId, "👋 Привет! Используй /help для списка команд.");
    }

    private void handleAddQuery(long userId, String query) {
        if (!requireAuthorized(userId)) return;

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
        if (!requireAuthorized(userId)) return;

        List<String> queries = UserDataManager.getUserQueries(userId);
        if (queries.isEmpty()) {
            sendMessage(userId, "📭 Запросов нет. Добавь: /addquery");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 Ваши запросы:\n\n");
        for (int i = 0; i < queries.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, queries.get(i)));
        }

        sendMessage(userId, sb.toString());
    }

    private void handleRemoveQuery(long userId, String arg) {
        if (!requireAuthorized(userId)) return;

        try {
            if (arg.trim().isEmpty()) {
                sendMessage(userId, "Использование: /removequery [номер]");
                return;
            }

            int idx = Integer.parseInt(arg.trim()) - 1;
            List<String> queries = UserDataManager.getUserQueries(userId);

            if (idx < 0 || idx >= queries.size()) {
                sendMessage(userId, "❌ Неверный номер");
                return;
            }

            UserDataManager.removeUserQuery(userId, queries.get(idx));
            sendMessage(userId, "✅ Запрос удалён");
        } catch (NumberFormatException e) {
            sendMessage(userId, "❌ Неверный формат. Использование: /removequery [номер]");
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

    private void handleHelpCommand(long userId, String args) {
        String a = args == null ? "" : args.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(a)) {
            if (userId != adminId) {
                sendMessage(userId, "❌ Только для администратора");
                return;
            }
            sendAdminHelpMessage(userId);
            return;
        }
        sendUserHelpMessage(userId);
    }

    private void sendUserHelpMessage(long userId) {
        String help = """
                📚 Команды пользователя:

                ℹ️ Доступ:
                /start - приветствие/проверка доступа
                /getid - показать ваш ID (чтобы отправить админу)

                🎯 Запросы:
                /addquery [текст] - добавить запрос
                /listqueries - список запросов
                /removequery [номер] - удалить запрос
                /clearqueries - очистить все запросы

                ⚙️ Настройки:
                /settings - показать настройки
                /settings check_interval <сек>
                /settings max_age <мин>
                /settings max_pages <число>
                /settings rows_per_page <число>

                ▶️ Парсер:
                /start_parser - запустить
                /stop_parser - остановить
                /status - статус
                /stats - статистика

                🧹 История:
                /clearhistory - очистить историю отправленных товаров

                👑 Админ:
                /help admin - команды админа
                """;
        sendMessage(userId, help);
    }

    private void sendAdminHelpMessage(long userId) {
        String help = """
                👑 Команды админа:

                ✅ Доступ:
                /admin users - список пользователей whitelist
                /admin adduser <id> - добавить пользователя
                /admin removeuser <id> - удалить пользователя
                /admin pending - заявки на доступ

                🍪 Cookies:
                /cookies - меню
                /cookies status - статус
                /cookies refresh - обновить
                /cookies dynamic - включить/выключить динамические

                ℹ️ Справка:
                /help - команды пользователя
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
                
                Update with:
                /settings check_interval <seconds>
                /settings max_age <minutes>
                /settings max_pages <number>
                /settings rows_per_page <number>
                """, s.getCheckInterval(), s.getMaxAgeMinutes(), s.getMaxPages(),
                s.getRowsPerPage(), s.getPriceCurrency());
        sendMessage(userId, msg);
    }

    private void handleSettingsCommand(long userId, String args) {
        if (!requireAuthorized(userId)) {
            return;
        }

        if (args == null || args.trim().isEmpty()) {
            sendSettingsMenu(userId);
            return;
        }

        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            sendMessage(userId, "Использование: /settings <параметр> <значение>\nПример: /settings check_interval 300");
            return;
        }

        String key = parts[0].toLowerCase(Locale.ROOT);
        String value = parts[1];

        UserSettings settings = UserDataManager.getUserSettings(userId);
        try {
            int intVal = Integer.parseInt(value.trim());
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
                default:
                    sendMessage(userId, "❌ Неизвестная настройка: " + key + "\nОткрой меню: /settings");
                    return;
            }

            UserDataManager.saveUserSettings(userId, settings);
            sendMessage(userId, "✅ Настройка сохранена.\n" + settings.getSummary());
        } catch (NumberFormatException e) {
            sendMessage(userId, "❌ Неверное число: " + value);
        }
    }

    private void sendStatus(long userId) {
        Map<String, Object> status = threadManager.getUserStatus(userId);
        if (status == null) {
            sendMessage(userId, "🔴 Парсер не запущен");
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
            sendMessage(userId, "❌ Только для администратора");
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
            String msg = "🍪 Cookie status:\n\n" +
                    "Dynamic: " + Config.isDynamicCookiesEnabled() + "\n" +
                    "Cached domains: " + stats.getOrDefault("cachedDomains", "N/A") + "\n" +
                    "Last refresh: " + stats.getOrDefault("lastRefreshTime", "N/A") + "\n" +
                    "TTL (min): " + stats.getOrDefault("cacheTTLMinutes", "N/A") + "\n";
            sendMessage(userId, msg);
        } else if (args.equals("dynamic")) {
            boolean current = Config.isDynamicCookiesEnabled();
            Config.setProperty("cookie.dynamic.enabled", String.valueOf(!current));
            Config.saveConfig();
            sendMessage(userId, "✅ Dynamic cookies: " + (!current ? "ON" : "OFF"));
        } else {
            sendMessage(userId, "Неизвестная команда cookies. Используй /cookies");
        }
    }

    private void handleAdminCommand(long userId, String args) {
        if (userId != adminId) {
            sendMessage(userId, "❌ Только для администратора");
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
                Collections.sort(users);
                StringBuilder sb = new StringBuilder("👥 Whitelist users: " + users.size() + "\n\n");
                for (Long u : users) sb.append(u).append("\n");
                sendMessage(userId, sb.toString());
                break;
            case "pending":
                List<String> reqs = AccessRequestManager.getRequests();
                if (reqs.isEmpty()) {
                    sendMessage(userId, "📭 Заявок нет");
                    break;
                }
                StringBuilder rsb = new StringBuilder("📨 Заявки на доступ:\n\n");
                for (String line : reqs) {
                    rsb.append(line).append("\n");
                }
                rsb.append("\nДобавить: /admin adduser <id>");
                sendMessage(userId, rsb.toString());
                break;
            case "adduser":
                if (parts.length > 1) {
                    try {
                        long uid = Long.parseLong(parts[1]);
                        WhitelistManager.addUser(uid);
                        AccessRequestManager.removeRequest(uid);
                        sendMessage(userId, "✅ User added");
                    } catch (NumberFormatException e) {
                        sendMessage(userId, "❌ Invalid ID");
                    }
                } else {
                    sendMessage(userId, "Use: /admin adduser [id]");
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
                } else {
                    sendMessage(userId, "Use: /admin removeuser [id]");
                }
                break;
        }
    }

    private boolean requireAuthorized(long userId) {
        if (!WhitelistManager.isUserAllowed(userId)) {
            AccessRequestManager.recordAccessRequest(userId, "unauthorized_command");
            sendMessage(userId, "❌ Нет доступа. Отправь /getid администратору.");
            return false;
        }
        return true;
    }

    protected void sendMessage(long userId, String text) {
        SendMessage msg = new SendMessage(String.valueOf(userId), text);
        // ВАЖНО: не используем Markdown, чтобы команды вида /start_parser не ломали entities.
        msg.disableWebPagePreview();
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