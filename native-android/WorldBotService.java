package ru.botcraft.studio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** User-started foreground polling engine: the APK itself runs the draft Telegram bot. */
public final class WorldBotService extends Service {
    public static final String ACTION_STOP = "ru.botcraft.studio.action.STOP_WORLD_BOT";
    private static final String PREFS = "botcraft-native";
    private static final String TOKEN_CIPHER_KEY = "telegram_bot_token_ciphertext";
    private static final String TOKEN_IV_KEY = "telegram_bot_token_iv";
    private static final String PROJECTS_KEY = "projects";
    private static final String PLAYER_STATES_KEY = "telegram_player_states";
    private static final String CAMPAIGN_SKIN_KEY = "campaign_player_skin";
    private static final String MAP_IMAGE = "04-archipelago-map.jpg";
    private static final String ACTIVE_WORLD_KEY = "active_world_id";
    private static final String RUNNING_KEY = "telegram_bot_running";
    private static final String SERVICE_STATUS_KEY = "telegram_bot_status";
    private static final String KEY_ALIAS = "botcraft-telegram-token-v1";
    private static final String CHANNEL_ID = "botcraft-telegram-bot";
    private static final int NOTIFICATION_ID = 7142;
    private volatile boolean running;
    private volatile HttpURLConnection activeConnection;
    private ExecutorService executor;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            startForeground(NOTIFICATION_ID, notification("Подключаю бота к Telegram…"));
            prefs().edit().putBoolean(RUNNING_KEY, true).putString(SERVICE_STATUS_KEY, "Подключаю бота к Telegram…").apply();
            executor.execute(this::pollLoop);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (activeConnection != null) activeConnection.disconnect();
        prefs().edit().putBoolean(RUNNING_KEY, false).putString(SERVICE_STATUS_KEY, "Бот остановлен").apply();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onTimeout(int startId, int foregroundServiceType) {
        running = false;
        prefs().edit().putBoolean(RUNNING_KEY, false).putString(SERVICE_STATUS_KEY, "Android остановил долгий polling; запусти бота снова").apply();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf(startId);
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void pollLoop() {
        long offset = prefs().getLong("telegram_update_offset", 0L);
        try {
            String token = readToken();
            if (token.isEmpty()) throw new IllegalStateException("В APK не найден подключённый токен бота");
            prepareBot(token);
            updateStatus("Бот активен на этом телефоне · long polling");
            while (running) {
                HttpURLConnection connection = null;
                try {
                    String query = "?timeout=25&offset=" + offset + "&allowed_updates=" + URLEncoder.encode("[\"message\",\"callback_query\"]", "UTF-8");
                    connection = (HttpURLConnection) new URL(api(token, "getUpdates") + query).openConnection();
                    activeConnection = connection;
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(40000);
                    JSONObject response = new JSONObject(readAll(connection.getInputStream()));
                    if (!response.optBoolean("ok", false)) throw new IllegalStateException("Telegram getUpdates error");
                    JSONArray updates = response.optJSONArray("result");
                    if (updates != null) for (int i = 0; i < updates.length() && running; i++) {
                        JSONObject update = updates.optJSONObject(i);
                        if (update == null) continue;
                        long updateId = update.optLong("update_id", -1);
                        if (updateId >= offset) {
                            offset = updateId + 1;
                            prefs().edit().putLong("telegram_update_offset", offset).apply();
                        }
                        handleUpdate(token, update);
                    }
                } catch (Exception error) {
                    if (running) {
                        updateStatus("Связь с Telegram прервана; повторное подключение…");
                        sleep(3500);
                    }
                } finally {
                    if (connection != null) connection.disconnect();
                    activeConnection = null;
                }
            }
        } catch (Exception error) {
            updateStatus("Не удалось запустить бота · проверь токен и Telegram");
            stopSelf();
        }
    }

    private void addBotCommand(JSONArray commands, String command, String description) throws Exception {
        JSONObject item = new JSONObject(); item.put("command", command); item.put("description", description); commands.put(item);
    }

    private void prepareBot(String token) throws Exception {
        JSONObject webhook = telegramGet(token, "getWebhookInfo");
        JSONObject result = webhook.optJSONObject("result");
        if (result != null && !result.optString("url", "").isEmpty()) {
            JSONObject params = new JSONObject(); params.put("drop_pending_updates", false);
            telegramPost(token, "deleteWebhook", params);
        }
        JSONObject commands = new JSONObject();
        JSONArray values = new JSONArray();
        addBotCommand(values, "start", "Продолжить приключение");
        addBotCommand(values, "map", "Открыть карту архипелага");
        addBotCommand(values, "character", "Персонаж и характеристики");
        addBotCommand(values, "inventory", "Рюкзак и добыча");
        addBotCommand(values, "wardrobe", "Экипировка и одежда");
        addBotCommand(values, "quests", "Журнал заданий");
        addBotCommand(values, "skin", "Сменить внешний облик");
        addBotCommand(values, "restart", "Начать историю заново");
        commands.put("commands", values);
        telegramPost(token, "setMyCommands", commands);
        updateWorldProfile(token, activeWorld());
    }

    private void updateWorldProfile(String token, JSONObject world) throws Exception {
        String worldName = world == null ? "" : limit(world.optString("name", ""), 64);
        if (worldName.isEmpty() || worldName.equals(prefs().getString("telegram_profile_world_name", ""))) return;
        JSONObject profile = new JSONObject(); profile.put("name", worldName);
        JSONObject nameResult = telegramPost(token, "setMyName", profile);
        JSONObject description = new JSONObject();
        description.put("description", limit("Играй в «" + worldName + "» — история и выборы в Telegram.", 512));
        JSONObject descriptionResult = telegramPost(token, "setMyDescription", description);
        if (nameResult.optBoolean("ok", false) && descriptionResult.optBoolean("ok", false)) {
            prefs().edit().putString("telegram_profile_world_name", worldName).apply();
        }
    }

    private void handleUpdate(String token, JSONObject update) {
        JSONObject message = update.optJSONObject("message");
        if (message != null) {
            JSONObject chat = message.optJSONObject("chat");
            if (chat == null) return;
            long chatId = chat.optLong("id");
            String text = message.optString("text", "").trim();
            if (text.startsWith("/start")) {
                JSONObject world = activeWorld();
                try { updateWorldProfile(token, world); } catch (Exception ignored) { }
                String startId = startNodeId(world);
                if (startId.isEmpty()) { sendText(token, chatId, "Открой Botcraft Studio, выбери кампанию «Сердце Полуночного Архипелага» и запусти бота."); return; }
                JSONObject player = loadPlayer(chatId, world, false);
                if ("world".equals(player.optString("currentMode", "story"))) showGridMap(token, chatId, world, player, "");
                else {
                    String current = player.optString("currentNode", "");
                    if (current.isEmpty()) current = startId;
                    sendScene(token, chatId, world, current, true);
                }
            } else if (text.startsWith("/map")) {
                JSONObject world = activeWorld(); showGridMap(token, chatId, world, loadPlayer(chatId, world, false), "");
            } else if (text.startsWith("/character")) {
                showCharacter(token, chatId, loadPlayer(chatId, activeWorld(), false));
            } else if (text.startsWith("/wardrobe")) {
                showWardrobe(token, chatId, loadPlayer(chatId, activeWorld(), false));
            } else if (text.startsWith("/quests")) {
                showQuests(token, chatId, loadPlayer(chatId, activeWorld(), false));
            } else if (text.startsWith("/skin")) {
                showSkinSelector(token, chatId, loadPlayer(chatId, activeWorld(), false));
            } else if (text.startsWith("/restart")) {
                JSONObject world = activeWorld();
                String startId = startNodeId(world);
                if (startId.isEmpty()) sendText(token, chatId, "Сначала выбери и запусти игровой мир в APK.");
                else sendScene(token, chatId, world, startId, true, true);
            } else if (text.startsWith("/inventory")) {
                showInventory(token, chatId, loadPlayer(chatId, activeWorld(), false));
            } else if (!text.isEmpty()) {
                sendText(token, chatId, "Навигация: /map — карта, /quests — задания, /character — герой, /wardrobe — экипировка, /inventory — рюкзак.");
            }
            return;
        }
        JSONObject callback = update.optJSONObject("callback_query");
        if (callback == null) return;
        String callbackId = callback.optString("id", "");
        JSONObject callbackMessage = callback.optJSONObject("message");
        JSONObject chat = callbackMessage == null ? null : callbackMessage.optJSONObject("chat");
        String data = callback.optString("data", "");
        if (!callbackId.isEmpty()) answerCallback(token, callbackId);
        if (chat == null) return;
        long chatId = chat.optLong("id");
        if (data.startsWith("grid:")) {
            handleGridAction(token, chatId, data.substring(5));
        } else if (data.startsWith("pick:")) {
            try {
                int separator = data.lastIndexOf(':');
                String sourceNode = data.substring(5, separator);
                int choiceIndex = Integer.parseInt(data.substring(separator + 1));
                handleChoice(token, chatId, sourceNode, choiceIndex);
            } catch (Exception ignored) { }
        } else if (data.startsWith("ow:")) {
            handleWorldAction(token, chatId, data.substring(3));
        } else if (data.startsWith("battle:")) {
            String[] parts = data.split(":", 4);
            if (parts.length == 4) {
                try { handleBattleAction(token, chatId, parts[1], Integer.parseInt(parts[2]), parts[3]); } catch (Exception ignored) { }
            }
        } else if (data.startsWith("next:")) {
            JSONObject world = activeWorld(); JSONObject player = loadPlayer(chatId, world, false);
            if (!data.substring(5).equals(player.optString("currentNode", ""))) return;
            JSONObject current = findNode(world == null ? null : world.optJSONArray("nodes"), player.optString("currentNode", ""));
            String target = current == null ? "" : current.optString("next", "");
            if (!target.isEmpty()) sendScene(token, chatId, world, target, true);
        } else if ("restart".equals(data)) {
            JSONObject world = activeWorld(); String start = startNodeId(world);
            if (!start.isEmpty()) sendScene(token, chatId, world, start, true, true);
        }
    }

    private String startNodeId(JSONObject world) {
        JSONArray nodes = world == null ? null : world.optJSONArray("nodes");
        String id = world == null ? "" : world.optString("startNode", "");
        if (id.isEmpty() || findNode(nodes, id) == null) {
            JSONObject first = nodes == null ? null : nodes.optJSONObject(0);
            if (first != null) id = first.optString("id", "");
        }
        return id;
    }

    private JSONObject loadPlayer(long chatId, JSONObject world, boolean reset) {
        String key = Long.toString(chatId);
        String worldId = world == null ? "" : world.optString("id", "");
        try {
            JSONObject players = new JSONObject(prefs().getString(PLAYER_STATES_KEY, "{}"));
            JSONObject player = players.optJSONObject(key);
            if (reset) {
                JSONObject previous = player; player = newPlayer(worldId);
                if (previous != null) { player.put("pageMessageId", previous.optLong("pageMessageId", 0L)); player.put("pageMode", previous.optString("pageMode", "photo")); }
            } else if (player == null || !worldId.equals(player.optString("worldId", ""))) player = newPlayer(worldId);
            migratePlayer(player);
            players.put(key, player);
            prefs().edit().putString(PLAYER_STATES_KEY, players.toString()).apply();
            return player;
        } catch (Exception ignored) { return newPlayer(worldId); }
    }

    private JSONObject newPlayer(String worldId) {
        JSONObject player = new JSONObject();
        try {
            String defaultSkin = prefs().getString(CAMPAIGN_SKIN_KEY, "wanderer");
            player.put("worldId", worldId); player.put("hp", 16); player.put("maxHp", 16);
            player.put("gold", 45); player.put("xp", 0); player.put("level", 1); player.put("inventory", new JSONArray());
            player.put("gear", new JSONArray()); player.put("equipment", new JSONObject());
            JSONArray skins = new JSONArray(); skins.put(defaultSkin); player.put("skins", skins); player.put("skin", defaultSkin);
            player.put("quests", new JSONObject()); player.put("kills", new JSONObject()); player.put("resources", new JSONObject());
            JSONArray visitedRegions = new JSONArray(); visitedRegions.put("harbor"); player.put("visitedRegions", visitedRegions);
            player.put("currentRegion", "harbor"); player.put("currentMode", "story");
            player.put("gridX", 50); player.put("gridY", 50); player.put("resolvedGridEvents", new JSONArray());
            player.put("flags", new JSONObject()); player.put("visited", new JSONArray());
            player.put("clearedBattles", new JSONArray()); player.put("currentNode", ""); player.put("pageMessageId", 0);
        } catch (Exception ignored) { }
        addItem(player, "Зелье северной мяты");
        return player;
    }

    private void migratePlayer(JSONObject player) {
        if (player == null) return;
        try {
            String defaultSkin = prefs().getString(CAMPAIGN_SKIN_KEY, "wanderer");
            if (!player.has("level")) player.put("level", 1);
            if (!player.has("gear")) player.put("gear", new JSONArray());
            if (!player.has("equipment")) player.put("equipment", new JSONObject());
            if (!player.has("quests")) player.put("quests", new JSONObject());
            if (!player.has("kills")) player.put("kills", new JSONObject());
            if (!player.has("resources")) player.put("resources", new JSONObject());
            if (!player.has("visitedRegions")) { JSONArray visited = new JSONArray(); visited.put("harbor"); player.put("visitedRegions", visited); }
            if (!player.has("currentRegion")) player.put("currentRegion", "harbor");
            if (!player.has("currentMode")) player.put("currentMode", "story");
            if (!player.has("gridX")) player.put("gridX", 50);
            if (!player.has("gridY")) player.put("gridY", 50);
            if (!player.has("resolvedGridEvents")) player.put("resolvedGridEvents", new JSONArray());
            if (!player.has("skins")) { JSONArray skins = new JSONArray(); skins.put(defaultSkin); player.put("skins", skins); }
            if (!player.has("skin")) player.put("skin", defaultSkin);
            if (!player.has("pageMessageId")) player.put("pageMessageId", 0);
            if (!player.has("maxHp")) player.put("maxHp", 12);
            if (!player.has("hp")) player.put("hp", player.optInt("maxHp", 12));
        } catch (Exception ignored) { }
    }

    private void savePlayer(long chatId, JSONObject player) {
        try {
            JSONObject players = new JSONObject(prefs().getString(PLAYER_STATES_KEY, "{}"));
            players.put(Long.toString(chatId), player);
            prefs().edit().putString(PLAYER_STATES_KEY, players.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void sendScene(String token, long chatId, JSONObject world, String nodeId) {
        sendScene(token, chatId, world, nodeId, true, false);
    }

    private void sendScene(String token, long chatId, JSONObject world, String nodeId, boolean includePhoto) {
        sendScene(token, chatId, world, nodeId, includePhoto, false);
    }

    private void sendScene(String token, long chatId, JSONObject world, String nodeId, boolean includePhoto, boolean reset) {
        JSONArray nodes = world == null ? null : world.optJSONArray("nodes");
        JSONObject node = findNode(nodes, nodeId);
        if (node == null) { sendText(token, chatId, "Эта сцена не найдена. Отправь /restart, чтобы начать заново."); return; }
        JSONObject player = loadPlayer(chatId, world, reset);
        if ("battle".equals(node.optString("type")) && contains(player.optJSONArray("clearedBattles"), nodeId)) {
            String clearedTarget = node.optString("victoryTarget", "");
            if (!clearedTarget.isEmpty()) { sendScene(token, chatId, world, clearedTarget, true); return; }
        }
        if ("battle".equals(node.optString("type"))) {
            JSONObject battle = player.optJSONObject("battle");
            if (battle == null || !nodeId.equals(battle.optString("nodeId", ""))) {
                battle = new JSONObject();
                try { battle.put("nodeId", nodeId); battle.put("battleId", UUID.randomUUID().toString().substring(0, 8)); battle.put("enemyHp", node.optInt("enemyHp", 5)); battle.put("turn", 0); player.put("battle", battle); }
                catch (Exception ignored) { }
            }
        }
        JSONArray visited = player.optJSONArray("visited");
        if (visited == null) { visited = new JSONArray(); try { player.put("visited", visited); } catch (Exception ignored) { } }
        if (!contains(visited, nodeId)) {
            try { visited.put(nodeId); } catch (Exception ignored) { }
            JSONObject onEnter = node.optJSONObject("onEnter");
            if (onEnter != null) applyEffect(player, onEnter);
        }
        try { player.put("currentNode", nodeId); } catch (Exception ignored) { }
        savePlayer(chatId, player);

        String title = node.optString("title", "Сцена");
        String body = node.optString("text", "");
        StringBuilder narrative = new StringBuilder(title);
        if (!body.isEmpty()) narrative.append("\n\n").append(body);
        narrative.append("\n\n").append(playerSummary(player));
        if ("inventory".equals(node.optString("type"))) narrative.append("\n\nСодержимое рюкзака: ").append(inventoryText(player));

        JSONObject markup = new JSONObject();
        JSONArray keyboard = new JSONArray();
        try {
            if ("battle".equals(node.optString("type"))) {
                JSONObject battle = player.optJSONObject("battle");
                int turn = battle == null ? 0 : battle.optInt("turn", 0);
                String battleId = battle == null ? "" : battle.optString("battleId", "");
                addButtonRow(keyboard, "⚔️ Атаковать", "battle:" + battleId + ":" + turn + ":attack");
                addButtonRow(keyboard, "🧪 Выпить зелье", "battle:" + battleId + ":" + turn + ":potion");
                addButtonRow(keyboard, "↩️ Отступить", "battle:" + battleId + ":" + turn + ":flee");
                narrative.append("\n\nПротивник: ").append(node.optString("enemyName", "Враг"))
                    .append(" · здоровье ").append(battle == null ? node.optInt("enemyHp", 5) : battle.optInt("enemyHp"));
            } else {
                JSONArray choices = node.optJSONArray("choices");
                if (choices != null) for (int i = 0; i < choices.length(); i++) {
                    JSONObject choice = choices.optJSONObject(i);
                    if (choice == null) continue;
                    if (!choiceAvailable(player, choice)) continue;
                    String target = choice.optString("target", "");
                    if (target.isEmpty()) target = node.optString("next", "");
                    if (target.isEmpty()) continue;
                    addButtonRow(keyboard, choice.optString("label", "Продолжить"), "pick:" + nodeId + ":" + i);
                }
                if (keyboard.length() == 0) {
                    if (choices != null && choices.length() > 0) addButtonRow(keyboard, "📜 Проверить задания", "ow:quests");
                    else {
                        String next = node.optString("next", "");
                        if (!next.isEmpty()) addButtonRow(keyboard, "Продолжить", "next:" + nodeId);
                        else addButtonRow(keyboard, "Начать историю заново", "restart");
                    }
                }
                addButtonRow(keyboard, "🗺 Открытый мир", "ow:map");
                addButtonRow(keyboard, "👤 Герой и экипировка", "ow:character");
            }
            if (keyboard.length() > 0) { JSONObject rows = new JSONObject(); rows.put("inline_keyboard", keyboard); markup = rows; }
            player.put("currentMode", "story");
            savePlayer(chatId, player);
        } catch (Exception ignored) { }
        sendGamePage(token, chatId, limit(narrative.toString(), 1000), markup, MAP_IMAGE);
    }

    private void addButtonRow(JSONArray keyboard, String label, String callbackData) throws Exception {
        JSONObject button = new JSONObject(); button.put("text", limit(label, 64)); button.put("callback_data", callbackData);
        JSONArray row = new JSONArray(); row.put(button); keyboard.put(row);
    }

    private boolean choiceAvailable(JSONObject player, JSONObject choice) {
        String requiredItem = choice.optString("requiresItem", "");
        if (!requiredItem.isEmpty() && !hasItem(player, requiredItem)) return false;
        JSONObject effect = choice.optJSONObject("effect");
        if (effect != null && "buy".equals(effect.optString("type", "")) && player.optInt("gold", 0) < effect.optInt("cost", 0)) return false;
        if (player.optInt("level", 1) < choice.optInt("requiresLevel", 1)) return false;
        String requiredQuest = choice.optString("requiresQuest", "");
        JSONObject quests = player.optJSONObject("quests");
        return requiredQuest.isEmpty() || (quests != null && "done".equals(quests.optString(requiredQuest, "")));
    }

    private void handleChoice(String token, long chatId, String expectedNodeId, int choiceIndex) {
        JSONObject world = activeWorld();
        JSONObject player = loadPlayer(chatId, world, false);
        if (!expectedNodeId.equals(player.optString("currentNode", ""))) return;
        JSONObject node = findNode(world == null ? null : world.optJSONArray("nodes"), player.optString("currentNode", ""));
        JSONArray choices = node == null ? null : node.optJSONArray("choices");
        if (node == null || choices == null || choiceIndex < 0 || choiceIndex >= choices.length()) return;
        JSONObject choice = choices.optJSONObject(choiceIndex);
        if (choice == null) return;
        if (!choiceAvailable(player, choice)) {
            sendText(token, chatId, "Пока не выполнены требования этой ветки. Проверь уровень, квесты, предметы и монеты."); return;
        }
        JSONObject effect = choice.optJSONObject("effect");
        if (effect != null && "reset".equals(effect.optString("type"))) {
            JSONObject previous = player; player = newPlayer(world.optString("id", ""));
            try { player.put("pageMessageId", previous.optLong("pageMessageId", 0L)); player.put("pageMode", previous.optString("pageMode", "photo")); } catch (Exception ignored) { }
        } else if (effect != null && !applyEffect(player, effect)) {
            sendText(token, chatId, "Не хватает монет или нужного предмета.");
            sendScene(token, chatId, world, node.optString("id", ""), false);
            return;
        }
        String target = choice.optString("target", "");
        if (target.isEmpty()) target = node.optString("next", "");
        if (player.optInt("hp", 1) <= 0) target = choice.optString("fallbackTarget", "");
        if (target.isEmpty()) { sendText(token, chatId, "Путь пока не настроен в APK."); return; }
        savePlayer(chatId, player);
        sendScene(token, chatId, world, target, true);
    }

    private boolean applyEffect(JSONObject player, JSONObject effect) {
        String type = effect.optString("type", "");
        String item = effect.optString("item", "");
        int amount = effect.optInt("amount", 0);
        try {
            switch (type) {
                case "item": addItem(player, item); return true;
                case "gold": player.put("gold", Math.max(0, player.optInt("gold") + amount)); return true;
                case "xp": player.put("xp", Math.max(0, player.optInt("xp") + amount)); return true;
                case "heal": player.put("hp", Math.min(player.optInt("maxHp", 10), player.optInt("hp", 10) + amount)); return true;
                case "damage": player.put("hp", Math.max(0, player.optInt("hp", 10) - amount)); return true;
                case "flag": {
                    JSONObject flags = player.optJSONObject("flags");
                    if (flags == null) { flags = new JSONObject(); player.put("flags", flags); }
                    flags.put(item, true); return true;
                }
                case "consume": return removeItem(player, item);
                case "buy":
                    int cost = effect.optInt("cost", 0);
                    if (player.optInt("gold") < cost) return false;
                    player.put("gold", player.optInt("gold") - cost); addItem(player, item); return true;
                default: return true;
            }
        } catch (Exception ignored) { return false; }
    }

    private void handleBattleAction(String token, long chatId, String expectedBattleId, int expectedTurn, String action) {
        JSONObject world = activeWorld();
        JSONObject player = loadPlayer(chatId, world, false);
        JSONObject battle = player.optJSONObject("battle");
        if (battle != null && ("overworld".equals(battle.optString("mode", "")) || "grid".equals(battle.optString("mode", "")))) {
            handleOverworldBattleAction(token, chatId, world, player, expectedBattleId, expectedTurn, action); return;
        }
        String nodeId = player.optString("currentNode", "");
        JSONObject node = findNode(world == null ? null : world.optJSONArray("nodes"), nodeId);
        if (node == null || !"battle".equals(node.optString("type", ""))) return;
        if (battle == null || !nodeId.equals(battle.optString("nodeId", ""))) return;
        if (!expectedBattleId.equals(battle.optString("battleId", "")) || expectedTurn != battle.optInt("turn", 0)) return;
        if ("flee".equals(action)) {
            player.remove("battle"); savePlayer(chatId, player);
            sendScene(token, chatId, world, node.optString("fleeTarget", node.optString("defeatTarget", "")), true); return;
        }
        int healTurn = 0;
        if ("potion".equals(action)) {
            if (!removeItem(player, "Зелье северной мяты")) {
                sendText(token, chatId, "В рюкзаке нет зелья. Загляни к торговцу перед следующим походом.");
                return;
            }
            int oldHp = player.optInt("hp", 12);
            try { player.put("hp", Math.min(player.optInt("maxHp", 12), oldHp + 8)); } catch (Exception ignored) { }
            healTurn = 1;
        } else if (!"attack".equals(action)) return;
        if (healTurn == 0) {
            int damage = playerAttack(player);
            int enemyHp = battle.optInt("enemyHp", node.optInt("enemyHp", 5)) - damage;
            try { battle.put("enemyHp", enemyHp); player.put("battle", battle); } catch (Exception ignored) { }
            if (enemyHp <= 0) {
                addString(player.optJSONArray("clearedBattles"), nodeId);
                try {
                    player.remove("battle");
                    player.put("gold", player.optInt("gold") + node.optInt("rewardGold", 0));
                    grantExperience(player, node.optInt("rewardXp", 0));
                    addItem(player, node.optString("rewardItem", ""));
                } catch (Exception ignored) { }
                savePlayer(chatId, player);
                sendScene(token, chatId, world, node.optString("victoryTarget", ""), true); return;
            }
        }
        int hp = player.optInt("hp", 12) - Math.max(1, node.optInt("enemyAttack", 1) - playerDefense(player));
        if (hp <= 0) {
            try { player.put("hp", 4); player.remove("battle"); } catch (Exception ignored) { }
            savePlayer(chatId, player);
            sendText(token, chatId, "Тебя отбросило назад к берегу. Мира перевязала раны и вернула 4 здоровья.");
            sendScene(token, chatId, world, node.optString("defeatTarget", ""), true); return;
        }
        try { battle.put("turn", battle.optInt("turn", 0) + 1); player.put("hp", hp); player.put("battle", battle); } catch (Exception ignored) { }
        savePlayer(chatId, player);
        sendScene(token, chatId, world, nodeId, false);
    }

    private JSONObject findWorldEvent(JSONObject world, String id) {
        JSONArray events = world == null ? null : world.optJSONArray("worldEvents");
        if (events == null) return null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null && id.equals(event.optString("id", ""))) return event;
        }
        return null;
    }

    private JSONObject eventAt(JSONObject world, int x, int y) {
        JSONArray events = world == null ? null : world.optJSONArray("worldEvents");
        if (events == null) return null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null && event.optInt("x", -1) == x && event.optInt("y", -1) == y) return event;
        }
        return null;
    }

