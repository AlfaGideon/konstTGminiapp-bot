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
        JSONObject start = new JSONObject(); start.put("command", "start"); start.put("description", "Начать игру"); values.put(start);
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
                JSONArray nodes = world == null ? null : world.optJSONArray("nodes");
                String startId = world == null ? "" : world.optString("startNode", "");
                if ((startId.isEmpty() || findNode(nodes, startId) == null) && nodes != null && nodes.length() > 0) {
                    JSONObject first = nodes.optJSONObject(0);
                    if (first != null) startId = first.optString("id", "");
                }
                if (startId.isEmpty()) sendText(token, chatId, "Botcraft: мир пока не создан. Открой APK, создай мир и запусти бота ещё раз.");
                else sendScene(token, chatId, world, startId);
            } else if (!text.isEmpty()) {
                sendText(token, chatId, "Выбери вариант кнопкой под сценой или отправь /start, чтобы начать заново.");
            }
            return;
        }
        JSONObject callback = update.optJSONObject("callback_query");
        if (callback != null) {
            String callbackId = callback.optString("id", "");
            JSONObject callbackMessage = callback.optJSONObject("message");
            JSONObject chat = callbackMessage == null ? null : callbackMessage.optJSONObject("chat");
            String data = callback.optString("data", "");
            if (!callbackId.isEmpty()) answerCallback(token, callbackId);
            if (chat != null && data.startsWith("node:")) {
                JSONObject world = activeWorld();
                sendScene(token, chat.optLong("id"), world, data.substring(5));
            }
        }
    }

    private void sendScene(String token, long chatId, JSONObject world, String nodeId) {
        JSONArray nodes = world == null ? null : world.optJSONArray("nodes");
        JSONObject node = findNode(nodes, nodeId);
        if (node == null) {
            sendText(token, chatId, "Эта сцена больше не найдена. Отправь /start, чтобы начать заново.");
            return;
        }
        String title = node.optString("title", "Сцена");
        String body = node.optString("text", "");
        String text = body.isEmpty() ? title : title + "\n\n" + body;
        if (text.length() > 4000) text = text.substring(0, 4000);
        JSONObject markup = new JSONObject();
        JSONArray keyboard = new JSONArray();
        JSONArray choices = node.optJSONArray("choices");
        if (choices != null) for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice == null) continue;
            String target = choice.optString("target", "");
            if (target.isEmpty()) target = node.optString("next", "");
            if (target.isEmpty()) continue;
            JSONObject button = new JSONObject();
            button.put("text", limit(choice.optString("label", "Дальше"), 64));
            button.put("callback_data", "node:" + target);
            JSONArray row = new JSONArray(); row.put(button); keyboard.put(row);
        }
        if (keyboard.length() == 0) {
            String next = node.optString("next", "");
            if (!next.isEmpty()) {
                JSONObject button = new JSONObject(); button.put("text", "Продолжить"); button.put("callback_data", "node:" + next);
                JSONArray row = new JSONArray(); row.put(button); keyboard.put(row);
            } else {
                String start = world.optString("startNode", "");
                if (!start.isEmpty()) {
                    JSONObject button = new JSONObject(); button.put("text", "Начать сначала"); button.put("callback_data", "node:" + start);
                    JSONArray row = new JSONArray(); row.put(button); keyboard.put(row);
                }
            }
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("chat_id", chatId); payload.put("text", text);
            if (keyboard.length() > 0) { markup.put("inline_keyboard", keyboard); payload.put("reply_markup", markup); }
            telegramPost(token, "sendMessage", payload);
        } catch (Exception ignored) { }
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
