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

    private void prepareBot(String token) throws Exception {
        JSONObject webhook = telegramGet(token, "getWebhookInfo");
        JSONObject result = webhook.optJSONObject("result");
        if (result != null && !result.optString("url", "").isEmpty()) {
            JSONObject params = new JSONObject(); params.put("drop_pending_updates", false);
            telegramPost(token, "deleteWebhook", params);
        }
        JSONObject commands = new JSONObject();
        JSONArray values = new JSONArray();
        JSONObject start = new JSONObject(); start.put("command", "start"); start.put("description", "Продолжить игру"); values.put(start);
        JSONObject restart = new JSONObject(); restart.put("command", "restart"); restart.put("description", "Начать кампанию заново"); values.put(restart);
        JSONObject inventory = new JSONObject(); inventory.put("command", "inventory"); inventory.put("description", "Показать рюкзак и характеристики"); values.put(inventory);
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
                String current = player.optString("currentNode", "");
                if (current.isEmpty()) current = startId;
                sendScene(token, chatId, world, current, true);
            } else if (text.startsWith("/restart")) {
                JSONObject world = activeWorld();
                String startId = startNodeId(world);
                if (startId.isEmpty()) sendText(token, chatId, "Сначала выбери и запусти игровой мир в APK.");
                else sendScene(token, chatId, world, startId, true, true);
            } else if (text.startsWith("/inventory")) {
                JSONObject world = activeWorld();
                JSONObject player = loadPlayer(chatId, world, false);
                sendText(token, chatId, playerSummary(player));
            } else if (!text.isEmpty()) {
                sendText(token, chatId, "Используй кнопки под сценой. Команды: /start — продолжить, /inventory — рюкзак, /restart — новая игра.");
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
        if (data.startsWith("pick:")) {
            try {
                int separator = data.lastIndexOf(':');
                String sourceNode = data.substring(5, separator);
                int choiceIndex = Integer.parseInt(data.substring(separator + 1));
                handleChoice(token, chatId, sourceNode, choiceIndex);
            } catch (Exception ignored) { }
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
            if (reset || player == null || !worldId.equals(player.optString("worldId", ""))) player = newPlayer(worldId);
            players.put(key, player);
            prefs().edit().putString(PLAYER_STATES_KEY, players.toString()).apply();
            return player;
        } catch (Exception ignored) { return newPlayer(worldId); }
    }

    private JSONObject newPlayer(String worldId) {
        JSONObject player = new JSONObject();
        try {
            player.put("worldId", worldId); player.put("hp", 10); player.put("maxHp", 10);
            player.put("gold", 28); player.put("xp", 0); player.put("inventory", new JSONArray());
            player.put("flags", new JSONObject()); player.put("visited", new JSONArray());
            player.put("clearedBattles", new JSONArray()); player.put("currentNode", "");
        } catch (Exception ignored) { }
        return player;
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
                    String required = choice.optString("requiresItem", "");
                    if (!required.isEmpty() && !hasItem(player, required)) continue;
                    JSONObject effect = choice.optJSONObject("effect");
                    if (effect != null && "buy".equals(effect.optString("type")) && player.optInt("gold") < effect.optInt("cost")) continue;
                    String target = choice.optString("target", "");
                    if (target.isEmpty()) target = node.optString("next", "");
                    if (target.isEmpty()) continue;
                    addButtonRow(keyboard, choice.optString("label", "Продолжить"), "pick:" + nodeId + ":" + i);
                }
                if (keyboard.length() == 0) {
                    String next = node.optString("next", "");
                    if (!next.isEmpty()) addButtonRow(keyboard, "Продолжить", "next:" + nodeId);
                    else addButtonRow(keyboard, "Начать историю заново", "restart");
                }
            }
            if (keyboard.length() > 0) { JSONObject rows = new JSONObject(); rows.put("inline_keyboard", keyboard); markup = rows; }
        } catch (Exception ignored) { }
        JSONObject payload = new JSONObject();
        try {
            payload.put("chat_id", chatId);
            String image = node.optString("image", "");
            if (includePhoto && !image.isEmpty()) {
                payload.put("caption", limit(narrative.toString(), 1000));
                if (keyboard.length() > 0) payload.put("reply_markup", markup);
                try { sendPhoto(token, payload, image); return; }
                catch (Exception ignored) { payload.remove("caption"); }
            }
            payload.put("text", limit(narrative.toString(), 4000));
            if (keyboard.length() > 0) payload.put("reply_markup", markup);
            telegramPost(token, "sendMessage", payload);
        } catch (Exception ignored) { }
    }

    private void addButtonRow(JSONArray keyboard, String label, String callbackData) throws Exception {
        JSONObject button = new JSONObject(); button.put("text", limit(label, 64)); button.put("callback_data", callbackData);
        JSONArray row = new JSONArray(); row.put(button); keyboard.put(row);
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
        String required = choice.optString("requiresItem", "");
        if (!required.isEmpty() && !hasItem(player, required)) { sendText(token, chatId, "Для этого решения нужен предмет: " + required); return; }
        JSONObject effect = choice.optJSONObject("effect");
        if (effect != null && "reset".equals(effect.optString("type"))) {
            player = newPlayer(world.optString("id", ""));
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
        String nodeId = player.optString("currentNode", "");
        JSONObject node = findNode(world == null ? null : world.optJSONArray("nodes"), nodeId);
        if (node == null || !"battle".equals(node.optString("type", ""))) return;
        JSONObject battle = player.optJSONObject("battle");
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
            int oldHp = player.optInt("hp", 10);
            try { player.put("hp", Math.min(player.optInt("maxHp", 10), oldHp + 4)); } catch (Exception ignored) { }
            healTurn = 1;
        } else if (!"attack".equals(action)) return;
        if (healTurn == 0) {
            int damage = 2 + (hasItem(player, "Сигнальный гарпун") ? 2 : 0);
            int enemyHp = battle.optInt("enemyHp", node.optInt("enemyHp", 5)) - damage;
            try { battle.put("enemyHp", enemyHp); player.put("battle", battle); } catch (Exception ignored) { }
            if (enemyHp <= 0) {
                addString(player.optJSONArray("clearedBattles"), nodeId);
                try {
                    player.remove("battle");
                    player.put("gold", player.optInt("gold") + node.optInt("rewardGold", 0));
                    player.put("xp", player.optInt("xp") + node.optInt("rewardXp", 0));
                    addItem(player, node.optString("rewardItem", ""));
                } catch (Exception ignored) { }
                savePlayer(chatId, player);
                sendScene(token, chatId, world, node.optString("victoryTarget", ""), true); return;
            }
        }
        int hp = player.optInt("hp", 10) - node.optInt("enemyAttack", 1);
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

    private void addItem(JSONObject player, String item) {
        if (item == null || item.isEmpty()) return;
        JSONArray inventory = player.optJSONArray("inventory");
        if (inventory == null) { inventory = new JSONArray(); try { player.put("inventory", inventory); } catch (Exception ignored) { } }
        if (!contains(inventory, item)) try { inventory.put(item); } catch (Exception ignored) { }
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
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.length(); i++) { if (result.length() > 0) result.append(", "); result.append(items.optString(i)); }
        return result.toString();
    }

    private String playerSummary(JSONObject player) {
        if (player == null) return "";
        return "❤️ " + player.optInt("hp", 10) + "/" + player.optInt("maxHp", 10) + "   ·   🪙 " + player.optInt("gold") + "   ·   ✨ " + player.optInt("xp")
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
        JSONObject payload = new JSONObject();
        try { payload.put("chat_id", chatId); payload.put("text", text); telegramPost(token, "sendMessage", payload); }
        catch (Exception ignored) { }
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
            return new JSONObject(readAll(connection.getInputStream()));
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