    private String regionForGrid(int x, int y) {
        String[] ids = {"harbor", "forest", "village", "shipwreck", "mine", "crystal_cave", "wolf_cliffs", "watchtower", "lighthouse", "high_pass", "observatory", "storm_isle", "heart_peak"};
        int[][] centers = {{50,50},{39,38},{46,61},{27,35},{37,74},{23,60},{30,50},{58,61},{68,63},{50,75},{72,39},{82,71},{91,86}};
        int best = 0; int distance = Integer.MAX_VALUE;
        for (int i = 0; i < centers.length; i++) {
            int dx = x - centers[i][0], dy = y - centers[i][1]; int candidate = dx * dx + dy * dy;
            if (candidate < distance) { distance = candidate; best = i; }
        }
        return ids[best];
    }

    private String regionSymbol(String id) {
        switch (id) {
            case "harbor": return "🏠"; case "forest": return "🌲"; case "village": return "🏚";
            case "shipwreck": return "⚓"; case "mine": return "⛏"; case "crystal_cave": return "💎";
            case "wolf_cliffs": return "🐺"; case "watchtower": return "🗼"; case "lighthouse": return "🔆";
            case "high_pass": return "⛰"; case "observatory": return "🔭"; case "storm_isle": return "🌩";
            case "heart_peak": return "🐉"; default: return "·";
        }
    }

