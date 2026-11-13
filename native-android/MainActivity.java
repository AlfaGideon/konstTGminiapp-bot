package ru.botcraft.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
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
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A fully native Android shell and project studio. It intentionally never creates a WebView. */
public class MainActivity extends Activity {
    private static final String PREFS = "botcraft-native";
    private static final String PROJECTS_KEY = "projects";
    private static final int INSTALL_PERMISSION_REQUEST = 411;
    private static final int TAB_HOME = 0;
    private static final int TAB_TEMPLATES = 1;
    private static final int TAB_PROJECTS = 2;
    private static final int TAB_SETTINGS = 3;
    private static final String RELEASES_API = "https://api.github.com/repos/AlfaGideon/konstTGminiapp-bot/releases/latest";

    private static final Template[] TEMPLATES = new Template[] {
        new Template("quiz", "Квиз-арена", "Викторина", "Собери друзей, отвечайте на вопросы и поднимайтесь в таблице лидеров.", "Командная", "🧠"),
        new Template("clicker", "Тап-фабрика", "Кликер", "Запускай производство, открывай апгрейды и соревнуйся за рекорд.", "На каждый день", "⚡"),
        new Template("memory", "Мемори-дуэль", "Пазл", "Находи пары быстрее соперников. Несколько размеров поля и личный рекорд.", "На скорость", "🃏"),
        new Template("word", "Словолов", "Слова", "Отгадывай слово по подсказкам и делись результатом в чате.", "На логику", "АБ"),
        new Template("reaction", "Реакция", "Аркада", "Жди сигнал, нажимай вовремя и сравнивай время реакции.", "На рекорд", "↯"),
        new Template("daily", "Миссия дня", "Квест", "Короткое ежедневное задание с серией дней и наградой за возвращение.", "Серия дней", "✦")
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
    private String selectedTemplate = "quiz";
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
        if (!"home".equals(screen)) {
            screen = "home";
            activeTab = TAB_HOME;
            render();
        } else {
            super.onBackPressed();
        }
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
        String[] labels = {"Главная", "Игры", "Проекты", "Настройки"};
        String[] marks = {"⌂", "✦", "▤", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            boolean selected = ("builder".equals(screen) ? TAB_TEMPLATES : activeTab) == i;
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
        addBrandHeader("Botcraft", "СТУДИЯ ИГР");
        addSpace(20);
        TextView eyebrow = text("ТВОЯ ИДЕЯ — УЖЕ ИГРА", 11, colorPrimary(), true);
        page.addView(eyebrow);
        TextView title = text("Создавай игры\nдля Telegram", 31, colorText(), true);
        title.setLineSpacing(dp(1), 1.0f);
        LinearLayout.LayoutParams titleLp = params(-1, -2);
        titleLp.topMargin = dp(8);
        page.addView(title, titleLp);
        TextView intro = text("Собери механику, настрой правила, проверь проект и подготовь его к запуску — всё в одном приложении.", 15, colorMuted(), false);
        intro.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams introLp = params(-1, -2);
        introLp.topMargin = dp(10);
        page.addView(intro, introLp);

        LinearLayout start = cardColumn();
        start.setBackground(round(dark ? "#292744" : "#EEEFFF", 22));
        TextView badge = text("КОНСТРУКТОР · БЕЗ КОДА НА СТАРТЕ", 10, colorPrimary(), true);
        start.addView(badge);
        TextView startTitle = text("Какую игру\nсоздадим сегодня?", 21, colorText(), true);
        LinearLayout.LayoutParams st = params(-1, -2); st.topMargin = dp(8); start.addView(startTitle, st);
        TextView startCopy = text("Выбери сценарий — дальше можно настроить название и сохранить проект на устройстве.", 13, colorMuted(), false);
        LinearLayout.LayoutParams sc = params(-1, -2); sc.topMargin = dp(5); start.addView(startCopy, sc);
        TextView create = actionButton("＋   Выбрать игру", true);
        LinearLayout.LayoutParams cp = params(-1, 50); cp.topMargin = dp(16); start.addView(create, cp);
        create.setOnClickListener(v -> navigate(TAB_TEMPLATES));
        LinearLayout.LayoutParams slp = params(-1, -2); slp.topMargin = dp(18); page.addView(start, slp);

        addSectionTitle("С чего начать", "Подборка для первого проекта");
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
        addBrandHeader("Каталог игр", "6 СЦЕНАРИЕВ");
        addSpace(16);
        TextView title = text("Выбери механику", 26, colorText(), true);
        page.addView(title);
        TextView copy = text("Каждый сценарий — отправная точка. Можно переименовать игру, сохранить проект и выгрузить стартовый архив.", 14, colorMuted(), false);
        LinearLayout.LayoutParams cp = params(-1, -2); cp.topMargin = dp(6); page.addView(copy, cp);
        EditText search = editText("Поиск по играм");
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
        TextView tipText = text("Квиз подходит для компании, кликер — для коротких сессий, а «Миссия дня» помогает возвращать игроков.", 12, colorMuted(), false);
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
        addBrandHeader("Мои проекты", projects.size() + " СОХРАНЕНО");
        addSpace(16);
        page.addView(text("Твоя коллекция", 26, colorText(), true));
        TextView copy = text("Черновики остаются на телефоне и доступны без сети.", 14, colorMuted(), false);
        LinearLayout.LayoutParams cp = params(-1, -2); cp.topMargin = dp(5); page.addView(copy, cp);
        TextView newProject = actionButton("＋   Новый проект", true);
        LinearLayout.LayoutParams np = params(-1, 50); np.topMargin = dp(16); page.addView(newProject, np);
        newProject.setOnClickListener(v -> navigate(TAB_TEMPLATES));
        addSpace(10);
        if (projects.isEmpty()) {
            LinearLayout empty = cardColumn();
            empty.setGravity(Gravity.CENTER);
            TextView emptyMark = text("✦", 28, colorPrimary(), true); emptyMark.setGravity(Gravity.CENTER); empty.addView(emptyMark);
            TextView emptyTitle = text("Первый проект начинается с идеи", 16, colorText(), true); emptyTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams et = params(-1, -2); et.topMargin = dp(7); empty.addView(emptyTitle, et);
            TextView emptyCopy = text("Выбери механику из каталога — и сохрани свой первый черновик.", 13, colorMuted(), false); emptyCopy.setGravity(Gravity.CENTER);
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
            TextView export = actionButton("Скачать архив", false);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(44), 1); ep.topMargin = dp(12); actions.addView(export, ep);
            TextView remove = actionButton("Удалить", false);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(44), 1); rp.setMargins(dp(8), dp(12), 0, 0); actions.addView(remove, rp);
            String projectName = project.optString("name", "game");
            export.setOnClickListener(v -> exportProject(projectName, t));
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
        TextView storageCopy = text("Проекты хранятся на этом устройстве. Их можно отдельно экспортировать в ZIP.", 12, colorMuted(), false);
        LinearLayout.LayoutParams sl = params(-1, -2); sl.topMargin = dp(4); storage.addView(storageCopy, sl);
        TextView clear = actionButton("Очистить проекты", false);
        LinearLayout.LayoutParams cl = params(-1, 46); cl.topMargin = dp(12); storage.addView(clear, cl);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Удалить все проекты?").setMessage("Действие нельзя отменить. Сначала скачай архивы, если хочешь сохранить исходники.").setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> { projects.clear(); saveProjects(); render(); }).show());
        page.addView(storage);

        TextView about = text("Botcraft Studio · Android\nНативный интерфейс · проекты доступны офлайн", 11, colorMuted(), false);
        about.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ab = params(-1, -2); ab.topMargin = dp(20); page.addView(about, ab);
    }

