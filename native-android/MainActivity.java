package ru.botcraft.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A fully native Android shell and project studio. It intentionally never creates a WebView. */
public class MainActivity extends Activity {
    private static final String PREFS = "botcraft-native";
    private static final String PROJECTS_KEY = "projects";
    private static final String BOT_TOKEN_CIPHER_KEY = "telegram_bot_token_ciphertext";
    private static final String BOT_TOKEN_IV_KEY = "telegram_bot_token_iv";
    private static final String BOT_NAME_KEY = "telegram_bot_username";
    private static final String BOT_RUNNING_KEY = "telegram_bot_running";
    private static final String BOT_STATUS_KEY = "telegram_bot_status";
    private static final String ACTIVE_WORLD_KEY = "active_world_id";
    private static final String BOT_TOKEN_KEY_ALIAS = "botcraft-telegram-token-v1";
    private static final int INSTALL_PERMISSION_REQUEST = 411;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 412;
    private static final int TAB_HOME = 0;
    private static final int TAB_TEMPLATES = 1;
    private static final int TAB_PROJECTS = 2;
    private static final int TAB_SETTINGS = 3;
    private static final String RELEASES_API = "https://api.github.com/repos/AlfaGideon/konstTGminiapp-bot/releases/latest";

    private static final Template[] TEMPLATES = new Template[] {
        new Template("start", "Вход в мир", "Сценарий", "Стартовый узел: приветствие, выбор персонажа и первое действие игрока.", "Основа", "⌂"),
        new Template("dialogue", "Диалог", "Диалог", "Реплики NPC, варианты ответа и переход к следующему шагу.", "История", "…"),
        new Template("npc", "NPC-персонаж", "Персонажи", "Имя, портрет-заглушка, характер и набор доступных реплик.", "Мир", "♙"),
        new Template("choice", "Выбор игрока", "Логика", "Ветвление сюжета по выбору игрока или его параметрам.", "Алгоритм", "⑂"),
        new Template("quest", "Квест", "Задания", "Цели, этапы, условия завершения и награды за выполнение.", "Прогресс", "⚑"),
        new Template("shop", "Торговая площадка", "Экономика", "Каталог предметов, цены, валюта и действия покупки/продажи.", "Торговля", "¤"),
        new Template("inventory", "Инвентарь", "Предметы", "Предметы игрока, экипировка, расходники и лимит вместимости.", "Система", "▤"),
        new Template("battle", "Бой", "Сражения", "Пошаговый бой, характеристики, очередь ходов и исход сражения.", "Система", "⚔"),
        new Template("loot", "Награда и добыча", "Награды", "Выдача предметов, валюты и опыта с правилами редкости.", "Награды", "✦"),
        new Template("condition", "Проверка условия", "Логика", "Проверка уровня, флага, предмета или результата предыдущего шага.", "Алгоритм", "?"),
        new Template("travel", "Переход между локациями", "Мир", "Карта, доступные места, требования входа и точка назначения.", "Навигация", "⌖"),
        new Template("craft", "Создание предмета", "Экономика", "Рецепт, требуемые ресурсы, результат и проверка инвентаря.", "Ремесло", "⚒"),
        new Template("guild", "Гильдия и группа", "Социальное", "Приглашения, роли, общий прогресс и совместные цели.", "Мультиплеер", "♧"),
        new Template("raid", "Рейдовый босс", "Сражения", "Общий босс, вклад участников и распределение наград.", "Мультиплеер", "♜"),
        new Template("timer", "Таймер и ожидание", "События", "Задержка, расписание, перезарядка и повторное событие.", "События", "◷"),
        new Template("admin", "Действие администратора", "Администрирование", "Служебное сообщение, модерация или ручное событие мира.", "Админка", "⚙")
    };

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<JSONObject> projects = new ArrayList<>();
    private SharedPreferences preferences;
    private LinearLayout root;
    private LinearLayout page;
    private LinearLayout templateList;
    private TextView updateStatus;
    private int activeTab = TAB_HOME;
    private String screen = "home";
    private String selectedTemplate = "start";
    private String editingId = "";
    private String themeChoice = "system";
    private boolean dark;
    private long pendingDownloadId = -1;
    private boolean receiverRegistered;
    private String templateQuery = "";
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == pendingDownloadId) handleDownloadedApk(id);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        themeChoice = preferences.getString("theme", "system");
        dark = resolveDarkMode();
        loadProjects();
        getWindow().setStatusBarColor(colorBg());
        getWindow().setNavigationBarColor(colorSurface());
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        registerDownloadReceiver();
        render();
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) { }
        }
        worker.shutdown();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if ("admin".equals(screen)) screen = "editor";
        else if ("editor".equals(screen)) { screen = "projects"; activeTab = TAB_PROJECTS; }
        else if ("builder".equals(screen)) { screen = "home"; activeTab = TAB_HOME; }
        else if (!"home".equals(screen)) { screen = "home"; activeTab = TAB_HOME; }
        else { super.onBackPressed(); return; }
        render();
    }

    private boolean resolveDarkMode() {
        if ("dark".equals(themeChoice)) return true;
        if ("light".equals(themeChoice)) return false;
        int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int colorBg() { return Color.parseColor(dark ? "#101522" : "#F4F6FA"); }
    private int colorSurface() { return Color.parseColor(dark ? "#192132" : "#FFFFFF"); }
    private int colorCard() { return Color.parseColor(dark ? "#202B3E" : "#FFFFFF"); }
    private int colorText() { return Color.parseColor(dark ? "#F5F7FC" : "#20243A"); }
    private int colorMuted() { return Color.parseColor(dark ? "#B1BED0" : "#586276"); }
    private int colorLine() { return Color.parseColor(dark ? "#344158" : "#E0E5EE"); }
    private int colorPrimary() { return Color.parseColor(dark ? "#958BFF" : "#5549CC"); }
    private int colorAccent() { return Color.parseColor(dark ? "#45D6A0" : "#16815E"); }

    private void render() {
        dark = resolveDarkMode();
        Window window = getWindow();
        window.setStatusBarColor(colorBg());
        window.setNavigationBarColor(colorSurface());
        window.getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg());
        root.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(colorBg());
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.TOP);
        page.setBackgroundColor(colorBg());
        page.setPadding(dp(20), dp(14), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if ("builder".equals(screen)) renderBuilder();
        else if ("editor".equals(screen)) renderEditor();
        else if ("admin".equals(screen)) renderAdmin();
        else if (activeTab == TAB_TEMPLATES) renderTemplates();
        else if (activeTab == TAB_PROJECTS) renderProjects();
        else if (activeTab == TAB_SETTINGS) renderSettings();
        else renderHome();

        root.addView(buildBottomNav(), new LinearLayout.LayoutParams(-1, dp(72)));
        setContentView(root);
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(colorSurface());
        nav.setElevation(dp(10));
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        String[] labels = {"Миры", "Механики", "Проекты", "Настройки"};
        String[] marks = {"⌂", "✦", "▤", "⚙"};
        int selectedTab = ("editor".equals(screen) || "admin".equals(screen)) ? TAB_PROJECTS : activeTab;
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            boolean selected = selectedTab == i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(56));
            item.setBackground(round(selected ? (dark ? "#32304F" : "#EFEDFF") : (dark ? "#192132" : "#FFFFFF"), 16));
            TextView mark = text(marks[i], 18, selected ? colorPrimary() : colorMuted(), true);
            mark.setGravity(Gravity.CENTER);
            TextView label = text(labels[i], 12, selected ? colorPrimary() : colorMuted(), selected);
            label.setGravity(Gravity.CENTER);
            item.addView(mark);
            item.addView(label);
            item.setContentDescription(labels[i]);
            item.setFocusable(true);
            item.setClickable(true);
            item.setOnClickListener(v -> {
                activeTab = tab;
                screen = tab == TAB_TEMPLATES ? "templates" : tab == TAB_PROJECTS ? "projects" : tab == TAB_SETTINGS ? "settings" : "home";
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(item, lp);
        }
        return nav;
    }

    private void renderHome() {
        addBrandHeader("Botcraft", "БОТ + MINI APP");
        addSpace(20);
        TextView eyebrow = text("КОНСТРУКТОР MMORPG ДЛЯ TELEGRAM", 11, colorPrimary(), true);
        page.addView(eyebrow);
        TextView title = text("Собери свой\nигровой мир", 31, colorText(), true);
        title.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams titleLp = params(-1, -2);
        titleLp.topMargin = dp(8);
        page.addView(title, titleLp);
        TextView intro = text("Собирай мир из связанных узлов: NPC и диалоги, квесты, торговля, бои и правила переходов. APK — админка; запуск для игроков потребует работающего Telegram-обработчика.", 15, colorMuted(), false);
        intro.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams introLp = params(-1, -2);
        introLp.topMargin = dp(10);
        page.addView(intro, introLp);

        LinearLayout start = cardColumn();
        start.setBackground(round(dark ? "#292744" : "#EEEFFF", 22));
        TextView badge = text("ВИЗУАЛЬНЫЙ RPG-КОНСТРУКТОР", 10, colorPrimary(), true);
        start.addView(badge);
        TextView startTitle = text("Один мир.\nМного систем.", 21, colorText(), true);
        LinearLayout.LayoutParams st = params(-1, -2); st.topMargin = dp(8); start.addView(startTitle, st);
        TextView startCopy = text("Создай мир, редактируй игровые узлы и подключи своего Telegram-бота прямо в панели APK. Игровой онлайн-обработчик подключается отдельно.", 13, colorMuted(), false);
        LinearLayout.LayoutParams sc = params(-1, -2); sc.topMargin = dp(5); start.addView(startCopy, sc);
        TextView create = actionButton("＋   Создать игровой мир", true);
        LinearLayout.LayoutParams cp = params(-1, 50); cp.topMargin = dp(16); start.addView(create, cp);
        create.setOnClickListener(v -> startNewWorld());
        LinearLayout.LayoutParams slp = params(-1, -2); slp.topMargin = dp(18); page.addView(start, slp);

        addSectionTitle("Базовые блоки", "Начни с входа в мир и персонажей");
        addTemplateCard(TEMPLATES[0], false);
        addTemplateCard(TEMPLATES[1], false);
        addTemplateCard(TEMPLATES[2], false);
        addSectionTitle("Твоя студия", "Проекты сохраняются только на этом устройстве");
        LinearLayout stats = cardColumn();
        TextView count = text(projects.size() + " проектов", 18, colorText(), true);
        stats.addView(count);
        TextView countSub = text("Создано: " + formatDate(new Date()), 12, colorMuted(), false);
        LinearLayout.LayoutParams cs = params(-1, -2); cs.topMargin = dp(3); stats.addView(countSub, cs);
        LinearLayout.LayoutParams sp = params(-1, -2); sp.topMargin = dp(10); page.addView(stats, sp);
        TextView viewProjects = actionButton("Открыть проекты", false);
        LinearLayout.LayoutParams vp = params(-1, 46); vp.topMargin = dp(9); page.addView(viewProjects, vp);
        viewProjects.setOnClickListener(v -> navigate(TAB_PROJECTS));
    }

    private void renderTemplates() {
        addBrandHeader("Библиотека механик", TEMPLATES.length + " БЛОКОВ");
        addSpace(16);
        TextView title = text("Собери алгоритм игры", 26, colorText(), true);
        page.addView(title);
        TextView copy = text("Это не готовые игры, а узлы конструктора. Добавляй блоки в мир, меняй тексты и соединяй переходами — например: NPC → диалог → выбор → квест.", 14, colorMuted(), false);
        LinearLayout.LayoutParams cp = params(-1, -2); cp.topMargin = dp(6); page.addView(copy, cp);
        TextView scope = text("MVP: ветвления и базовые демо-режимы квеста, магазина, боя, инвентаря и наград. Остальные блоки пока работают как редактируемые сцены/переходы; онлайн-мультиплеера и общей базы нет.", 12, colorMuted(), false);
        LinearLayout.LayoutParams scopeLp = params(-1, -2); scopeLp.topMargin = dp(8); page.addView(scope, scopeLp);
        EditText search = editText("Поиск по механикам");
        LinearLayout.LayoutParams searchLp = params(-1, 50); searchLp.topMargin = dp(16); page.addView(search, searchLp);
        templateList = new LinearLayout(this);
        templateList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = params(-1, -2); listLp.topMargin = dp(10); page.addView(templateList, listLp);
        fillTemplateList("");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { fillTemplateList(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout tip = cardColumn();
        TextView tipTitle = text("Не уверен, что выбрать?", 14, colorText(), true);
        tip.addView(tipTitle);
        TextView tipText = text("Добавляй игровые механики в редакторе мира и соединяй их в свой сценарий.", 12, colorMuted(), false);
        LinearLayout.LayoutParams tl = params(-1, -2); tl.topMargin = dp(4); tip.addView(tipText, tl);
        LinearLayout.LayoutParams tipLp = params(-1, -2); tipLp.topMargin = dp(6); page.addView(tip, tipLp);
    }

    private void fillTemplateList(String query) {
        if (templateList == null) return;
        templateList.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Template template : TEMPLATES) {
            String hay = (template.title + " " + template.category + " " + template.description + " " + template.tag).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            addTemplateCardTo(templateList, template, true);
            shown++;
        }
        if (shown == 0) {
            TextView empty = text("Ничего не нашлось. Попробуй другой запрос.", 14, colorMuted(), false);
            empty.setPadding(dp(4), dp(18), dp(4), dp(18));
            templateList.addView(empty);
        }
    }

    private void renderProjects() {
        addBrandHeader("Мои миры", projects.size() + " СОХРАНЕНО");
        addSpace(16);
        page.addView(text("Твоя коллекция", 26, colorText(), true));
        TextView copy = text("Сценарии мира хранятся локально. Открой мир, чтобы редактировать узлы, переходы и экран Mini App.", 14, colorMuted(), false);
        LinearLayout.LayoutParams cp = params(-1, -2); cp.topMargin = dp(5); page.addView(copy, cp);
        TextView newProject = actionButton("＋   Новый мир", true);
        LinearLayout.LayoutParams np = params(-1, 50); np.topMargin = dp(16); page.addView(newProject, np);
        newProject.setOnClickListener(v -> startNewWorld());
        addSpace(10);
        if (projects.isEmpty()) {
            LinearLayout empty = cardColumn();
            empty.setGravity(Gravity.CENTER);
            TextView emptyMark = text("✦", 28, colorPrimary(), true); emptyMark.setGravity(Gravity.CENTER); empty.addView(emptyMark);
            TextView emptyTitle = text("Первый мир начинается с идеи", 16, colorText(), true); emptyTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams et = params(-1, -2); et.topMargin = dp(7); empty.addView(emptyTitle, et);
            TextView emptyCopy = text("Создай мир, затем добавляй сцены и настраивай игровые переходы.", 13, colorMuted(), false); emptyCopy.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ec = params(-1, -2); ec.topMargin = dp(4); empty.addView(emptyCopy, ec);
            LinearLayout.LayoutParams ep = params(-1, -2); ep.topMargin = dp(8); page.addView(empty, ep);
            return;
        }
        for (int i = projects.size() - 1; i >= 0; i--) {
            JSONObject project = projects.get(i);
            LinearLayout card = cardColumn();
            String id = project.optString("id", "");
            Template t = findTemplate(project.optString("template", "quiz"));
            TextView name = text(t.emoji + "   " + project.optString("name", "Без названия"), 16, colorText(), true);
            card.addView(name);
            TextView detail = text(t.category + "  ·  " + formatDate(new Date(project.optLong("updatedAt", System.currentTimeMillis()))), 12, colorMuted(), false);
            LinearLayout.LayoutParams dl = params(-1, -2); dl.topMargin = dp(5); card.addView(detail, dl);
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView open = actionButton("Конструктор", true);
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, dp(44), 1); op.topMargin = dp(12); actions.addView(open, op);
            TextView remove = actionButton("Удалить", false);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(44), 1); rp.setMargins(dp(8), dp(12), 0, 0); actions.addView(remove, rp);
            open.setOnClickListener(v -> openProjectEditor(id));
            remove.setOnClickListener(v -> confirmDelete(id));
            LinearLayout.LayoutParams al = params(-1, -2); card.addView(actions, al);
            LinearLayout.LayoutParams cl = params(-1, -2); cl.topMargin = dp(10); page.addView(card, cl);
        }
    }

    private void renderSettings() {
        addBrandHeader("Настройки", "ПРИЛОЖЕНИЕ");
        addSpace(16);
        page.addView(text("Всё под контролем", 26, colorText(), true));
        TextView intro = text("Оформление, обновления и локальные данные.", 14, colorMuted(), false);
        LinearLayout.LayoutParams il = params(-1, -2); il.topMargin = dp(5); page.addView(intro, il);

        addBotConnectionCard();
        addSectionTitle("Внешний вид", "Настрой под себя");
        LinearLayout themeCard = cardColumn();
        addThemeOption(themeCard, "system", "Как в системе", "Следовать настройкам Android");
        addThemeOption(themeCard, "light", "Светлая тема", "Светлый фон и контрастный текст");
        addThemeOption(themeCard, "dark", "Тёмная тема", "Комфортно в темноте и на OLED");
        page.addView(themeCard);

        addSectionTitle("Обновление", "Проверка прямо в приложении");
        LinearLayout updateCard = cardColumn();
        TextView version = text("Botcraft Studio  " + currentVersion(), 15, colorText(), true);
        updateCard.addView(version);
        TextView updateCopy = text("Проверю свежую версию, скачаю APK и открою системную установку. Android попросит подтвердить обновление.", 12, colorMuted(), false);
        LinearLayout.LayoutParams uc = params(-1, -2); uc.topMargin = dp(5); updateCard.addView(updateCopy, uc);
        TextView check = actionButton("Проверить обновления", true);
        LinearLayout.LayoutParams ck = params(-1, 48); ck.topMargin = dp(12); updateCard.addView(check, ck);
        updateStatus = text("Версия установлена: " + currentVersion(), 12, colorMuted(), false);
        LinearLayout.LayoutParams us = params(-1, -2); us.topMargin = dp(8); updateCard.addView(updateStatus, us);
        check.setOnClickListener(v -> checkForUpdates(check));
        page.addView(updateCard);

        addSectionTitle("Хранилище", "Только данные приложения");
        LinearLayout storage = cardColumn();
        storage.addView(text(projects.size() + " локальных проектов", 15, colorText(), true));
        TextView storageCopy = text("Миры и настройки подключения Telegram-бота хранятся на этом устройстве.", 12, colorMuted(), false);
        LinearLayout.LayoutParams sl = params(-1, -2); sl.topMargin = dp(4); storage.addView(storageCopy, sl);
        TextView clear = actionButton("Очистить проекты", false);
        LinearLayout.LayoutParams cl = params(-1, 46); cl.topMargin = dp(12); storage.addView(clear, cl);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Удалить все проекты?").setMessage("Действие нельзя отменить. Все локально сохранённые миры будут удалены.").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> { projects.clear(); saveProjects(); render(); }).show());
        page.addView(storage);

        TextView about = text("Botcraft Studio · Android\nНативный интерфейс · проекты доступны офлайн", 11, colorMuted(), false);
        about.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ab = params(-1, -2); ab.topMargin = dp(20); page.addView(about, ab);
    }

    private void renderBuilder() {
        Template template = findTemplate(selectedTemplate);
        addBrandHeader("Новый RPG-мир", "НАСТРОЙКА МИРА");
        TextView back = text("‹   Назад к механикам", 14, colorPrimary(), true);
        LinearLayout.LayoutParams bp = params(-1, 44); bp.topMargin = dp(8); page.addView(back, bp);
        back.setGravity(Gravity.CENTER_VERTICAL); back.setOnClickListener(v -> navigate(TAB_TEMPLATES));
        addSpace(8);
        TextView title = text(template.emoji + "  " + template.title, 25, colorText(), true);
        page.addView(title);
        TextView copy = text("Этот блок станет первой сценой мира. После создания ты сможешь добавить NPC, диалоги, торговлю, квесты и связать узлы переходами.", 14, colorMuted(), false);
        LinearLayout.LayoutParams cp = params(-1, -2); cp.topMargin = dp(7); page.addView(copy, cp);

        LinearLayout form = cardColumn();
        LinearLayout.LayoutParams fp = params(-1, -2); fp.topMargin = dp(18); page.addView(form, fp);
        form.addView(text("Название проекта", 14, colorText(), true));
        EditText name = editText("Например, Космический квиз");
        LinearLayout.LayoutParams nameLp = params(-1, 50); nameLp.topMargin = dp(8); form.addView(name, nameLp);
        if (!editingId.isEmpty()) {
            JSONObject existing = findProject(editingId);
            if (existing != null) name.setText(existing.optString("name", ""));
        }
        TextView tip = text("Потом это название можно изменить в списке проектов.", 12, colorMuted(), false);
        LinearLayout.LayoutParams tp = params(-1, -2); tp.topMargin = dp(5); form.addView(tip, tp);
        TextView typeLabel = text("С чем начнём", 14, colorText(), true);
        LinearLayout.LayoutParams typeLp = params(-1, -2); typeLp.topMargin = dp(18); form.addView(typeLabel, typeLp);
        addFeatureLine(form, "Сценарий", "Первый узел и связанные переходы");
        addFeatureLine(form, "Telegram-бот", "Подключается по токену из панели администратора APK");
        addFeatureLine(form, "Публикация игры", "Нужен доступный HTTPS-адрес для запуска Mini App в Telegram");
        TextView save = actionButton("Создать мир и открыть конструктор", true);
        LinearLayout.LayoutParams saveLp = params(-1, 52); saveLp.topMargin = dp(18); form.addView(save, saveLp);
        save.setOnClickListener(v -> {
            String projectName = name.getText().toString().trim();
            if (projectName.isEmpty()) { name.setError("Укажи название проекта"); name.requestFocus(); return; }
            JSONObject saved = saveProject(projectName, template.id, editingId);
            editingId = saved.optString("id", "");
            preferences.edit().putString(ACTIVE_WORLD_KEY, editingId).apply();
            if (!saved.has("nodes") || saved.optJSONArray("nodes") == null || saved.optJSONArray("nodes").length() == 0) initializeWorldNodes(saved, template.id);
            saveProjects();
            screen = "editor";
            activeTab = TAB_PROJECTS;
            Toast.makeText(this, "Мир создан — добавь первые узлы", Toast.LENGTH_SHORT).show();
            render();
        });
        TextView privacy = text("Токен Telegram можно подключить в панели администратора APK. Публикация игрового сценария для игроков пока требует доступного хостинга.", 12, colorMuted(), false);
        LinearLayout.LayoutParams privacyLp = params(-1, -2); privacyLp.topMargin = dp(12); page.addView(privacy, privacyLp);
    }

    private void startNewWorld() {
        selectedTemplate = "start";
        editingId = "";
        screen = "builder";
        activeTab = TAB_PROJECTS;
        render();
    }

    private void openProjectEditor(String id) {
        if (findProject(id) == null) return;
        preferences.edit().putString(ACTIVE_WORLD_KEY, id).apply();
        editingId = id;
        screen = "editor";
        activeTab = TAB_PROJECTS;
        render();
    }

    private void initializeWorldNodes(JSONObject project, String templateId) {
        if (project == null) return;
        JSONArray nodes = project.optJSONArray("nodes");
        if (nodes != null && nodes.length() > 0) return;
        Template start = findTemplate(templateId);
        JSONObject node = createNode(start);
        try { node.put("title", "Начало приключения"); node.put("text", "Добро пожаловать в " + project.optString("name", "наш мир") + "! Куда отправимся?"); }
        catch (Exception ignored) { }
        nodes = new JSONArray(); nodes.put(node);
        try { project.put("nodes", nodes); project.put("startNode", node.optString("id")); project.put("updatedAt", System.currentTimeMillis()); }
        catch (Exception ignored) { }
        saveProjects();
    }

    private JSONObject createNode(Template template) {
        JSONObject node = new JSONObject();
        try {
            node.put("id", UUID.randomUUID().toString());
            node.put("type", template.id);
            node.put("title", template.title);
            node.put("text", template.description);
            node.put("next", "");
            node.put("choices", new JSONArray());
        } catch (Exception ignored) { }
        return node;
    }

    private JSONArray worldNodes(JSONObject project) {
        JSONArray nodes = project.optJSONArray("nodes");
        if (nodes == null) { nodes = new JSONArray(); try { project.put("nodes", nodes); } catch (Exception ignored) { } }
        return nodes;
    }

    private JSONObject findNode(JSONObject project, String id) {
        JSONArray nodes = worldNodes(project);
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null && id.equals(node.optString("id"))) return node;
        }
        return null;
    }

    private void touchProject(JSONObject project) {
        try { project.put("updatedAt", System.currentTimeMillis()); } catch (Exception ignored) { }
        saveProjects();
    }

    private void renderEditor() {
        JSONObject project = findProject(editingId);
        if (project == null) { screen = "projects"; renderProjects(); return; }
        initializeWorldNodes(project, project.optString("template", "start"));
        JSONArray nodes = worldNodes(project);
        addBrandHeader("Конструктор мира", nodes.length() + " УЗЛОВ");
        TextView worldTitle = text(project.optString("name", "Без названия"), 25, colorText(), true);
        LinearLayout.LayoutParams titleLp = params(-1, -2); titleLp.topMargin = dp(12); page.addView(worldTitle, titleLp);
        TextView help = text("Нажми на узел, чтобы изменить текст. Связывай переходы и варианты ответов — сценарий хранится на устройстве.", 13, colorMuted(), false);
        LinearLayout.LayoutParams helpLp = params(-1, -2); helpLp.topMargin = dp(5); page.addView(help, helpLp);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL);
        TextView add = actionButton("＋ Добавить узел", true);
        TextView admin = actionButton("Админка", false);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(46), 1); a.topMargin = dp(14); controls.addView(add, a);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(46), 1); b.setMargins(dp(7), dp(14), 0, 0); controls.addView(admin, b);
        page.addView(controls);
        add.setOnClickListener(v -> showNodeLibrary());
        admin.setOnClickListener(v -> { screen = "admin"; render(); });
        TextView graphHeading = text("ЦЕПОЧКА СЦЕНАРИЯ", 11, colorPrimary(), true);
        LinearLayout.LayoutParams gh = params(-1, -2); gh.topMargin = dp(22); page.addView(graphHeading, gh);
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i); if (node == null) continue;
            final String nodeId = node.optString("id", "");
            Template type = findTemplate(node.optString("type", "dialogue"));
            LinearLayout card = cardColumn();
            LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = text(type.emoji, 22, colorPrimary(), true); badge.setGravity(Gravity.CENTER);
            badge.setBackground(round(dark ? "#30314C" : "#F0EFFF", 13)); head.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1); ml.setMargins(dp(10), 0, 0, 0);
            boolean isStart = nodeId.equals(project.optString("startNode", ""));
            meta.addView(text((isStart ? "СТАРТ · " : "УЗЕЛ " + (i + 1) + " · ") + type.title, 11, colorPrimary(), true));
            TextView nt = text(node.optString("title", type.title), 16, colorText(), true); LinearLayout.LayoutParams ntp = params(-1, -2); ntp.topMargin = dp(2); meta.addView(nt, ntp);
            head.addView(meta, ml); card.addView(head);
            TextView body = text(node.optString("text", ""), 13, colorMuted(), false); LinearLayout.LayoutParams bodyLp = params(-1, -2); bodyLp.topMargin = dp(10); card.addView(body, bodyLp);
            JSONArray choices = node.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                for (int c = 0; c < choices.length(); c++) {
                    JSONObject choice = choices.optJSONObject(c);
                    if (choice != null) {
                        final int choiceIndex = c;
                        TextView choiceLine = text("↳ " + choice.optString("label", "Вариант") + "  →  " + nodeTitle(project, choice.optString("target", "")) + "  ·  настроить", 12, colorPrimary(), false);
                        choiceLine.setPadding(0, dp(5), 0, dp(5));
                        choiceLine.setOnClickListener(v -> chooseChoiceTarget(project, nodeId, choiceIndex));
                        card.addView(choiceLine);
                    }
                }
            }
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView edit = actionButton("Изменить", false); TextView link = actionButton("Переход →", false); TextView remove = actionButton("Удалить", false);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(42), 1); ep.topMargin = dp(10); actions.addView(edit, ep);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1); lp.setMargins(dp(5), dp(10), 0, 0); actions.addView(link, lp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(42), 1); rp.setMargins(dp(5), dp(10), 0, 0); actions.addView(remove, rp);
            card.addView(actions); edit.setOnClickListener(v -> editNode(project, nodeId)); link.setOnClickListener(v -> chooseNodeTarget(project, nodeId));
            remove.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Удалить узел?").setMessage("Связи на этот узел станут пустыми.").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> { deleteNode(project, nodeId); render(); }).show());
            LinearLayout.LayoutParams cardLp = params(-1, -2); cardLp.topMargin = dp(10); page.addView(card, cardLp);
            String nextId = node.optString("next", "");
            if (!nextId.isEmpty()) {
                TextView arrow = text("↓  ДАЛЕЕ: " + nodeTitle(project, nextId), 10, colorMuted(), true);
                arrow.setGravity(Gravity.CENTER);
                page.addView(arrow, params(-1, 28));
            }
        }
        TextView botSetup = actionButton("Настроить Telegram-бота в APK", true);
        LinearLayout.LayoutParams xp = params(-1, 50); xp.topMargin = dp(18); page.addView(botSetup, xp);
        botSetup.setOnClickListener(v -> { screen = "admin"; render(); });
        TextView backendNote = text("Управление миром остаётся в APK. Чтобы игроки запускали Mini App через Telegram, нужен публичный HTTPS-адрес игры и обработчик событий бота; токен сам по себе не публикует данные с телефона.", 12, colorMuted(), false);
        LinearLayout.LayoutParams bn = params(-1, -2); bn.topMargin = dp(10); page.addView(backendNote, bn);
    }

    private String nodeTitle(JSONObject project, String id) {
        JSONObject node = findNode(project, id);
        return node == null ? "не подключён" : node.optString("title", "Узел");
    }

    private void showNodeLibrary() {
        String[] labels = new String[TEMPLATES.length]; for (int i = 0; i < TEMPLATES.length; i++) labels[i] = TEMPLATES[i].emoji + "  " + TEMPLATES[i].title;
        new AlertDialog.Builder(this).setTitle("Добавить игровой блок").setItems(labels, (d, which) -> addWorldNode(TEMPLATES[which])).setNegativeButton("Отмена", null).show();
    }

    private void addWorldNode(Template template) {
        JSONObject project = findProject(editingId); if (project == null) return;
        JSONArray nodes = worldNodes(project); JSONObject node = createNode(template);
        try {
            if (nodes.length() > 0) { JSONObject previous = nodes.optJSONObject(nodes.length() - 1); if (previous != null && previous.optString("next", "").isEmpty()) previous.put("next", node.optString("id")); }
            nodes.put(node); touchProject(project);
        } catch (Exception ignored) { }
        render();
    }

    private void editNode(JSONObject project, String nodeId) {
        JSONObject node = findNode(project, nodeId); if (node == null) return;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(8), dp(4), dp(8), 0);
        EditText title = editText("Заголовок узла"); title.setText(node.optString("title", "")); fields.addView(title, params(-1, 50));
        EditText body = editText("Текст сцены, условия или результата"); body.setSingleLine(false); body.setMinLines(3); body.setGravity(Gravity.TOP); body.setText(node.optString("text", "")); LinearLayout.LayoutParams bp = params(-1, 110); bp.topMargin = dp(8); fields.addView(body, bp);
        EditText options = editText("Варианты по строкам; магазин: Зелье - 10 золота"); options.setSingleLine(false); options.setMinLines(2); options.setGravity(Gravity.TOP);
        JSONArray oldChoices = node.optJSONArray("choices"); StringBuilder optionText = new StringBuilder();
        if (oldChoices != null) for (int i = 0; i < oldChoices.length(); i++) { JSONObject ch = oldChoices.optJSONObject(i); if (ch != null) { if (optionText.length() > 0) optionText.append('\n'); optionText.append(ch.optString("label", "")); } }
        options.setText(optionText.toString()); LinearLayout.LayoutParams op = params(-1, 90); op.topMargin = dp(8); fields.addView(options, op);
        EditText rule = editText("Для проверки: gold>=50, xp>=20, has:Зелье, quest:Название квеста");
        rule.setText(node.optString("condition", ""));
        LinearLayout.LayoutParams ruleLp = params(-1, 50); ruleLp.topMargin = dp(8); fields.addView(rule, ruleLp);
        new AlertDialog.Builder(this).setTitle("Редактировать узел").setView(fields).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (d, w) -> {
            try {
                node.put("title", title.getText().toString().trim().isEmpty() ? "Без названия" : title.getText().toString().trim());
                node.put("text", body.getText().toString().trim());
                JSONArray choices = new JSONArray();
                JSONArray previousChoices = node.optJSONArray("choices");
                for (String line : options.getText().toString().split("\\n")) {
                    String label = line.trim();
                    if (!label.isEmpty()) {
                        int index = choices.length();
                        JSONObject ch = new JSONObject();
                        ch.put("label", label);
                        JSONObject previous = previousChoices == null ? null : previousChoices.optJSONObject(index);
                        ch.put("target", previous == null ? "" : previous.optString("target", ""));
                        choices.put(ch);
                    }
                }
                node.put("choices", choices);
                node.put("condition", rule.getText().toString().trim());
                touchProject(project);
            } catch (Exception ignored) { }
            render();
        }).show();
    }

    private void chooseChoiceTarget(JSONObject project, String sourceId, int choiceIndex) {
        JSONObject source = findNode(project, sourceId);
        JSONArray nodes = worldNodes(project);
        JSONArray choices = source == null ? null : source.optJSONArray("choices");
        if (choices == null || choiceIndex < 0 || choiceIndex >= choices.length()) return;
        String[] labels = new String[nodes.length() + 1];
        labels[0] = "— Завершить ветку —";
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            labels[i + 1] = node == null ? "Узел" : node.optString("title", "Узел");
        }
        new AlertDialog.Builder(this).setTitle("Переход для варианта").setItems(labels, (d, which) -> {
            try {
                JSONObject choice = choices.optJSONObject(choiceIndex);
                if (choice != null) choice.put("target", which == 0 ? "" : nodes.optJSONObject(which - 1).optString("id"));
                touchProject(project);
            } catch (Exception ignored) { }
            render();
        }).setNegativeButton("Отмена", null).show();
    }

    private void chooseNodeTarget(JSONObject project, String sourceId) {
        JSONObject source = findNode(project, sourceId); JSONArray nodes = worldNodes(project);
        if (source == null || nodes.length() < 2) { Toast.makeText(this, "Сначала добавь ещё один узел", Toast.LENGTH_SHORT).show(); return; }
        String[] labels = new String[nodes.length() + 1]; labels[0] = "— Без перехода —";
        for (int i = 0; i < nodes.length(); i++) { JSONObject node = nodes.optJSONObject(i); labels[i + 1] = node == null ? "Узел" : node.optString("title", "Узел"); }
        new AlertDialog.Builder(this).setTitle("Следующий узел").setItems(labels, (d, which) -> {
            try { source.put("next", which == 0 ? "" : nodes.optJSONObject(which - 1).optString("id")); touchProject(project); }
            catch (Exception ignored) { }
            render();
        }).setNegativeButton("Отмена", null).show();
    }

    private void deleteNode(JSONObject project, String nodeId) {
        JSONArray old = worldNodes(project), kept = new JSONArray();
        for (int i = 0; i < old.length(); i++) { JSONObject node = old.optJSONObject(i); if (node == null || nodeId.equals(node.optString("id"))) continue;
            if (nodeId.equals(node.optString("next"))) try { node.put("next", ""); } catch (Exception ignored) { }
            JSONArray choices = node.optJSONArray("choices"); if (choices != null) { JSONArray valid = new JSONArray(); for (int c = 0; c < choices.length(); c++) { JSONObject ch = choices.optJSONObject(c); if (ch != null && !nodeId.equals(ch.optString("target"))) valid.put(ch); } try { node.put("choices", valid); } catch (Exception ignored) { } }
            kept.put(node);
        }
        try { project.put("nodes", kept); if (nodeId.equals(project.optString("startNode"))) project.put("startNode", kept.length() == 0 ? "" : kept.optJSONObject(0).optString("id")); touchProject(project); } catch (Exception ignored) { }
    }

    private void addBotConnectionCard() {
        addSectionTitle("Telegram-бот", "Токен хранится в APK под защитой Android Keystore");
        LinearLayout card = cardColumn();
        String connectedName = preferences.getString(BOT_NAME_KEY, "");
        boolean running = preferences.getBoolean(BOT_RUNNING_KEY, false);
        String serviceStatus = preferences.getString(BOT_STATUS_KEY, "");
        String statusText = running && !serviceStatus.isEmpty() ? serviceStatus : connectedName.isEmpty() ? "Бот ещё не подключён" : "Проверен бот @" + connectedName;
        TextView status = text(statusText, 14, connectedName.isEmpty() ? colorMuted() : colorAccent(), true);
        card.addView(status);
        TextView botFather = actionButton("Создать бота в @BotFather", false);
        LinearLayout.LayoutParams fatherLp = params(-1, 44); fatherLp.topMargin = dp(8); card.addView(botFather, fatherLp);
        botFather.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))); } catch (Exception ignored) { } });
        EditText token = editText("Вставь токен от @BotFather");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams tokenLp = params(-1, 50); tokenLp.topMargin = dp(9); card.addView(token, tokenLp);
        TextView hint = text("Сначала создай бота у @BotFather и вставь выданный токен. Приложение проверяет токен, а после нажатия «Запустить бота» отвечает по сценарию выбранного мира. Кнопка отключения удаляет токен только с телефона; для отзыва используй @BotFather.", 12, colorMuted(), false);
        LinearLayout.LayoutParams hintLp = params(-1, -2); hintLp.topMargin = dp(6); card.addView(hint, hintLp);
        TextView connect = actionButton(connectedName.isEmpty() ? "Подключить бота" : "Проверить подключение", true);
        LinearLayout.LayoutParams connectLp = params(-1, 46); connectLp.topMargin = dp(10); card.addView(connect, connectLp);
        connect.setOnClickListener(v -> connectTelegramBot(token, status, connect));
        TextView openBot = actionButton("Открыть бота в Telegram", false);
        LinearLayout.LayoutParams openLp = params(-1, 44); openLp.topMargin = dp(7); card.addView(openBot, openLp);
        openBot.setEnabled(!connectedName.isEmpty());
        openBot.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + connectedName))); }
            catch (Exception e) { Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show(); }
        });
        TextView runBot = actionButton(running ? "Бот работает · остановить" : "Запустить бота на этом телефоне", true);
        LinearLayout.LayoutParams runLp = params(-1, 46); runLp.topMargin = dp(7); card.addView(runBot, runLp);
        runBot.setEnabled(!connectedName.isEmpty());
        runBot.setOnClickListener(v -> {
            if (preferences.getBoolean(BOT_RUNNING_KEY, false)) stopLocalBot();
            else new AlertDialog.Builder(this).setTitle("Запустить бота на телефоне?")
                .setMessage("APK будет получать сообщения Telegram через long polling и отвечать по сценарию выбранного мира. Телефон должен быть включён и подключён к интернету. Это переключает бота с webhook на polling, если webhook был настроен. Режим Mini App требует отдельного HTTPS-хостинга.")
                .setNegativeButton("Отмена", null).setPositiveButton("Запустить", (d, w) -> startLocalBot()).show();
        });
        TextView disconnect = actionButton("Отключить бота", false);
        LinearLayout.LayoutParams disconnectLp = params(-1, 44); disconnectLp.topMargin = dp(7); card.addView(disconnect, disconnectLp);
        disconnect.setOnClickListener(v -> {
            stopService(new Intent(this, WorldBotService.class));
            try {
                preferences.edit().remove(BOT_TOKEN_CIPHER_KEY).remove(BOT_TOKEN_IV_KEY).remove(BOT_NAME_KEY).remove("telegram_update_offset").putBoolean(BOT_RUNNING_KEY, false).apply();
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore"); keyStore.load(null);
                if (keyStore.containsAlias(BOT_TOKEN_KEY_ALIAS)) keyStore.deleteEntry(BOT_TOKEN_KEY_ALIAS);
            } catch (Exception ignored) { }
            Toast.makeText(this, "Токен удалён с устройства", Toast.LENGTH_SHORT).show(); render();
        });
        page.addView(card);
        TextView limitation = text("Запущенный режим обрабатывает сообщения ботом на этом телефоне, пока Android держит foreground-сервис активным и есть интернет. Это не облачный сервер: при выключенном телефоне бот не отвечает. Для Mini App всё равно нужен публичный HTTPS-адрес, а для надёжной круглосуточной игры — хостинг/backend.", 12, colorMuted(), false);
        LinearLayout.LayoutParams limitationLp = params(-1, -2); limitationLp.topMargin = dp(8); page.addView(limitation, limitationLp);
    }

    private SecretKey telegramTokenKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(BOT_TOKEN_KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(BOT_TOKEN_KEY_ALIAS, null);
            return entry.getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(BOT_TOKEN_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private void storeTelegramToken(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, telegramTokenKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
            .putString(BOT_TOKEN_CIPHER_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(BOT_TOKEN_IV_KEY, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .apply();
    }

    private String readTelegramToken() throws Exception {
        String encrypted = preferences.getString(BOT_TOKEN_CIPHER_KEY, "");
        String iv = preferences.getString(BOT_TOKEN_IV_KEY, "");
        if (encrypted.isEmpty() || iv.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, telegramTokenKey(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private void startLocalBot() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            Toast.makeText(this, "Разреши постоянное уведомление и затем снова нажми «Запустить бота»", Toast.LENGTH_LONG).show();
            return;
        }
        Intent service = new Intent(this, WorldBotService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            preferences.edit().putBoolean(BOT_RUNNING_KEY, true).putString(BOT_STATUS_KEY, "Запускаю бота на этом телефоне").apply();
            Toast.makeText(this, "Бот запускается. Android покажет постоянное уведомление.", Toast.LENGTH_LONG).show();
            render();
        } catch (Exception e) {
            preferences.edit().putBoolean(BOT_RUNNING_KEY, false).apply();
            Toast.makeText(this, "Android не разрешил запустить сервис бота", Toast.LENGTH_LONG).show();
        }
    }

    private void stopLocalBot() {
        stopService(new Intent(this, WorldBotService.class));
        preferences.edit().putBoolean(BOT_RUNNING_KEY, false).putString(BOT_STATUS_KEY, "Бот остановлен").apply();
        Toast.makeText(this, "Бот остановлен", Toast.LENGTH_SHORT).show();
        render();
    }

    private void connectTelegramBot(EditText tokenInput, TextView status, TextView button) {
        String token = tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            try { token = readTelegramToken(); } catch (Exception ignored) { token = ""; }
        }
        if (token.isEmpty() || token.contains(" ")) {
            tokenInput.setError("Вставь токен от @BotFather");
            return;
        }
        final String candidate = token;
        String storedToken = "";
        try { storedToken = readTelegramToken(); } catch (Exception ignored) { }
        final boolean tokenChanged = !candidate.equals(storedToken);
        status.setText("Проверяю токен в Telegram…"); status.setTextColor(colorMuted());
        button.setEnabled(false); button.setText("Подключаюсь…");
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.telegram.org/bot" + candidate + "/getMe");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET"); connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                JSONObject response = new JSONObject(readAll(connection.getInputStream()));
                if (!response.optBoolean("ok", false)) throw new IllegalStateException("Telegram не принял токен");
                JSONObject result = response.optJSONObject("result");
                String username = result == null ? "" : result.optString("username", "");
                runOnUiThread(() -> {
                    try {
                        String previousName = preferences.getString(BOT_NAME_KEY, "");
                        if (tokenChanged && preferences.getBoolean(BOT_RUNNING_KEY, false)) {
                            stopService(new Intent(this, WorldBotService.class));
                            preferences.edit().putBoolean(BOT_RUNNING_KEY, false).putString(BOT_STATUS_KEY, "Токен обновлён · запусти бота снова").apply();
                        }
                        if (!previousName.equals(username)) preferences.edit().remove("telegram_update_offset").apply();
                        storeTelegramToken(candidate);
                        preferences.edit().putString(BOT_NAME_KEY, username).apply();
                        status.setText(username.isEmpty() ? "Бот проверен в Telegram" : "Подключён: @" + username);
                        status.setTextColor(colorAccent());
                        tokenInput.setText("");
                        Toast.makeText(this, "Токен проверен и защищённо сохранён", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        status.setText("Не удалось безопасно сохранить токен на устройстве"); status.setTextColor(colorMuted());
                    }
                    button.setEnabled(true); button.setText("Проверить подключение");
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Не удалось проверить токен. Проверь его и интернет-соединение."); status.setTextColor(colorMuted());
                    button.setEnabled(true); button.setText("Повторить подключение");
                });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void renderAdmin() {
        JSONObject project = findProject(editingId); if (project == null) { screen = "projects"; return; }
        addBrandHeader("Администрирование", "ЛОКАЛЬНЫЙ МИР"); addSpace(12);
        addBotConnectionCard();
        addSectionTitle("Параметры мира", "Настройки выбранного проекта");
        TextView intro = text("Токен бота хранится отдельно и шифруется ключом Android Keystore; в данные игрового мира он не попадает.", 13, colorMuted(), false); LinearLayout.LayoutParams il = params(-1, -2); il.topMargin = dp(5); page.addView(intro, il);
        LinearLayout card = cardColumn(); LinearLayout.LayoutParams cl = params(-1, -2); cl.topMargin = dp(14); page.addView(card, cl);
        card.addView(text("Название мира", 14, colorText(), true)); EditText name = editText("Название"); name.setText(project.optString("name", "")); LinearLayout.LayoutParams nl = params(-1, 50); nl.topMargin = dp(7); card.addView(name, nl);
        TextView save = actionButton("Сохранить параметры", true); LinearLayout.LayoutParams sl = params(-1, 46); sl.topMargin = dp(12); card.addView(save, sl);
        save.setOnClickListener(v -> { String value = name.getText().toString().trim(); if (value.isEmpty()) { name.setError("Укажи название"); return; } try { project.put("name", value); } catch (Exception ignored) { } touchProject(project); Toast.makeText(this, "Настройки мира сохранены", Toast.LENGTH_SHORT).show(); screen = "editor"; render(); });
        TextView technical = text("Игроки: пока локальный прототип без общей базы. Для постоянного прогресса и мультиплеера подключи backend и проверку Telegram initData на сервере.", 13, colorMuted(), false); LinearLayout.LayoutParams tp = params(-1, -2); tp.topMargin = dp(16); page.addView(technical, tp);
        TextView back = actionButton("Назад в конструктор", false); LinearLayout.LayoutParams bl = params(-1, 46); bl.topMargin = dp(16); page.addView(back, bl); back.setOnClickListener(v -> { screen = "editor"; render(); });
    }

    private void addFeatureLine(LinearLayout target, String title, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(2));
        TextView check = text("✓", 16, colorAccent(), true);
        row.addView(check, new LinearLayout.LayoutParams(dp(28), -2));
        LinearLayout detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
        detail.addView(text(title, 13, colorText(), true));
        detail.addView(text(description, 11, colorMuted(), false));
        row.addView(detail, params(-1, -2)); target.addView(row);
    }

    private void addThemeOption(LinearLayout target, String value, String title, String subtitle) {
        TextView option = text((themeChoice.equals(value) ? "●  " : "○  ") + title + "\n    " + subtitle, 13, themeChoice.equals(value) ? colorPrimary() : colorText(), themeChoice.equals(value));
        option.setPadding(dp(4), dp(9), dp(4), dp(9));
        option.setMinHeight(dp(48));
        option.setFocusable(true);
        target.addView(option);
        option.setOnClickListener(v -> {
            themeChoice = value;
            preferences.edit().putString("theme", value).apply();
            render();
        });
    }

    private void addTemplateCard(Template template, boolean selectable) { addTemplateCardTo(page, template, selectable); }

    private void addTemplateCardTo(LinearLayout target, Template template, boolean selectable) {
        LinearLayout card = cardColumn();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text(template.emoji, 23, colorPrimary(), true); mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(dark ? "#30314C" : "#F0EFFF", 14));
        top.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.setMargins(dp(12), 0, 0, 0);
        titleBox.addView(text(template.title, 16, colorText(), true));
        TextView category = text(template.category + "  ·  " + template.tag, 11, colorPrimary(), true);
        LinearLayout.LayoutParams categoryLp = params(-1, -2); categoryLp.topMargin = dp(2); titleBox.addView(category, categoryLp);
        top.addView(titleBox, titleParams);
        card.addView(top);
        TextView description = text(template.description, 13, colorMuted(), false);
        LinearLayout.LayoutParams descLp = params(-1, -2); descLp.topMargin = dp(10); card.addView(description, descLp);
        TextView action = text(selectable ? "Выбрать эту механику   ›" : "Создать проект   ›", 13, colorPrimary(), true);
        LinearLayout.LayoutParams actionLp = params(-1, 44); actionLp.topMargin = dp(7); card.addView(action, actionLp);
        action.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true); card.setClickable(true);
        card.setOnClickListener(v -> {
            selectedTemplate = template.id;
            editingId = "";
            screen = "builder";
            activeTab = TAB_TEMPLATES;
            render();
        });
        LinearLayout.LayoutParams lp = params(-1, -2); lp.topMargin = dp(10); target.addView(card, lp);
    }

    private void addBrandHeader(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text(title, 21, colorText(), true);
        row.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(subtitle, 10, colorPrimary(), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(8), dp(10), dp(8));
        badge.setBackground(round(dark ? "#30314C" : "#EFEDFF", 999));
        row.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        page.addView(row);
    }

    private void addSectionTitle(String title, String subtitle) {
        TextView t = text(title, 18, colorText(), true);
        LinearLayout.LayoutParams tl = params(-1, -2); tl.topMargin = dp(22); page.addView(t, tl);
        TextView s = text(subtitle, 12, colorMuted(), false);
        LinearLayout.LayoutParams sl = params(-1, -2); sl.topMargin = dp(3); page.addView(s, sl);
    }

    private void navigate(int tab) {
        activeTab = tab;
        screen = tab == TAB_TEMPLATES ? "templates" : tab == TAB_PROJECTS ? "projects" : tab == TAB_SETTINGS ? "settings" : "home";
        render();
    }

    private LinearLayout cardColumn() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(colorCard(), 18, colorLine(), 1));
        return card;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(true);
        return view;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(label, 14, primary ? Color.WHITE : colorPrimary(), true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setFocusable(true);
        button.setClickable(true);
        int fill = primary ? colorPrimary() : Color.parseColor(dark ? "#2A3448" : "#F0EFFF");
        button.setBackground(round(fill, 13, primary ? colorPrimary() : colorLine(), 1));
        return button;
    }

    private EditText editText(String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(14);
        edit.setTextColor(colorText());
        edit.setHintTextColor(colorMuted());
        edit.setHint(hint);
        edit.setPadding(dp(13), dp(10), dp(13), dp(10));
        edit.setBackground(round(colorSurface(), 12, colorLine(), 1));
        edit.setSelectAllOnFocus(false);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        return edit;
    }

    private GradientDrawable round(String color, int radius) { return round(Color.parseColor(color), radius, Color.TRANSPARENT, 0); }
    private GradientDrawable round(int color, int radius) { return round(color, radius, Color.TRANSPARENT, 0); }
    private GradientDrawable round(String color, int radius, int stroke, int strokeDp) { return round(Color.parseColor(color), radius, stroke, strokeDp); }
    private GradientDrawable round(int color, int radius, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private void addSpace(int height) { View space = new View(this); page.addView(space, params(1, height)); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void loadProjects() {
        projects.clear();
        String raw = preferences.getString(PROJECTS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) projects.add(array.getJSONObject(i));
        } catch (Exception ignored) { projects.clear(); }
    }

    private void saveProjects() {
        JSONArray array = new JSONArray();
        for (JSONObject project : projects) array.put(project);
        preferences.edit().putString(PROJECTS_KEY, array.toString()).apply();
    }

    private JSONObject saveProject(String name, String template, String existingId) {
        JSONObject project = findProject(existingId);
        if (project == null) project = new JSONObject();
        String id = existingId == null || existingId.isEmpty() ? Long.toString(System.currentTimeMillis()) : existingId;
        try {
            project.put("id", id); project.put("name", name); project.put("template", template); project.put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) { }
        if (findProject(id) == null) projects.add(project);
        saveProjects();
        return project;
    }

    private JSONObject findProject(String id) {
        if (id == null || id.isEmpty()) return null;
        for (JSONObject project : projects) if (id.equals(project.optString("id"))) return project;
        return null;
    }

    private void confirmDelete(String id) {
        new AlertDialog.Builder(this).setTitle("Удалить проект?").setMessage("Архив можно будет создать заново, но черновик будет удалён.").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> {
            JSONObject item = findProject(id); if (item != null) projects.remove(item); saveProjects(); render();
        }).show();
    }

    private Template findTemplate(String id) {
        for (Template template : TEMPLATES) if (template.id.equals(id)) return template;
        return TEMPLATES[0];
    }

    private String formatDate(Date date) { return new SimpleDateFormat("d MMM yyyy", new Locale("ru" )).format(date); }

    private String currentVersion() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? "0.1.0" : version;
        } catch (PackageManager.NameNotFoundException ignored) { return "0.1.0"; }
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, filter);
        receiverRegistered = true;
    }

    private void checkForUpdates(TextView button) {
        button.setEnabled(false);
        button.setText("Проверяю…");
        if (updateStatus != null) updateStatus.setText("Подключаюсь к серверу обновлений…");
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Botcraft-Studio-Android");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                String body = readAll(connection.getInputStream());
                JSONObject release = new JSONObject(body);
                String latest = release.optString("tag_name", "").replaceFirst("^v", "");
                String downloadUrl = "";
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && "botcraft-studio.apk".equals(asset.optString("name"))) { downloadUrl = asset.optString("browser_download_url"); break; }
                }
                final String resultVersion = latest;
                final String resultUrl = downloadUrl;
                runOnUiThread(() -> {
                    String current = currentVersion();
                    if (latestVersionIsNewer(resultVersion, current)) {
                        if (resultUrl.isEmpty()) setUpdateMessage("Новая версия найдена, но APK пока не приложен.", false);
                        else new AlertDialog.Builder(this).setTitle("Доступно обновление " + resultVersion).setMessage("Скачаю APK. Затем Android покажет системное окно — подтвердите обновление.").setNegativeButton("Позже", (d, w) -> setUpdateMessage("Обновление доступно: " + resultVersion, true)).setPositiveButton("Скачать", (d, w) -> startApkDownload(resultUrl)).show();
                    } else setUpdateMessage("У тебя актуальная версия " + current + ".", true);
                    button.setEnabled(true); button.setText("Проверить обновления");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setUpdateMessage("Не удалось проверить обновления. Проверь интернет и попробуй ещё раз.", false); button.setEnabled(true); button.setText("Повторить проверку"); });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void setUpdateMessage(String message, boolean success) {
        if (updateStatus != null) { updateStatus.setText(message); updateStatus.setTextColor(success ? colorAccent() : colorMuted()); }
    }

    private boolean latestVersionIsNewer(String latest, String current) {
        try {
            String[] a = latest.split("\\."), b = current.split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int av = i < a.length ? Integer.parseInt(a[i].replaceAll("\\D.*$", "")) : 0;
                int bv = i < b.length ? Integer.parseInt(b[i].replaceAll("\\D.*$", "")) : 0;
                if (av != bv) return av > bv;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void startApkDownload(String url) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Botcraft Studio — обновление");
            request.setDescription("Загружается новая версия приложения");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "botcraft-studio-update.apk");
            pendingDownloadId = manager.enqueue(request);
            setUpdateMessage("Загрузка началась. Когда завершится, открою установщик Android.", true);
        } catch (Exception e) { setUpdateMessage("Не удалось начать загрузку APK.", false); }
    }

    private void handleDownloadedApk(long id) {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        android.database.Cursor cursor = manager.query(query);
        try {
            if (cursor == null || !cursor.moveToFirst()) { setUpdateMessage("Не удалось найти загруженный APK.", false); return; }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL) { setUpdateMessage("Загрузка не завершилась. Попробуй проверить обновления ещё раз.", false); return; }
            Uri apk = manager.getUriForDownloadedFile(id);
            setUpdateMessage("APK загружен. Открываю установщик Android…", true);
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                pendingApkUri = apk;
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, INSTALL_PERMISSION_REQUEST);
            } else openInstaller(apk);
        } catch (Exception e) { setUpdateMessage("Android не смог открыть системную установку.", false); }
        finally { if (cursor != null) cursor.close(); }
    }

    private Uri pendingApkUri;

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INSTALL_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) openInstaller(pendingApkUri);
            else setUpdateMessage("Разрешение не включено. Его можно дать в настройках Android и повторить обновление.", false);
        }
    }

    private void openInstaller(Uri uri) {
        if (uri == null) { setUpdateMessage("Не найден APK для установки.", false); return; }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) { setUpdateMessage("Не удалось открыть системный установщик Android.", false); }
    }

    private String readAll(InputStream input) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(input); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private static class Template {
        final String id, title, category, description, tag, emoji;
        Template(String id, String title, String category, String description, String tag, String emoji) {
            this.id = id; this.title = title; this.category = category; this.description = description; this.tag = tag; this.emoji = emoji;
        }
    }
}