    private String eventSymbol(JSONObject event) {
        if (event == null) return "·";
        switch (event.optString("kind", "")) {
            case "mob": return "⚔️"; case "npc": return "👤"; case "cache": return "🎁";
            case "shrine": return "✨"; default: return "❔";
        }
    }

    private void showGridMap(String token, long chatId, JSONObject world, JSONObject player, String notice) {
        JSONObject grid = world == null ? null : world.optJSONObject("grid");
        JSONArray events = world == null ? null : world.optJSONArray("worldEvents");
        if (grid == null || events == null || events.length() < 500) {
            sendText(token, chatId, "Клеточная карта ещё не загружена. Открой кампанию в APK и перезапусти бота."); return;
        }
        JSONObject ongoingBattle = player.optJSONObject("battle");
        if (ongoingBattle != null && ("grid".equals(ongoingBattle.optString("mode", "")) || "overworld".equals(ongoingBattle.optString("mode", "")))) { showOverworldBattle(token, chatId, world, player, "Сначала заверши бой или отступи."); return; }
        int x = Math.max(0, Math.min(99, player.optInt("gridX", 50)));
        int y = Math.max(0, Math.min(99, player.optInt("gridY", 50)));
        String regionId = regionForGrid(x, y); JSONObject region = findRegion(world, regionId);
        JSONArray visited = player.optJSONArray("visitedRegions"); boolean newRegion = !contains(visited, regionId); if (newRegion) addString(visited, regionId);
        try { player.put("gridX", x); player.put("gridY", y); player.put("currentRegion", regionId); player.put("currentMode", "world"); savePlayer(chatId, player); } catch (Exception ignored) { }

        StringBuilder text = new StringBuilder("МИРОВАЯ КАРТА · 100 × 100\n");
        text.append("Область: ").append(region == null ? regionId : region.optString("title"));
        text.append(" · координаты ").append(x).append(", ").append(y);
        text.append("\nНажми соседнюю клетку или стрелку, чтобы сделать один шаг. Диагоналей нет.");
        text.append("\n🔒 — непроходимая клетка. Исследуй доступные точки интереса.");
        if (newRegion) text.append("\nНовая область открыта.");
        if (!notice.isEmpty()) text.append("\n\n").append(notice);
        JSONObject here = eventAt(world, x, y);
        boolean eventAvailable = here != null && !contains(player.optJSONArray("resolvedGridEvents"), here.optString("id", ""));
        if (eventAvailable) text.append("\n\nНа этой клетке есть событие: ").append(here.optString("title", "Точка интереса"));
        text.append("\n\n").append(playerSummary(player));

        JSONArray keyboard = new JSONArray();
        try {
            int startX = x - 4, startY = y - 4;
            for (int rowIndex = 0; rowIndex < 10; rowIndex++) {
                JSONArray row = new JSONArray();
                for (int column = 0; column < 10; column++) {
                    int cx = startX + column, cy = startY + rowIndex;
                    String label, callback;
                    if (cx < 0 || cx >= 100 || cy < 0 || cy >= 100) {
                        label = "⬛"; callback = "grid:void";
                    } else if (!isGridCellAccessible(cx, cy)) {
                        label = "🔒"; callback = "grid:void";
                    } else if (cx == x && cy == y) {
                        label = "🧍"; callback = "grid:interact";
                    } else {
                        JSONObject event = eventAt(world, cx, cy);
                        label = event != null && !contains(player.optJSONArray("resolvedGridEvents"), event.optString("id", ""))
                            ? eventSymbol(event) : regionSymbol(regionForGrid(cx, cy));
                        callback = "grid:cell:" + cx + ":" + cy;
                    }
                    addInlineButton(row, label, callback);
                }
                keyboard.put(row);
            }
            JSONArray directions = new JSONArray();
            addInlineButton(directions, "⬆️", "grid:move:up");
            addInlineButton(directions, "⬅️", "grid:move:left");
            addInlineButton(directions, eventAvailable ? "🔎 Изучить" : "📍 Здесь", eventAvailable ? "grid:interact" : "grid:stay");
            addInlineButton(directions, "➡️", "grid:move:right");
            addInlineButton(directions, "⬇️", "grid:move:down");
            keyboard.put(directions);
            if (!"harbor".equals(regionId)) addButtonRow(keyboard, "⚔️ Охотиться в этой зоне", "grid:hunt");
            addButtonRow(keyboard, "📜 Задания", "ow:quests");
            addButtonRow(keyboard, "👤 Герой и экипировка", "ow:character");
            addButtonRow(keyboard, "🎒 Рюкзак", "ow:inventory");
        } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard, null);
    }

    private boolean isGridCellAccessible(int x, int y) {
        if (x < 0 || x >= 100 || y < 0 || y >= 100) return false;
        int localX = x % 10, localY = y % 10;
        return !(localX == 4 && localY >= 3 && localY <= 6)
            && !(localX == 5 && (localY == 3 || localY == 6));
    }

    private void addInlineButton(JSONArray row, String label, String data) throws Exception {
        JSONObject button = new JSONObject(); button.put("text", limit(label, 64)); button.put("callback_data", data); row.put(button);
    }

    private void handleGridAction(String token, long chatId, String action) {
        JSONObject world = activeWorld(); JSONObject player = loadPlayer(chatId, world, false);
        String[] parts = action.split(":", 4);
        if (parts.length == 0) return;
        if (parts[0].equals("move") && parts.length > 1) { moveGrid(token, chatId, world, player, parts[1]); return; }
        if (parts[0].equals("cell") && parts.length > 2) {
            try {
                int targetX = Integer.parseInt(parts[1]), targetY = Integer.parseInt(parts[2]);
                int dx = targetX - player.optInt("gridX", 50), dy = targetY - player.optInt("gridY", 50);
                if (dx == 0 && dy == 0) { showGridEvent(token, chatId, world, player, eventAt(world, targetX, targetY)); return; }
                if (Math.abs(dx) + Math.abs(dy) != 1) { showGridMap(token, chatId, world, player, "Можно шагнуть только на одну соседнюю клетку."); return; }
                if (dx == 1) moveGrid(token, chatId, world, player, "right");
                else if (dx == -1) moveGrid(token, chatId, world, player, "left");
                else if (dy == 1) moveGrid(token, chatId, world, player, "down");
                else moveGrid(token, chatId, world, player, "up");
            } catch (Exception ignored) { showGridMap(token, chatId, world, player, ""); }
            return;
        }
        if (parts[0].equals("interact")) { showGridEvent(token, chatId, world, player, eventAt(world, player.optInt("gridX", 50), player.optInt("gridY", 50))); return; }
        if (parts[0].equals("fight") && parts.length > 1) { startGridEventBattle(token, chatId, world, player, parts[1]); return; }
        if (parts[0].equals("resolve") && parts.length > 2) { resolveGridEvent(token, chatId, world, player, parts[1], parts[2]); return; }
        if (parts[0].equals("hunt")) { startGridHunt(token, chatId, world, player); return; }
        if (parts[0].equals("stay")) { showGridMap(token, chatId, world, player, ""); return; }
        if (parts[0].equals("void")) { showGridMap(token, chatId, world, player, "Эта клетка закрыта или находится за краем карты."); return; }
        if (parts[0].equals("edge")) { showGridMap(token, chatId, world, player, "Ты у края мира: дальше пройти нельзя."); return; }
        showGridMap(token, chatId, world, player, "");
    }

    private void moveGrid(String token, long chatId, JSONObject world, JSONObject player, String direction) {
        JSONObject battle = player.optJSONObject("battle");
        if (battle != null && ("grid".equals(battle.optString("mode", "")) || "overworld".equals(battle.optString("mode", "")))) {
            showOverworldBattle(token, chatId, world, player, "Сначала заверши бой или отступи."); return;
        }
        int x = player.optInt("gridX", 50), y = player.optInt("gridY", 50);
        switch (direction) {
            case "up": y--; break; case "down": y++; break; case "left": x--; break; case "right": x++; break;
            default: showGridMap(token, chatId, world, player, "Можно двигаться только вверх, вниз, влево или вправо."); return;
        }
        if (x < 0 || x > 99 || y < 0 || y > 99) { showGridMap(token, chatId, world, player, "Дальше край карты."); return; }
        if (!isGridCellAccessible(x, y)) { showGridMap(token, chatId, world, player, "Путь закрыт: эта клетка непроходима. Выбери другой маршрут."); return; }
        String prior = player.optString("currentRegion", "harbor"), next = regionForGrid(x, y);
        String notice = "Ты сделал шаг на одну клетку к " + (direction.equals("up") ? "северу" : direction.equals("down") ? "югу" : direction.equals("left") ? "западу" : "востоку") + ".";
        if (!prior.equals(next)) { JSONObject region = findRegion(world, next); notice += " Новая зона: " + (region == null ? next : region.optString("title")) + "."; }
        try { player.put("gridX", x); player.put("gridY", y); player.put("currentMode", "world"); savePlayer(chatId, player); } catch (Exception ignored) { }
        showGridMap(token, chatId, world, player, notice);
    }

    private void showGridEvent(String token, long chatId, JSONObject world, JSONObject player, JSONObject event) {
        if (event == null) { showGridMap(token, chatId, world, player, "На этой клетке ничего нет."); return; }
        String id = event.optString("id", "");
        if (contains(player.optJSONArray("resolvedGridEvents"), id)) { showGridMap(token, chatId, world, player, "Ты уже исследовал это место."); return; }
        String kind = event.optString("kind", "mystery");
        StringBuilder text = new StringBuilder(event.optString("title", "Событие архипелага"));
        text.append("\n\n").append(event.optString("text", "Следы ведут дальше в снег.")).append("\n\nКоординаты узла: ").append(event.optInt("x")).append(", ").append(event.optInt("y"));
        if (kind.equals("mob")) text.append("\n\nУгроза: ").append(event.optString("enemy")).append(" · опасность ").append(event.optInt("rank"));
        JSONArray keyboard = new JSONArray();
        try {
            if (kind.equals("mob")) {
                addButtonRow(keyboard, "⚔️ Вступить в бой", "grid:fight:" + id);
                addButtonRow(keyboard, "🕯 Обойти стороной", "grid:resolve:" + id + ":evade");
            } else if (kind.equals("npc")) {
                addButtonRow(keyboard, "🤝 Помочь путнику", "grid:resolve:" + id + ":help");
                addButtonRow(keyboard, "🗺 Расспросить о маршруте", "grid:resolve:" + id + ":ask");
                addButtonRow(keyboard, "↩️ Не вмешиваться", "grid:resolve:" + id + ":leave");
            } else if (kind.equals("cache")) {
                addButtonRow(keyboard, "🎁 Открыть находку", "grid:resolve:" + id + ":open");
                addButtonRow(keyboard, "↩️ Оставить на месте", "grid:resolve:" + id + ":leave");
            } else if (kind.equals("shrine")) {
                addButtonRow(keyboard, "✨ Попросить помощи у святилища", "grid:resolve:" + id + ":pray");
                addButtonRow(keyboard, "↩️ Уйти молча", "grid:resolve:" + id + ":leave");
            } else {
                addButtonRow(keyboard, "🔮 Коснуться руны", "grid:resolve:" + id + ":touch");
                addButtonRow(keyboard, "🧠 Разгадать послание", "grid:resolve:" + id + ":solve");
                addButtonRow(keyboard, "↩️ Отойти", "grid:resolve:" + id + ":leave");
            }
            addButtonRow(keyboard, "↩️ К клеточной карте", "ow:map");
        } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard);
    }

    private void resolveGridEvent(String token, long chatId, JSONObject world, JSONObject player, String id, String choice) {
        JSONObject event = findWorldEvent(world, id);
        if (event == null || contains(player.optJSONArray("resolvedGridEvents"), id)) { showGridMap(token, chatId, world, player, ""); return; }
        if ("leave".equals(choice)) { showGridMap(token, chatId, world, player, "Ты решил пока не вмешиваться."); return; }
        addString(player.optJSONArray("resolvedGridEvents"), id);
        int xp = event.optInt("xp", 15), gold = event.optInt("gold", 5); String notice;
        try {
            if ("evade".equals(choice)) { notice = "Ты обошёл опасность и продолжил путь."; }
            else if ("pray".equals(choice)) {
                player.put("hp", player.optInt("maxHp", 12)); grantExperience(player, Math.max(4, xp / 2)); notice = "Святилище восстановило здоровье и оставило в ладони тёплую руну.";
                addItem(player, "Руна северного света");
            } else if ("ask".equals(choice)) {
                player.put("gold", player.optInt("gold") + Math.max(3, gold / 2)); grantExperience(player, Math.max(3, xp / 3));
                incrementCount(player, "flags", "helpedWayfarers", 1); notice = "Путник рассказал о тайных тропах и поделился монетами.";
            } else if ("help".equals(choice)) {
                player.put("gold", player.optInt("gold") + gold); grantExperience(player, xp); incrementCount(player, "flags", "helpedWayfarers", 1);
                addItem(player, "Письмо благодарности"); notice = "Путник спасён. Он оставил награду и обещал рассказать о тебе в Нордхейме.";
            } else if ("open".equals(choice)) {
                player.put("gold", player.optInt("gold") + gold * 2); grantExperience(player, xp);
                String loot = event.optString("loot", "");
                if (!grantGear(player, loot)) addItem(player, "Осколок старого компаса");
                notice = "Тайник открыт: опыт, монеты и находка добавлены в рюкзак.";
            } else {
                boolean lucky = Math.random() > 0.3;
                if (lucky) { player.put("gold", player.optInt("gold") + gold); grantExperience(player, xp); addItem(player, "Осколок старого компаса"); notice = "Руна вспыхнула: ты получил опыт, монеты и редкий осколок."; }
                else { player.put("hp", Math.max(1, player.optInt("hp", 12) - Math.max(1, event.optInt("rank", 1)))); notice = "Руна обожгла ладонь. Ты потерял здоровье, но разгадал часть надписи."; }
            }
            savePlayer(chatId, player);
        } catch (Exception ignored) { notice = "Событие завершено."; }
        showGridMap(token, chatId, world, player, notice);
    }

    private void startGridEventBattle(String token, long chatId, JSONObject world, JSONObject player, String id) {
        JSONObject event = findWorldEvent(world, id);
        if (event == null || !"mob".equals(event.optString("kind", "")) || contains(player.optJSONArray("resolvedGridEvents"), id)) { showGridMap(token, chatId, world, player, ""); return; }
        JSONObject battle = new JSONObject();
        try {
            battle.put("mode", "grid"); battle.put("eventId", id); battle.put("battleId", UUID.randomUUID().toString().substring(0, 8));
            battle.put("regionId", player.optString("currentRegion", "harbor")); battle.put("enemyName", event.optString("enemy"));
            battle.put("enemyHp", event.optInt("enemyHp", 10)); battle.put("enemyMaxHp", event.optInt("enemyHp", 10));
            battle.put("enemyAttack", event.optInt("enemyAttack", 2)); battle.put("turn", 0);
            battle.put("xpReward", event.optInt("xp", 20)); battle.put("goldReward", event.optInt("gold", 5)); battle.put("loot", event.optString("loot", ""));
            player.put("battle", battle); savePlayer(chatId, player);
        } catch (Exception ignored) { }
        showOverworldBattle(token, chatId, world, player, "Найди уязвимость противника.");
    }

    private void startGridHunt(String token, long chatId, JSONObject world, JSONObject player) {
        String regionId = regionForGrid(player.optInt("gridX", 50), player.optInt("gridY", 50));
        JSONObject region = findRegion(world, regionId);
        if (region == null || region.optString("enemy", "").isEmpty()) { showGridMap(token, chatId, world, player, "В безопасной зоне никто не нападает. Сделай шаг дальше от Нордхейма."); return; }
        JSONObject battle = new JSONObject();
        try {
            int level = player.optInt("level", 1);
            battle.put("mode", "grid"); battle.put("eventId", ""); battle.put("battleId", UUID.randomUUID().toString().substring(0, 8));
            battle.put("regionId", regionId); battle.put("enemyName", region.optString("enemy"));
            battle.put("enemyHp", region.optInt("enemyHp", 8)); battle.put("enemyMaxHp", region.optInt("enemyHp", 8));
            battle.put("enemyAttack", region.optInt("enemyAttack", 2)); battle.put("turn", 0);
            battle.put("xpReward", region.optInt("xp", 20) + level * 3); battle.put("goldReward", region.optInt("gold", 5));
            battle.put("loot", region.optString("loot", "")); player.put("battle", battle); savePlayer(chatId, player);
        } catch (Exception ignored) { }
        showOverworldBattle(token, chatId, world, player, "Ты выследил противника в окрестностях.");
    }

    private JSONObject findRegion(JSONObject world, String regionId) {
        JSONArray regions = world == null ? null : world.optJSONArray("regions");
        if (regions == null) return null;
        for (int i = 0; i < regions.length(); i++) {
            JSONObject region = regions.optJSONObject(i);
            if (region != null && regionId.equals(region.optString("id", ""))) return region;
        }
        return null;
    }

    private void handleWorldAction(String token, long chatId, String action) {
        JSONObject world = activeWorld();
        JSONObject player = loadPlayer(chatId, world, false);
        if (action.equals("map")) { showGridMap(token, chatId, world, player, ""); return; }
        if (action.equals("character")) { showCharacter(token, chatId, player); return; }
        if (action.equals("inventory")) { showInventory(token, chatId, player); return; }
        if (action.equals("wardrobe")) { showWardrobe(token, chatId, player); return; }
        if (action.equals("quests")) { showQuests(token, chatId, player); return; }
        if (action.startsWith("questpage:")) { try { showQuestPage(token, chatId, player, Integer.parseInt(action.substring(10))); } catch (Exception ignored) { showQuests(token, chatId, player); } return; }
        if (action.equals("skin")) { showSkinSelector(token, chatId, player); return; }
        if (action.equals("story")) {
            try { player.put("currentMode", "story"); savePlayer(chatId, player); } catch (Exception ignored) { }
            String node = player.optString("currentNode", ""); if (node.isEmpty()) node = startNodeId(world);
            sendScene(token, chatId, world, node, true); return;
        }
        String[] parts = action.split(":", 3);
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "move": if (parts.length > 1) travelTo(token, chatId, world, player, parts[1]); break;
            case "hunt": startOverworldFight(token, chatId, world, player); break;
            case "gather": gatherResources(token, chatId, world, player); break;
            case "rest": restAtHarbor(token, chatId, world, player); break;
            case "buy": buyItem(token, chatId, world, player, parts.length > 1 ? parts[1] : ""); break;
            case "potion": drinkPotion(token, chatId, world, player); break;
            case "equip": if (parts.length > 1) equipGear(token, chatId, world, player, parts[1]); break;
            case "skin": if (parts.length > 1) changeSkin(token, chatId, world, player, parts[1]); break;
            case "craft": craftGear(token, chatId, world, player, parts.length > 1 ? parts[1] : ""); break;
            case "quest": if (parts.length > 2) updateQuest(token, chatId, world, player, parts[1], parts[2]); break;
            default: showWorldMap(token, chatId, world, player); break;
        }
    }

    private void showWorldMap(String token, long chatId, JSONObject world, JSONObject player) {
        if (world == null || world.optJSONArray("regions") == null) { sendText(token, chatId, "Открой кампанию в APK, чтобы загрузить карту архипелага."); return; }
        String currentId = player.optString("currentRegion", "harbor");
        JSONObject current = findRegion(world, currentId);
        if (current == null) { currentId = "harbor"; current = findRegion(world, currentId); }
        JSONArray visited = player.optJSONArray("visitedRegions");
        StringBuilder text = new StringBuilder("КАРТА ПОЛУНОЧНОГО АРХИПЕЛАГА\n\n");
        text.append("Сейчас: ").append(current == null ? "Нордхейм" : current.optString("title"));
        text.append("\nОткрыто мест: ").append(visited == null ? 1 : visited.length()).append(" / ")
            .append(world.optJSONArray("regions").length()).append("\n\nВыбери соседнюю область, чтобы отправиться туда. На карте путь открывается только после исследования соседней локации.");
        text.append("\n\n").append(playerSummary(player));
        text.append("\n\nИзведанные места:");
        if (visited != null) for (int i = 0; i < visited.length(); i++) {
            JSONObject region = findRegion(world, visited.optString(i, ""));
            if (region != null) text.append("\n• ").append(region.optString("title"));
        }
        JSONArray keyboard = new JSONArray();
        try {
            JSONObject activeBattle = player.optJSONObject("battle");
            if (activeBattle != null && "overworld".equals(activeBattle.optString("mode", ""))) {
                addButtonRow(keyboard, "⚔️ Вернуться в бой", "ow:hunt");
            } else if (current != null) {
                JSONArray exits = current.optJSONArray("exits");
                if (exits != null) for (int i = 0; i < exits.length(); i++) {
                    JSONObject next = findRegion(world, exits.optString(i, ""));
                    if (next == null) continue;
                    String prefix = contains(visited, next.optString("id", "")) ? "↩ " : "➜ ";
                    String label = prefix + next.optString("title", "Остров");
                    addButtonRow(keyboard, label, "ow:move:" + next.optString("id"));
                }
            }
            addButtonRow(keyboard, "📍 Осмотреться здесь", "ow:move:" + currentId);
            addButtonRow(keyboard, "📜 Журнал заданий", "ow:quests");
            addButtonRow(keyboard, "👤 Герой", "ow:character");
            addButtonRow(keyboard, "📖 Продолжить главную историю", "ow:story");
        } catch (Exception ignored) { }
        try { player.put("currentMode", "world"); savePlayer(chatId, player); } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard);
    }

    private void travelTo(String token, long chatId, JSONObject world, JSONObject player, String targetId) {
        JSONObject battle = player.optJSONObject("battle");
        if (battle != null && "overworld".equals(battle.optString("mode", ""))) { showOverworldBattle(token, chatId, world, player, "Сначала заверши бой или отступи."); return; }
        String currentId = player.optString("currentRegion", "harbor");
        if (currentId.equals(targetId)) { showRegion(token, chatId, world, targetId, ""); return; }
        JSONObject current = findRegion(world, currentId);
        JSONArray exits = current == null ? null : current.optJSONArray("exits");
        if (exits == null || !contains(exits, targetId)) { sendText(token, chatId, "Сюда пока нет проложенного пути. Исследуй соседние области на карте."); return; }
        showRegion(token, chatId, world, targetId, "Ты добрался до новой области.");
    }

    private void showRegion(String token, long chatId, JSONObject world, String regionId, String notice) {
        JSONObject player = loadPlayer(chatId, world, false);
        JSONObject region = findRegion(world, regionId);
        if (region == null) { showWorldMap(token, chatId, world, player); return; }
        JSONArray visited = player.optJSONArray("visitedRegions");
        boolean firstVisit = !contains(visited, regionId);
        if (firstVisit) addString(visited, regionId);
        try { player.put("currentRegion", regionId); player.put("currentMode", "world"); savePlayer(chatId, player); } catch (Exception ignored) { }
        StringBuilder text = new StringBuilder(region.optString("title", "Неизвестный остров"));
        text.append("\n\n").append(region.optString("text", ""));
        text.append("\n\nРекомендуемый уровень: ").append(region.optInt("minLevel", 1));
        text.append("\nТвой уровень: ").append(player.optInt("level", 1));
        if (firstVisit) text.append("\n\n✨ Новая область открыта! Исследуй её и отметь на карте.");
        if (!notice.isEmpty()) text.append("\n\n").append(notice);
        String enemy = region.optString("enemy", "");
        if (!enemy.isEmpty()) text.append("\n\nОпасность: ").append(enemy).append(" · победа приносит опыт, монеты и шанс найти экипировку.");
        if ("harbor".equals(regionId)) text.append("\n\nВ порту можно восстановить силы и купить зелья.");
        text.append("\n\n").append(playerSummary(player));
        JSONArray keyboard = new JSONArray();
        try {
            if (player.optJSONObject("battle") != null && "overworld".equals(player.optJSONObject("battle").optString("mode", "")))
                addButtonRow(keyboard, "⚔️ Вернуться в текущий бой", "ow:hunt");
            else if (!enemy.isEmpty()) addButtonRow(keyboard, "⚔️ Охотиться на моба", "ow:hunt");
            if ("mine".equals(regionId) || "crystal_cave".equals(regionId) || "shipwreck".equals(regionId) || "observatory".equals(regionId))
                addButtonRow(keyboard, "⛏ Исследовать и собирать ресурсы", "ow:gather");
            if ("harbor".equals(regionId)) {
                addButtonRow(keyboard, "🧪 Купить зелье · 18 монет", "ow:buy:potion");
                addButtonRow(keyboard, "🛏 Отдохнуть · 8 монет", "ow:rest");
                addButtonRow(keyboard, "🧭 Доска заданий", "ow:quests");
            }
            addButtonRow(keyboard, "🗺 Открыть карту и пути", "ow:map");
            addButtonRow(keyboard, "📜 Журнал заданий", "ow:quests");
            addButtonRow(keyboard, "🎒 Рюкзак и находки", "ow:inventory");
            addButtonRow(keyboard, "👤 Персонаж · скины · одежда", "ow:character");
        } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard);
    }

    private void startOverworldFight(String token, long chatId, JSONObject world, JSONObject player) {
        JSONObject existing = player.optJSONObject("battle");
        if (existing != null && "overworld".equals(existing.optString("mode", ""))) { showOverworldBattle(token, chatId, world, player, ""); return; }
        String regionId = player.optString("currentRegion", "harbor");
        JSONObject region = findRegion(world, regionId);
        if (region == null || region.optString("enemy", "").isEmpty()) { showRegion(token, chatId, world, regionId, "Здесь сейчас нет противников."); return; }
        if ("heart_peak".equals(regionId) && (player.optInt("level", 1) < 6 || player.optJSONObject("quests") == null || !"done".equals(player.optJSONObject("quests").optString("stars", "")))) {
            showRegion(token, chatId, world, regionId, "Вейр пока недоступен: достигни 6 уровня и заверши задание «Глаз звездочёта»."); return;
        }
        JSONObject battle = new JSONObject();
        try {
            battle.put("mode", "overworld"); battle.put("battleId", UUID.randomUUID().toString().substring(0, 8));
            battle.put("regionId", regionId); battle.put("enemyName", region.optString("enemy"));
            battle.put("enemyHp", region.optInt("enemyHp", 8)); battle.put("enemyAttack", region.optInt("enemyAttack", 2));
            battle.put("enemyMaxHp", region.optInt("enemyHp", 8)); battle.put("turn", 0);
            battle.put("xpReward", region.optInt("xp", 20)); battle.put("goldReward", region.optInt("gold", 5));
            battle.put("loot", region.optString("loot", "")); player.put("battle", battle); savePlayer(chatId, player);
        } catch (Exception ignored) { }
        showOverworldBattle(token, chatId, world, player, "");
    }

    private void showOverworldBattle(String token, long chatId, JSONObject world, JSONObject player, String notice) {
        JSONObject battle = player.optJSONObject("battle");
        if (battle == null) { showRegion(token, chatId, world, player.optString("currentRegion", "harbor"), ""); return; }
        StringBuilder text = new StringBuilder("СРАЖЕНИЕ · ").append(battle.optString("enemyName", "Противник"));
        text.append("\n\nЗдоровье врага: ").append(Math.max(0, battle.optInt("enemyHp", 0))).append(" / ").append(battle.optInt("enemyMaxHp", 1));
        text.append("\nТвоё здоровье: ").append(player.optInt("hp", 12)).append(" / ").append(player.optInt("maxHp", 12));
        text.append("\nУрон героя: ").append(playerAttack(player)).append(" · защита: ").append(playerDefense(player));
        text.append("\n\n").append(notice.isEmpty() ? "Выбери действие. Каждый удар противника отнимает здоровье." : notice);
        JSONArray keyboard = new JSONArray();
        try {
            String battleId = battle.optString("battleId", ""); int turn = battle.optInt("turn", 0);
            addButtonRow(keyboard, "⚔️ Ударить", "battle:" + battleId + ":" + turn + ":attack");
            addButtonRow(keyboard, "🧪 Выпить зелье", "battle:" + battleId + ":" + turn + ":potion");
            addButtonRow(keyboard, "🏃 Отступить", "battle:" + battleId + ":" + turn + ":flee");
        } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard);
    }

    private void handleOverworldBattleAction(String token, long chatId, JSONObject world, JSONObject player, String expectedId, int expectedTurn, String action) {
        JSONObject battle = player.optJSONObject("battle");
        if (battle == null || !expectedId.equals(battle.optString("battleId", "")) || expectedTurn != battle.optInt("turn", 0)) return;
        String regionId = battle.optString("regionId", "harbor");
        boolean gridBattle = "grid".equals(battle.optString("mode", ""));
        if ("flee".equals(action)) {
            player.remove("battle"); savePlayer(chatId, player);
            if (gridBattle) showGridMap(token, chatId, world, player, "Ты отступил и сохранил добычу.");
            else showRegion(token, chatId, world, regionId, "Ты отступил и сохранил добычу.");
            return;
        }
        if ("potion".equals(action)) {
            if (!removeItem(player, "Зелье северной мяты")) { showOverworldBattle(token, chatId, world, player, "В рюкзаке нет зелий. Вернись в Нордхейм."); return; }
            try { player.put("hp", Math.min(player.optInt("maxHp", 12), player.optInt("hp", 12) + 8)); } catch (Exception ignored) { }
        } else if ("attack".equals(action)) {
            int enemyHp = battle.optInt("enemyHp", 1) - playerAttack(player);
            try { battle.put("enemyHp", enemyHp); } catch (Exception ignored) { }
            if (enemyHp <= 0) {
                int previousKills = getCount(player.optJSONObject("kills"), regionId);
                incrementCount(player, "kills", regionId, 1);
                String eventId = battle.optString("eventId", "");
                if (!eventId.isEmpty()) addString(player.optJSONArray("resolvedGridEvents"), eventId);
                player.remove("battle");
                try { player.put("gold", player.optInt("gold") + battle.optInt("goldReward", 0)); } catch (Exception ignored) { }
                grantExperience(player, battle.optInt("xpReward", 0));
                String loot = battle.optString("loot", "");
                String lootNotice = "Победа! +" + battle.optInt("xpReward") + " опыта, +" + battle.optInt("goldReward") + " монет.";
                if (!loot.isEmpty() && (previousKills == 0 || Math.random() < 0.28) && grantGear(player, loot)) lootNotice += " Найдена экипировка: " + gearName(loot) + "! Её можно надеть в /wardrobe.";
                savePlayer(chatId, player);
                if (gridBattle) showGridMap(token, chatId, world, player, lootNotice);
                else showRegion(token, chatId, world, regionId, lootNotice);
                return;
            }
        } else return;
        int incoming = Math.max(1, battle.optInt("enemyAttack", 1) - playerDefense(player));
        int hp = player.optInt("hp", 12) - incoming;
        if (hp <= 0) {
            try { player.put("hp", Math.max(5, player.optInt("maxHp", 12) / 2)); } catch (Exception ignored) { }
            player.remove("battle"); savePlayer(chatId, player);
            String defeatText = "Ты отступил к безопасной клетке. Враг остался жив — восстановись и попробуй снова.";
            if (gridBattle) showGridMap(token, chatId, world, player, defeatText);
            else showRegion(token, chatId, world, regionId, defeatText);
            return;
        }
        try { battle.put("turn", battle.optInt("turn", 0) + 1); player.put("hp", hp); player.put("battle", battle); } catch (Exception ignored) { }
        savePlayer(chatId, player);
        showOverworldBattle(token, chatId, world, player, "Враг ударил на " + incoming + " урона.");
    }

    private int playerAttack(JSONObject player) {
        int value = 2 + Math.max(0, player.optInt("level", 1) - 1);
        JSONObject equipment = player.optJSONObject("equipment");
        if (equipment != null) for (String slot : new String[] {"weapon", "armor", "charm"}) value += gearStat(equipment.optString(slot, ""), "attack");
        if (hasItem(player, "Сигнальный гарпун")) value += 2;
        return value;
    }

    private int playerDefense(JSONObject player) {
        int value = Math.max(0, player.optInt("level", 1) / 2);
        JSONObject equipment = player.optJSONObject("equipment");
        if (equipment != null) for (String slot : new String[] {"weapon", "armor", "charm"}) value += gearStat(equipment.optString(slot, ""), "defense");
        return value;
    }

    private void grantExperience(JSONObject player, int amount) {
        try {
            int xp = player.optInt("xp", 0) + Math.max(0, amount);
            int level = player.optInt("level", 1); int gained = 0;
            while (xp >= level * 70 && level < 20) { xp -= level * 70; level++; gained++; }
            player.put("xp", xp); player.put("level", level);
            if (gained > 0) {
                int maxHp = player.optInt("maxHp", 12) + gained * 3;
                player.put("maxHp", maxHp); player.put("hp", maxHp);
                unlockSkins(player);
                player.put("lastLevelGain", gained);
            }
        } catch (Exception ignored) { }
    }

    private void showCharacter(String token, long chatId, JSONObject player) {
        JSONObject equipment = player.optJSONObject("equipment");
        String skin = skinName(player.optString("skin", "wanderer"));
        int nextLevel = player.optInt("level", 1) * 70;
        String text = "ПЕРСОНАЖ · " + skin + "\n\nУровень " + player.optInt("level", 1) + " · опыт " + player.optInt("xp", 0) + "/" + nextLevel
            + "\n❤️ Здоровье: " + player.optInt("hp", 12) + "/" + player.optInt("maxHp", 12)
            + "\n⚔️ Атака: " + playerAttack(player) + " · 🛡 защита: " + playerDefense(player)
            + "\n🪙 Монеты: " + player.optInt("gold")
            + "\n\nНадето:\nОружие — " + gearName(equipment == null ? "" : equipment.optString("weapon", ""))
            + "\nОдежда — " + gearName(equipment == null ? "" : equipment.optString("armor", ""))
            + "\nАксессуар — " + gearName(equipment == null ? "" : equipment.optString("charm", ""))
            + "\n\nОпыт дают за победы над мобами и выполнение квестов. При новом уровне растут здоровье и сила, открываются новые облики.";
        JSONArray keyboard = new JSONArray();
        try { addButtonRow(keyboard, "👘 Сменить скин", "ow:skin"); addButtonRow(keyboard, "🛡 Гардероб и экипировка", "ow:wardrobe"); addButtonRow(keyboard, "🗺 Карта", "ow:map"); }
        catch (Exception ignored) { }
        sendPage(token, chatId, text, keyboard, skinImageName(player.optString("skin", "wanderer")));
    }

    private void showInventory(String token, long chatId, JSONObject player) {
        StringBuilder text = new StringBuilder("РЮКЗАК И ДОБЫЧА\n\nПредметы: ").append(inventoryText(player));
        JSONArray gear = player.optJSONArray("gear");
        text.append("\n\nЭкипировка в коллекции: ");
        if (gear == null || gear.length() == 0) text.append("пока нет — побеждай мобов, чтобы получить первую вещь.");
        else for (int i = 0; i < gear.length(); i++) { if (i > 0) text.append(", "); text.append(gearName(gear.optString(i))); }
        JSONObject resources = player.optJSONObject("resources");
        if (resources != null) text.append("\n\nРесурсы: руда ").append(resources.optInt("ore", 0)).append(" · кристаллы ").append(resources.optInt("crystal", 0)).append(" · звёздная пыль ").append(resources.optInt("stardust", 0));
        JSONArray keyboard = new JSONArray();
        try { addButtonRow(keyboard, "🧪 Выпить зелье", "ow:potion"); addButtonRow(keyboard, "👘 Гардероб", "ow:wardrobe"); addButtonRow(keyboard, "🗺 Карта", "ow:map"); }
        catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard);
    }

    private void showWardrobe(String token, long chatId, JSONObject player) {
        JSONArray gear = player.optJSONArray("gear"); JSONObject equipment = player.optJSONObject("equipment");
        StringBuilder text = new StringBuilder("ГАРДЕРОБ ПУТЕШЕСТВЕННИКА\n\nНадень найденные вещи, чтобы увеличить урон и защиту.");
        JSONArray keyboard = new JSONArray();
        try {
            if (gear != null) for (int i = 0; i < gear.length(); i++) {
                String id = gear.optString(i, "");
                JSONObject item = gearInfo(id);
                String slot = item == null ? "" : item.optString("slot", "");
                String worn = equipment != null && id.equals(equipment.optString(slot, "")) ? " · надето" : "";
                addButtonRow(keyboard, "Надеть: " + gearName(id) + worn, "ow:equip:" + id);
            }
            addButtonRow(keyboard, "⚒ Рунный клинок · 4 руды", "ow:craft:blade");
            addButtonRow(keyboard, "🧥 Кристальная броня · 3 кристалла", "ow:craft:armor");
            addButtonRow(keyboard, "🔮 Амулет звёзд · 5 пыли", "ow:craft:amulet");
            addButtonRow(keyboard, "🗺 Карта", "ow:map");
        } catch (Exception ignored) { }
        if (gear == null || gear.length() == 0) text.append("\n\nПобеди моба в Шепчущем лесу — первый трофей выпадет гарантированно.");
        sendPage(token, chatId, text.toString(), keyboard, skinImageName(player.optString("skin", "wanderer")));
    }

    private void showSkinSelector(String token, long chatId, JSONObject player) {
        String[] ids = {"wanderer", "ranger", "arctic", "scholar", "storm", "royal"};
        String[] names = {"Странник", "Следопыт", "Полярник", "Хранитель рун", "Штормовой", "Северный князь"};
        String[] icons = {"🧥", "🏹", "❄️", "🔮", "⚡", "👑"};
        int[] levels = {1, 2, 3, 4, 5, 7};
        StringBuilder text = new StringBuilder("ОБЛИКИ ГЕРОЯ\n\nВыбери образ. Часть внешности открывается с новыми уровнями; стартовый образ задаётся в APK.");
        JSONArray keyboard = new JSONArray();
        try {
            for (int i = 0; i < ids.length; i++) {
                boolean unlocked = contains(player.optJSONArray("skins"), ids[i]) || player.optInt("level", 1) >= levels[i];
                text.append("\n").append(unlocked ? icons[i] : "🔒").append(" ").append(names[i]).append(unlocked ? "" : " · уровень " + levels[i]);
                if (unlocked) addButtonRow(keyboard, (ids[i].equals(player.optString("skin")) ? "✓ " : "") + icons[i] + " " + names[i], "ow:skin:" + ids[i]);
            }
            addButtonRow(keyboard, "👤 Карточка персонажа", "ow:character");
        } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard, skinImageName(player.optString("skin", "wanderer")));
    }

    private void showQuests(String token, long chatId, JSONObject player) { showQuestPage(token, chatId, player, 0); }

    private void showQuestPage(String token, long chatId, JSONObject player, int page) {
        String[][] quests = questSpecs(); int pages = (quests.length + 3) / 4; page = Math.max(0, Math.min(page, pages - 1));
        StringBuilder text = new StringBuilder("ЖУРНАЛ · СТРАНИЦА ").append(page + 1).append(" / ").append(pages)
            .append("\n\nЗадания можно принимать параллельно и проходить в своём порядке.");
        JSONArray keyboard = new JSONArray(); JSONObject states = player.optJSONObject("quests");
        try {
            int first = page * 4, last = Math.min(first + 4, quests.length);
            for (int i = first; i < last; i++) {
                String[] quest = quests[i]; String status = states == null ? "" : states.optString(quest[0], "");
                String progress = questProgress(player, quest[0]);
                text.append("\n\n").append(status.equals("done") ? "✅ " : status.equals("active") ? "📜 " : "▫ ").append(quest[1]);
                text.append("\n").append(quest[2]).append("\nПрогресс: ").append(progress);
                if (status.isEmpty()) addButtonRow(keyboard, "Принять: " + quest[1], "ow:quest:accept:" + quest[0]);
                else if (status.equals("active") && questComplete(player, quest[0])) addButtonRow(keyboard, "Сдать: " + quest[1], "ow:quest:claim:" + quest[0]);
            }
            if (page > 0) addButtonRow(keyboard, "◀ Предыдущие поручения", "ow:questpage:" + (page - 1));
            if (page + 1 < pages) addButtonRow(keyboard, "Ещё поручения ▶", "ow:questpage:" + (page + 1));
            addButtonRow(keyboard, "🗺 Вернуться на карту", "ow:map");
        } catch (Exception ignored) { }
        sendPage(token, chatId, text.toString(), keyboard);
    }

    private String[][] questSpecs() {
        return new String[][] {
            {"survey", "Карты не врут", "Открой 6 областей архипелага.", "180", "95", "silver_compass"},
            {"wolves", "Тихий лес", "Победи 5 волков в лесу и на утёсах.", "240", "110", "wolf_pelt"},
            {"surveyor", "Исчезнувший картограф", "Исследуй Забытую деревню и Мыс разбитых кораблей.", "210", "100", "guard_badge"},
            {"miners", "Голубой уголь", "Собери 4 куска руды в старой шахте.", "260", "130", "miner_pick"},
            {"beacon", "Пробуждение маяка", "Победи 3 дозорных у маяка без света.", "350", "180", "beacon_spear"},
            {"stars", "Глаз звездочёта", "Зажги маяк, исследуй перевал и обсерваторию, одолей 2 звёздных стражей.", "440", "220", "star_lens"},
            {"storm", "Через белую бурю", "Выиграй 3 боя на Острове белой бури.", "580", "300", "storm_ring"},
            {"wyrm", "Сердце архипелага", "Достигни 6 уровня и победи Вейра на вершине.", "900", "500", "wyrm_armor"}
        };
    }

    private String questProgress(JSONObject player, String id) {
        JSONObject kills = player.optJSONObject("kills"); JSONArray visited = player.optJSONArray("visitedRegions"); JSONObject resources = player.optJSONObject("resources");
        switch (id) {
            case "survey": return Math.min(visited == null ? 0 : visited.length(), 6) + " / 6 областей";
            case "wolves": return (getCount(kills, "forest") + getCount(kills, "wolf_cliffs")) + " / 5 побед";
            case "surveyor": return (contains(visited, "village") ? 1 : 0) + " / 1 деревня · " + (contains(visited, "shipwreck") ? 1 : 0) + " / 1 мыс";
            case "miners": return (resources == null ? 0 : resources.optInt("ore", 0)) + " / 4 руды";
            case "beacon": return getCount(kills, "lighthouse") + " / 3 побед";
            case "stars": return ((player.optJSONObject("quests") != null && "done".equals(player.optJSONObject("quests").optString("beacon", ""))) ? "маяк ✅" : "нужен маяк") + " · " + (contains(visited, "high_pass") ? 1 : 0) + " / 1 перевал · " + (contains(visited, "observatory") ? 1 : 0) + " / 1 обсерватория · " + getCount(kills, "observatory") + " / 2 победы";
            case "storm": return getCount(kills, "storm_isle") + " / 3 побед";
            case "wyrm": return player.optInt("level", 1) + " ур. · " + getCount(kills, "heart_peak") + " / 1 дракон";
            default: return "—";
        }
    }

    private boolean questComplete(JSONObject player, String id) {
        JSONObject kills = player.optJSONObject("kills"); JSONArray visited = player.optJSONArray("visitedRegions"); JSONObject resources = player.optJSONObject("resources");
        switch (id) {
            case "survey": return visited != null && visited.length() >= 6;
            case "wolves": return getCount(kills, "forest") + getCount(kills, "wolf_cliffs") >= 5;
            case "surveyor": return contains(visited, "village") && contains(visited, "shipwreck");
            case "miners": return resources != null && resources.optInt("ore", 0) >= 4;
            case "beacon": return getCount(kills, "lighthouse") >= 3;
            case "stars": return player.optJSONObject("quests") != null && "done".equals(player.optJSONObject("quests").optString("beacon", "")) && contains(visited, "high_pass") && contains(visited, "observatory") && getCount(kills, "observatory") >= 2;
            case "storm": return getCount(kills, "storm_isle") >= 3;
            case "wyrm": return player.optInt("level", 1) >= 6 && getCount(kills, "heart_peak") >= 1;
            default: return false;
        }
    }

    private void updateQuest(String token, long chatId, JSONObject world, JSONObject player, String verb, String id) {
        JSONObject states = player.optJSONObject("quests"); if (states == null) states = new JSONObject();
        String[][] specs = questSpecs(); String[] found = null;
        for (String[] spec : specs) if (id.equals(spec[0])) found = spec;
        if (found == null) return;
        String status = states.optString(id, "");
        if ("accept".equals(verb) && status.isEmpty()) {
            try { states.put(id, "active"); player.put("quests", states); savePlayer(chatId, player); } catch (Exception ignored) { }
            showQuests(token, chatId, player); return;
        }
        if ("claim".equals(verb) && status.equals("active") && questComplete(player, id)) {
            try {
                states.put(id, "done"); player.put("quests", states);
                player.put("gold", player.optInt("gold") + Integer.parseInt(found[4]));
                grantExperience(player, Integer.parseInt(found[3])); grantGear(player, found[5]);
                savePlayer(chatId, player);
            } catch (Exception ignored) { }
            showQuests(token, chatId, player); return;
        }
        showQuests(token, chatId, player);
    }

    private void gatherResources(String token, long chatId, JSONObject world, JSONObject player) {
        String region = player.optString("currentRegion", "");
        String resource = "ore"; String label = "синий уголь";
        if ("crystal_cave".equals(region)) { resource = "crystal"; label = "ледяной кристалл"; }
        else if ("observatory".equals(region)) { resource = "stardust"; label = "звёздную пыль"; }
        else if ("shipwreck".equals(region)) { resource = "scrap"; label = "обломки корабля"; }
        int amount = 1 + (int)(Math.random() * 2);
        JSONObject resources = player.optJSONObject("resources"); if (resources == null) resources = new JSONObject();
        try { resources.put(resource, resources.optInt(resource, 0) + amount); player.put("resources", resources); savePlayer(chatId, player); }
        catch (Exception ignored) { }
        showRegion(token, chatId, world, region, "Ты собрал " + amount + " ед. ресурса: " + label + ". Их можно использовать для изготовления экипировки.");
    }

    private void restAtHarbor(String token, long chatId, JSONObject world, JSONObject player) {
        if (!"harbor".equals(player.optString("currentRegion", ""))) { showRegion(token, chatId, world, player.optString("currentRegion", "harbor"), "Отдохнуть можно только в трактире Нордхейма."); return; }
        int cost = 8;
        if (player.optInt("gold", 0) < cost) { showRegion(token, chatId, world, "harbor", "Нужно 8 монет, чтобы снять комнату в трактире."); return; }
        try { player.put("gold", player.optInt("gold") - cost); player.put("hp", player.optInt("maxHp", 12)); savePlayer(chatId, player); } catch (Exception ignored) { }
        showRegion(token, chatId, world, "harbor", "Ты выспался и полностью восстановил здоровье.");
    }

    private void buyItem(String token, long chatId, JSONObject world, JSONObject player, String item) {
        if (!"potion".equals(item)) return;
        if (!"harbor".equals(player.optString("currentRegion", ""))) { showRegion(token, chatId, world, player.optString("currentRegion", "harbor"), "Припасами торгуют только в Нордхейме."); return; }
        if (player.optInt("gold", 0) < 18) { showRegion(token, chatId, world, "harbor", "Не хватает монет: зелье стоит 18."); return; }
        try { player.put("gold", player.optInt("gold") - 18); addItem(player, "Зелье северной мяты"); savePlayer(chatId, player); } catch (Exception ignored) { }
        showRegion(token, chatId, world, "harbor", "Ты купил зелье северной мяты. Оно восстанавливает 8 здоровья в бою.");
    }

    private void drinkPotion(String token, long chatId, JSONObject world, JSONObject player) {
        if (!removeItem(player, "Зелье северной мяты")) { showInventory(token, chatId, player); return; }
        try { player.put("hp", Math.min(player.optInt("maxHp", 12), player.optInt("hp", 12) + 8)); savePlayer(chatId, player); } catch (Exception ignored) { }
        showCharacter(token, chatId, player);
    }

    private void equipGear(String token, long chatId, JSONObject world, JSONObject player, String gearId) {
        if (!contains(player.optJSONArray("gear"), gearId)) { showWardrobe(token, chatId, player); return; }
        JSONObject data = gearInfo(gearId); JSONObject equipment = player.optJSONObject("equipment"); if (equipment == null) equipment = new JSONObject();
        try { equipment.put(data.optString("slot", "armor"), gearId); player.put("equipment", equipment); savePlayer(chatId, player); } catch (Exception ignored) { }
        showWardrobe(token, chatId, player);
    }

    private void changeSkin(String token, long chatId, JSONObject world, JSONObject player, String skin) {
        String[] ids = {"wanderer", "ranger", "arctic", "scholar", "storm", "royal"}; int[] levels = {1, 2, 3, 4, 5, 7}; int required = 99;
        for (int i = 0; i < ids.length; i++) if (skin.equals(ids[i])) required = levels[i];
        boolean unlocked = contains(player.optJSONArray("skins"), skin) || player.optInt("level", 1) >= required;
        if (!unlocked) { showSkinSelector(token, chatId, player); return; }
        addString(player.optJSONArray("skins"), skin);
        try { player.put("skin", skin); savePlayer(chatId, player); } catch (Exception ignored) { }
        showCharacter(token, chatId, player);
    }

    private void unlockSkins(JSONObject player) {
        String[] ids = {"wanderer", "ranger", "arctic", "scholar", "storm", "royal"}; int[] levels = {1, 2, 3, 4, 5, 7};
        JSONArray skins = player.optJSONArray("skins");
        if (skins == null) { skins = new JSONArray(); try { player.put("skins", skins); } catch (Exception ignored) { } }
        for (int i = 0; i < ids.length; i++) if (player.optInt("level", 1) >= levels[i]) addString(skins, ids[i]);
    }

    private String skinName(String id) {
        switch (id) {
            case "ranger": return "🏹 Следопыт"; case "arctic": return "❄️ Полярник"; case "scholar": return "🔮 Хранитель рун";
            case "storm": return "⚡ Штормовой"; case "royal": return "👑 Северный князь"; default: return "🧥 Странник";
        }
    }

    private String skinImageName(String id) {
        if ("ranger".equals(id) || "arctic".equals(id) || "scholar".equals(id) || "storm".equals(id) || "royal".equals(id)) return "skin-" + id + ".jpg";
        return "skin-wanderer.jpg";
    }

    private void craftGear(String token, long chatId, JSONObject world, JSONObject player, String recipe) {
        JSONObject resources = player.optJSONObject("resources");
        if (resources == null) { showWardrobe(token, chatId, player); return; }
        String resource; String id; int cost;
        if ("armor".equals(recipe)) { resource = "crystal"; id = "crystal_armor"; cost = 3; }
        else if ("amulet".equals(recipe)) { resource = "stardust"; id = "star_amulet"; cost = 5; }
        else { resource = "ore"; id = "rune_blade"; cost = 4; }
        if (resources.optInt(resource, 0) < cost || contains(player.optJSONArray("gear"), id)) { showWardrobe(token, chatId, player); return; }
        try { resources.put(resource, resources.optInt(resource) - cost); player.put("resources", resources); grantGear(player, id); savePlayer(chatId, player); } catch (Exception ignored) { }
        showWardrobe(token, chatId, player);
    }

    private String gearName(String id) {
        JSONObject data = gearInfo(id); return data == null || id == null || id.isEmpty() ? "не надето" : data.optString("name", id);
    }

    private JSONObject gearInfo(String id) {
        String[][] rows = {
            {"wolf_fang", "Клинок волчьего клыка", "weapon", "2", "0"}, {"wolf_pelt", "Плащ из волчьей шкуры", "armor", "0", "2"},
            {"silver_compass", "Компас Первого морехода", "charm", "1", "1"}, {"miner_pick", "Кирка синего угля", "weapon", "3", "0"},
            {"crystal_mail", "Кристальная кольчуга", "armor", "1", "3"}, {"guard_badge", "Знак старого дозора", "charm", "1", "2"},
            {"beacon_spear", "Копьё маячного стража", "weapon", "4", "1"}, {"giant_mantle", "Плащ ледяного великана", "armor", "1", "4"},
            {"star_lens", "Линза звёздочёта", "charm", "3", "1"}, {"storm_ring", "Кольцо белой бури", "charm", "4", "2"},
            {"wyrm_armor", "Доспех из чешуи Вейра", "armor", "2", "6"}, {"rune_blade", "Рунный клинок синего угля", "weapon", "6", "1"},
            {"crystal_armor", "Кристальная броня", "armor", "1", "5"}, {"star_amulet", "Амулет звёзд", "charm", "5", "2"}
        };
        for (String[] row : rows) if (row[0].equals(id)) {
            JSONObject result = new JSONObject();
            try { result.put("id", row[0]); result.put("name", row[1]); result.put("slot", row[2]); result.put("attack", Integer.parseInt(row[3])); result.put("defense", Integer.parseInt(row[4])); }
            catch (Exception ignored) { }
            return result;
        }
        return null;
    }

    private int gearStat(String id, String stat) {
        JSONObject gear = gearInfo(id); return gear == null ? 0 : gear.optInt(stat, 0);
    }

    private boolean grantGear(JSONObject player, String id) {
        if (gearInfo(id) == null || contains(player.optJSONArray("gear"), id)) return false;
        JSONArray gear = player.optJSONArray("gear"); if (gear == null) gear = new JSONArray();
        try { gear.put(id); player.put("gear", gear); return true; } catch (Exception ignored) { return false; }
    }

    private int getCount(JSONObject object, String key) { return object == null ? 0 : object.optInt(key, 0); }

    private void incrementCount(JSONObject player, String field, String key, int amount) {
        try { JSONObject counts = player.optJSONObject(field); if (counts == null) counts = new JSONObject(); counts.put(key, counts.optInt(key, 0) + amount); player.put(field, counts); }
        catch (Exception ignored) { }
    }

    private void sendPage(String token, long chatId, String text, JSONArray keyboard) { sendPage(token, chatId, text, keyboard, MAP_IMAGE); }

    private void sendPage(String token, long chatId, String text, JSONArray keyboard, String imageName) {
        JSONObject markup = new JSONObject();
        try { markup.put("inline_keyboard", keyboard == null ? new JSONArray() : keyboard); } catch (Exception ignored) { }
        sendGamePage(token, chatId, limit(text, 1000), markup, imageName);
    }

    private void addItem(JSONObject player, String item) {
        if (item == null || item.isEmpty()) return;
        JSONArray inventory = player.optJSONArray("inventory");
        if (inventory == null) { inventory = new JSONArray(); try { player.put("inventory", inventory); } catch (Exception ignored) { } }
        if (!contains(inventory, item) || "Зелье северной мяты".equals(item)) try { inventory.put(item); } catch (Exception ignored) { }
    }

    private boolean removeItem(JSONObject player, String item) {
        JSONArray inventory = player.optJSONArray("inventory");
        if (inventory == null) return false;
        JSONArray kept = new JSONArray(); boolean removed = false;
        for (int i = 0; i < inventory.length(); i++) {
            String value = inventory.optString(i, "");
            if (!removed && item.equals(value)) { removed = true; continue; }
            try { kept.put(value); } catch (Exception ignored) { }
        }
        try { player.put("inventory", kept); } catch (Exception ignored) { }
        return removed;
    }

    private boolean hasItem(JSONObject player, String item) { return contains(player.optJSONArray("inventory"), item); }

    private boolean contains(JSONArray values, String expected) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) if (expected.equals(values.optString(i, ""))) return true;
        return false;
    }

    private void addString(JSONArray values, String value) {
        if (values == null || value == null || value.isEmpty() || contains(values, value)) return;
        try { values.put(value); } catch (Exception ignored) { }
    }

    private String inventoryText(JSONObject player) {
        JSONArray items = player.optJSONArray("inventory");
        if (items == null || items.length() == 0) return "пусто";
        StringBuilder result = new StringBuilder(); int potions = 0;
        for (int i = 0; i < items.length(); i++) {
            String item = items.optString(i);
            if ("Зелье северной мяты".equals(item)) { potions++; continue; }
            if (result.length() > 0) result.append(", "); result.append(item);
        }
        if (potions > 0) { if (result.length() > 0) result.append(", "); result.append("Зелье северной мяты ×").append(potions); }
        return result.length() == 0 ? "пусто" : result.toString();
    }

    private String playerSummary(JSONObject player) {
        if (player == null) return "";
        int level = player.optInt("level", 1);
        return "❤️ " + player.optInt("hp", 12) + "/" + player.optInt("maxHp", 12) + "   ·   🪙 " + player.optInt("gold")
            + "   ·   ✨ ур. " + level + " · " + player.optInt("xp") + "/" + (level * 70)
            + "\n" + skinName(player.optString("skin", "wanderer")) + "   ·   ⚔️ " + playerAttack(player) + "   ·   🛡 " + playerDefense(player)
            + "\n🎒 " + inventoryText(player);
    }

    private byte[] readAsset(String assetName) throws Exception {
        if (assetName == null || !assetName.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid image asset name");
        try (InputStream input = getAssets().open("botcraft/" + assetName); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private JSONObject sendPhoto(String token, JSONObject payload, String imageName) throws Exception {
        byte[] photo = readAsset(imageName);
        String boundary = "Botcraft" + System.nanoTime();
        HttpURLConnection connection = (HttpURLConnection) new URL(api(token, "sendPhoto")).openConnection();
        try {
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream output = connection.getOutputStream()) {
                for (String field : new String[] {"chat_id", "caption", "reply_markup"}) {
                    if (!payload.has(field)) continue;
                    writeField(output, boundary, field, payload.optString(field, ""));
                }
                output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"" + imageName + "\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(photo); output.write("\r\n".getBytes(StandardCharsets.UTF_8));
                output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            JSONObject response = new JSONObject(readAll(connection.getInputStream()));
            if (!response.optBoolean("ok", false)) throw new IllegalStateException("Telegram sendPhoto rejected the image payload");
            return response;
        } finally { connection.disconnect(); }
    }

    private JSONObject editPhotoMessage(String token, long chatId, long messageId, String caption, JSONObject markup, String imageName) throws Exception {
        byte[] photo = readAsset(imageName);
        JSONObject media = new JSONObject(); media.put("type", "photo"); media.put("media", "attach://photo"); media.put("caption", limit(caption, 1000));
        String boundary = "BotcraftEdit" + System.nanoTime();
        HttpURLConnection connection = (HttpURLConnection) new URL(api(token, "editMessageMedia")).openConnection();
        try {
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream output = connection.getOutputStream()) {
                writeField(output, boundary, "chat_id", Long.toString(chatId));
                writeField(output, boundary, "message_id", Long.toString(messageId));
                writeField(output, boundary, "media", media.toString());
                if (markup != null) writeField(output, boundary, "reply_markup", markup.toString());
                output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"" + imageName + "\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(photo); output.write("\r\n".getBytes(StandardCharsets.UTF_8));
                output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            InputStream response = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (response == null) throw new IllegalStateException("Empty Telegram response");
            return new JSONObject(readAll(response));
        } finally { connection.disconnect(); }
    }

    private void sendGamePage(String token, long chatId, String caption, JSONObject markup, String imageName) {
        JSONObject world = activeWorld();
        JSONObject player = loadPlayer(chatId, world, false);
        long messageId = player.optLong("pageMessageId", 0L);
        String pageMode = player.optString("pageMode", "photo");
        boolean textOnly = imageName == null || imageName.isEmpty();
        String desiredImage = textOnly ? "" : imageName;
        if (messageId > 0 && ((textOnly && !"text".equals(pageMode)) || (!textOnly && "text".equals(pageMode)))) {
            try {
                JSONObject delete = new JSONObject(); delete.put("chat_id", chatId); delete.put("message_id", messageId);
                telegramPost(token, "deleteMessage", delete);
            } catch (Exception ignored) { }
            messageId = 0;
            try { player.put("pageMessageId", 0); player.put("pageMode", textOnly ? "text" : "photo"); savePlayer(chatId, player); } catch (Exception ignored) { }
        }
        if (messageId > 0) {
            JSONObject payload = new JSONObject();
            try {
                if (!"text".equals(pageMode) && !desiredImage.equals(player.optString("pageImage", ""))) {
                    JSONObject mediaEdit = editPhotoMessage(token, chatId, messageId, caption, markup, desiredImage);
                    if (mediaEdit.optBoolean("ok", false)) { player.put("pageImage", desiredImage); savePlayer(chatId, player); return; }
                }
                payload.put("chat_id", chatId); payload.put("message_id", messageId);
                payload.put("reply_markup", markup == null ? new JSONObject() : markup);
                if ("text".equals(pageMode)) payload.put("text", limit(caption, 4000));
                else payload.put("caption", limit(caption, 1000));
                JSONObject edited = telegramPost(token, "text".equals(pageMode) ? "editMessageText" : "editMessageCaption", payload);
                if (edited.optBoolean("ok", false) || isUnchanged(edited)) return;
                if (!"text".equals(pageMode)) {
                    payload.remove("caption"); payload.put("text", limit(caption, 4000));
                    JSONObject textEdit = telegramPost(token, "editMessageText", payload);
                    if (textEdit.optBoolean("ok", false) || isUnchanged(textEdit)) {
                        player.put("pageMode", "text"); savePlayer(chatId, player); return;
                    }
                }
            } catch (Exception ignored) { }
        }
        if (!textOnly) try {
            JSONObject payload = new JSONObject(); payload.put("chat_id", chatId);
            payload.put("caption", limit(caption, 1000));
            if (markup != null) payload.put("reply_markup", markup);
            JSONObject sent = sendPhoto(token, payload, desiredImage);
            JSONObject message = sent.optJSONObject("result");
            if (message != null) { player.put("pageMessageId", message.optLong("message_id", 0L)); player.put("pageMode", "photo"); player.put("pageImage", desiredImage); savePlayer(chatId, player); return; }
        } catch (Exception ignored) { }
        try {
            JSONObject payload = new JSONObject(); payload.put("chat_id", chatId); payload.put("text", limit(caption, 4000));
            if (markup != null) payload.put("reply_markup", markup);
            JSONObject sent = telegramPost(token, "sendMessage", payload);
            JSONObject message = sent.optJSONObject("result");
            if (message != null) { player.put("pageMessageId", message.optLong("message_id", 0L)); player.put("pageMode", "text"); player.put("pageImage", ""); savePlayer(chatId, player); }
        } catch (Exception ignored) { }
    }

    private boolean isUnchanged(JSONObject response) {
        return response != null && response.optString("description", "").toLowerCase().contains("message is not modified");
    }

    private void writeField(OutputStream output, String boundary, String name, String value) throws Exception {
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject activeWorld() {
        try {
            JSONArray projects = new JSONArray(prefs().getString(PROJECTS_KEY, "[]"));
            String selected = prefs().getString(ACTIVE_WORLD_KEY, "");
            JSONObject latest = null;
            for (int i = 0; i < projects.length(); i++) {
                JSONObject project = projects.optJSONObject(i);
                if (project == null) continue;
                if (!selected.isEmpty() && selected.equals(project.optString("id"))) return project;
                if (latest == null || project.optLong("updatedAt") > latest.optLong("updatedAt")) latest = project;
            }
            return latest;
        } catch (Exception ignored) { return null; }
    }

    private JSONObject findNode(JSONArray nodes, String id) {
        if (nodes == null) return null;
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null && id.equals(node.optString("id"))) return node;
        }
        return null;
    }

    private void sendText(String token, long chatId, String text) {
        JSONObject markup = new JSONObject();
        try { markup.put("inline_keyboard", new JSONArray()); } catch (Exception ignored) { }
        sendGamePage(token, chatId, limit(text, 1000), markup, MAP_IMAGE);
    }

    private void answerCallback(String token, String callbackId) {
        JSONObject payload = new JSONObject();
        try { payload.put("callback_query_id", callbackId); telegramPost(token, "answerCallbackQuery", payload); }
        catch (Exception ignored) { }
    }

    private JSONObject telegramGet(String token, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(api(token, method)).openConnection();
        try {
            connection.setConnectTimeout(12000); connection.setReadTimeout(15000);
            return new JSONObject(readAll(connection.getInputStream()));
        } finally { connection.disconnect(); }
    }

    private JSONObject telegramPost(String token, String method, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(api(token, method)).openConnection();
        try {
            connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(15000);
            connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            InputStream response = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (response == null) throw new IllegalStateException("Empty Telegram response");
            return new JSONObject(readAll(response));
        } finally { connection.disconnect(); }
    }

    private String api(String token, String method) { return "https://api.telegram.org/bot" + token + "/" + method; }

    private String readToken() throws Exception {
        String ciphertext = prefs().getString(TOKEN_CIPHER_KEY, "");
        String iv = prefs().getString(TOKEN_IV_KEY, "");
        if (ciphertext.isEmpty() || iv.isEmpty()) return "";
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null);
        SecretKey key = entry.getSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private String readAll(InputStream input) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(input); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private void updateStatus(String message) {
        prefs().edit().putString(SERVICE_STATUS_KEY, message).apply();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(message));
    }

    private Notification notification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Botcraft · Telegram-бот")
            .setContentText(text)
            .setOngoing(true)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Telegram-бот Botcraft", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Показывает, когда локальный бот запущен на телефоне");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void sleep(long milliseconds) {
        try { Thread.sleep(milliseconds); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