    private void renderBuilder() {
        Template template = findTemplate(selectedTemplate);
        addBrandHeader("Новый проект", "ШАГ 1 ИЗ 2");
        TextView back = text("‹   Назад к играм", 14, colorPrimary(), true);
        LinearLayout.LayoutParams bp = params(-1, 44); bp.topMargin = dp(8); page.addView(back, bp);
        back.setGravity(Gravity.CENTER_VERTICAL); back.setOnClickListener(v -> navigate(TAB_TEMPLATES));
        addSpace(8);
        TextView title = text(template.emoji + "  " + template.title, 25, colorText(), true);
        page.addView(title);
        TextView copy = text(template.description, 14, colorMuted(), false);
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
        TextView typeLabel = text("Что будет в архиве", 14, colorText(), true);
        LinearLayout.LayoutParams typeLp = params(-1, -2); typeLp.topMargin = dp(18); form.addView(typeLabel, typeLp);
        addFeatureLine(form, "Игровой экран", "Стартовый интерфейс и игровая механика");
        addFeatureLine(form, "Telegram-бот", "Команда /start и кнопка запуска Mini App");
        addFeatureLine(form, "Инструкция", "README с шагами публикации");
        TextView save = actionButton(editingId.isEmpty() ? "Сохранить и скачать ZIP" : "Сохранить изменения", true);
        LinearLayout.LayoutParams saveLp = params(-1, 52); saveLp.topMargin = dp(18); form.addView(save, saveLp);
        save.setOnClickListener(v -> {
            String projectName = name.getText().toString().trim();
            if (projectName.isEmpty()) { name.setError("Укажи название проекта"); name.requestFocus(); return; }
            JSONObject saved = saveProject(projectName, template.id, editingId);
            editingId = saved.optString("id", "");
            Toast.makeText(this, "Проект сохранён", Toast.LENGTH_SHORT).show();
            exportProject(projectName, template);
        });
        TextView privacy = text("Бот-токен в архив не добавляется — его подключают на своём сервере после экспорта.", 12, colorMuted(), false);
        LinearLayout.LayoutParams privacyLp = params(-1, -2); privacyLp.topMargin = dp(12); page.addView(privacy, privacyLp);
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

    private void exportProject(String projectName, Template template) {
        worker.execute(() -> {
            File out = null;
            try {
                File dir = new File(getCacheDir(), "exports");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Не удалось создать папку архива");
                String fileName = safeFileName(projectName) + ".zip";
                out = new File(dir, fileName);
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
                    putZip(zip, "miniapp/index.html", gameHtml(projectName, template));
                    putZip(zip, "bot/package.json", "{\n  \"name\": \"" + safeFileName(projectName) + "-bot\",\n  \"version\": \"1.0.0\",\n  \"private\": true,\n  \"scripts\": { \"start\": \"node index.js\" },\n  \"dependencies\": { \"dotenv\": \"^16.4.7\", \"node-telegram-bot-api\": \"^0.66.0\" }\n}\n");
                    putZip(zip, "bot/.env.example", "BOT_TOKEN=\nMINI_APP_URL=https://your-domain.example/\n");
                    putZip(zip, "bot/index.js", botSource());
                    putZip(zip, "README.md", readme(projectName, template));
                }
                File finalOut = out;
                runOnUiThread(() -> shareArchive(finalOut, projectName));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "Не удалось собрать ZIP: " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void putZip(ZipOutputStream zip, String path, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String safeFileName(String value) {
        String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9а-яё]+", "-").replaceAll("^-|-$", "");
        return safe.isEmpty() ? "telegram-game" : safe;
    }

    private void shareArchive(File file, String name) {
        if (file == null || !file.exists()) { Toast.makeText(this, "Архив не найден", Toast.LENGTH_SHORT).show(); return; }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/zip");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "Проект Botcraft Studio — " + name);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Сохранить или отправить ZIP"));
        } catch (Exception e) { Toast.makeText(this, "Не удалось открыть экспорт: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private String readme(String name, Template template) {
        return "# " + name + "\n\n" + template.description + "\n\n## Что внутри\n- `miniapp/index.html` — автономный игровой экран.\n- `bot/index.js` — Telegram-бот с кнопкой открытия Mini App.\n- `bot/.env.example` — переменные окружения, токен не включён.\n\n## Запуск бота\n1. Установите Node.js 20 или новее.\n2. В папке `bot` выполните `npm install`.\n3. Скопируйте `.env.example` в `.env` и добавьте токен от @BotFather.\n4. Укажите публичный HTTPS URL Mini App в `MINI_APP_URL`.\n5. Запустите `npm start`.\n\n## Публикация Mini App\nЗагрузите содержимое `miniapp` на HTTPS-хостинг и укажите этот адрес в BotFather.\n";
    }

    private String botSource() {
        return "require('dotenv').config();\nconst TelegramBot = require('node-telegram-bot-api');\nconst token = process.env.BOT_TOKEN;\nif (!token) throw new Error('Set BOT_TOKEN in .env');\nconst appUrl = process.env.MINI_APP_URL;\nif (!appUrl || !appUrl.startsWith('https://')) throw new Error('Set a public HTTPS MINI_APP_URL');\nconst bot = new TelegramBot(token, { polling: true });\nbot.onText(/\\/start/, (msg) => {\n  bot.sendMessage(msg.chat.id, 'Готов сыграть? Открой игру кнопкой ниже.', {\n    reply_markup: { inline_keyboard: [[{ text: 'Играть', web_app: { url: appUrl } }]] }\n  });\n});\nconsole.log('Bot is running');\n";
    }

    private String gameHtml(String name, Template template) {
        String safeTitle = escapeHtml(name);
        String gameScript;
        if ("clicker".equals(template.id)) {
            gameScript = "<p>Очки: <b id='score'>0</b></p><button id='tap'>Тапни и собери заряд</button><script>let n=0;document.getElementById('tap').onclick=()=>document.getElementById('score').textContent=++n;</script>";
        } else if ("memory".equals(template.id)) {
            gameScript = "<p>Найди одинаковые символы</p><div id='board' class='board'></div><script>const a=['☀','☀','◆','◆','●','●'].sort(()=>Math.random()-.5);let open=[];a.forEach((x,i)=>{let b=document.createElement('button');b.textContent='?';b.onclick=()=>{if(open.length>1||b.dataset.done)return;b.textContent=x;open.push(b);if(open.length===2){if(open[0].textContent===open[1].textContent){open.forEach(c=>c.dataset.done=1);open=[]}else setTimeout(()=>{open.forEach(c=>c.textContent='?');open=[]},650)}};document.getElementById('board').appendChild(b)});</script>";
        } else if ("word".equals(template.id)) {
            gameScript = "<p>Слово из пяти букв. Подсказка: оно бывает у моря.</p><form id='guess'><input id='word' maxlength='5' placeholder='Твоё слово' required><button>Проверить</button></form><p id='result'></p><script>document.getElementById('guess').onsubmit=e=>{e.preventDefault();document.getElementById('result').textContent=document.getElementById('word').value.toLowerCase()==='волна'?'Верно! Отличная догадка.':'Пока не угадал — попробуй ещё раз.'}</script>";
        } else if ("reaction".equals(template.id)) {
            gameScript = "<p>Нажми, когда кнопка станет зелёной</p><button id='wait'>Начать попытку</button><p id='result'></p><script>let t;const b=document.getElementById('wait');b.onclick=()=>{if(b.dataset.ready){document.getElementById('result').textContent='Твоя реакция: '+(Date.now()-t)+' мс';b.dataset.ready='';b.textContent='Ещё раз';return}b.textContent='Жди сигнал…';setTimeout(()=>{b.dataset.ready='1';b.textContent='ЖМИ!';t=Date.now()},900+Math.random()*1800)}</script>";
        } else if ("daily".equals(template.id)) {
            gameScript = "<p>Выполни пять действий, чтобы закрыть миссию дня.</p><p>Прогресс: <b id='n'>0</b> / 5</p><button id='do'>Выполнить действие</button><p id='done'></p><script>let n=0;document.getElementById('do').onclick=()=>{n=Math.min(5,n+1);document.getElementById('n').textContent=n;if(n===5)document.getElementById('done').textContent='Миссия выполнена! Возвращайся завтра.'}</script>";
        } else {
            gameScript = "<p id='q'>Какой язык запускается в браузере?</p><div class='answers'><button data-a='0'>JavaScript</button><button data-a='1'>SQL</button><button data-a='1'>Bash</button></div><p id='result'></p><script>document.querySelectorAll('[data-a]').forEach(b=>b.onclick=()=>document.getElementById('result').textContent=b.dataset.a==='0'?'Верно! +1 очко':'Попробуй ещё раз.')</script>";
        }
        return "<!doctype html><html lang='ru'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><meta name='theme-color' content='#f4f6fa'><title>" + safeTitle + "</title><style>*{box-sizing:border-box}body{font:16px system-ui,sans-serif;margin:0;padding:22px;background:#f4f6fa;color:#20243a}.app{max-width:520px;margin:auto}.brand{font-size:12px;font-weight:800;letter-spacing:1.3px;color:#5549cc}.hero{margin:18px 0 20px}.hero h1{font-size:30px;line-height:1.12;margin:5px 0}.hero p{color:#586276;line-height:1.55}.card{background:#fff;border:1px solid #e0e5ee;border-radius:20px;padding:20px;box-shadow:0 8px 28px #25294a0c}button,input{font:inherit}button{display:block;width:100%;min-height:48px;margin:8px 0;padding:12px 14px;border:1px solid #dfe3ec;border-radius:12px;background:#f8f8fc;color:#252a40;font-weight:700}button:active{background:#efedff;border-color:#5549cc}.board{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.board button{height:76px;font-size:24px}.answers{display:grid;gap:6px}input{width:100%;padding:12px;border:1px solid #dfe3ec;border-radius:10px}#tap{background:#5549cc;color:white}.foot{margin-top:18px;text-align:center;font-size:12px;color:#677084}</style><main class='app'><div class='brand'>BOTCRAFT · TELEGRAM GAME</div><header class='hero'><h1>" + safeTitle + "</h1><p>" + escapeHtml(template.description) + "</p></header><section class='card'>" + gameScript + "</section><p class='foot'>Небольшая игра, собранная в Botcraft Studio</p></main></html>";
    }

    private String escapeHtml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }

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
