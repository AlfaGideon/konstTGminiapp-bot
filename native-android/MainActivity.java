package ru.botcraft.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Dialog;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
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
    private static final String STARTER_WORLD_ID = "demo-north-archipelago";
    private static final String CAMPAIGN_SKIN_KEY = "campaign_player_skin";
    private static final String[][] CAMPAIGN_SKINS = {
        {"archer_trail_f", "🏹", "Лучник · девушка", "archer-free-female.png"},
        {"archer_trail_m", "🏹", "Лучник · парень", "archer-free-male.jpg"}
    };
    private static final String BOT_TOKEN_KEY_ALIAS = "botcraft-telegram-token-v1";
    private static final int INSTALL_PERMISSION_REQUEST = 411;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 412;
    private static final int NODE_IMAGE_REQUEST = 413;
    private static final int TAB_HOME = 0;
    private static final int TAB_TEMPLATES = 1;
    private static final int TAB_PROJECTS = 2;
    private static final int TAB_SETTINGS = 3;
    private static final String RELEASES_API = "https://api.github.com/repos/AlfaGideon/konstTGminiapp-bot/releases/latest";

    private static final Template[] TEMPLATES = new Template[] {
        new Template("start", "Вход в мир", "Сценарий", "Стартовый узел: приветствие, выбор персонажа и первое действие игрока.", "Основа", "⌂"),
        new Template("dialogue", "Диалог", "Диалог", "Реплики NPC, варианты ответа и переход к следующему шагу.", "История", "…"),
        new Template("illustration", "Иллюстрированная сцена", "Медиа", "Сюжетный экран с собственной PNG, JPEG или GIF-картинкой.", "История", "▧"),
        new Template("ending", "Финал главы", "История", "Финальный экран главы с иллюстрацией и переходом к продолжению.", "История", "⚑"),
        new Template("npc", "NPC-персонаж", "Персонажи", "Имя, портрет, характер, реплики и заменяемая иллюстрация.", "Мир", "♙"),
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
    private String adminReturnScreen = "home";
    private String selectedTemplate = "start";
    private String editingId = "";
    private String selectedNodeId = "";
    private String originalNodeImage = "";
    private ImageView nodeImagePreview;
    private TextView nodeImageStatus;
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
        seedStarterCampaign();
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
        if (screen.startsWith("admin_")) screen = "admin";
        else if ("admin".equals(screen)) { screen = adminReturnScreen; if ("settings".equals(screen)) activeTab = TAB_SETTINGS; else if ("home".equals(screen)) activeTab = TAB_HOME; }
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
        else if ("admin_bot".equals(screen)) renderAdminBot();
        else if ("admin_players".equals(screen)) renderAdminPlayers();
        else if ("admin_map".equals(screen)) renderAdminMap();
        else if ("admin_skins".equals(screen)) renderAdminSkins();
        else if ("admin_economy".equals(screen)) renderAdminEconomy();
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

    private void seedStarterCampaign() {
        JSONObject existing = findProject(STARTER_WORLD_ID);
        if (existing != null && existing.optInt("campaignVersion", 0) >= 3) return;
        if (existing != null && worldNodes(existing).length() > 0) {
            seedOpenWorld(existing);
            seedGridWorld(existing);
            upgradeCampaignStory(existing);
            try { existing.put("campaignVersion", 3); existing.put("updatedAt", System.currentTimeMillis()); } catch (Exception ignored) { }
            saveProjects();
            return;
        }
        JSONObject world = existing == null ? new JSONObject() : existing;
        try {
            world.put("id", STARTER_WORLD_ID);
            world.put("name", "Сердце Полуночного Архипелага");
            world.put("template", "start");
            world.put("startNode", "na_intro");
            world.put("updatedAt", System.currentTimeMillis());
            JSONArray nodes = new JSONArray();

            JSONObject intro = storyNode("na_intro", "start", "Северный порт Нордхейм",
                "Полночь длится уже девять дней. За фьордом погас маяк, и ледяной туман ползёт к домам. Картограф Мира обещает найти путь к его сердцу.\n\nВ кармане — 45 серебряных марок. Решай сам, кому доверять.", "01-north-harbor.jpg");
            addStoryChoice(intro, "Найти картографа Миру", "na_mira", "", "", 0, "", "");
            addStoryChoice(intro, "Сначала зайти за припасами", "na_market", "", "", 0, "", ""); nodes.put(intro);

            JSONObject mira = storyNode("na_mira", "npc", "Мира и карта ветров",
                "Мира разворачивает карту, испещрённую серебряными линиями. «Внутри маяка спрятано Полярное Сердце. Его стережёт дракон изо льда. Вернёшь свет — Нордхейм переживёт эту ночь».\n\nОна отмечает на карте старый причал и предупреждает о волках на острове.", "02-cartographer.jpg");
            addStoryChoice(mira, "Обещать вернуть свет маяку", "na_market", "flag", "quest_lighthouse", 1, "", "");
            addStoryChoice(mira, "Спросить, что случилось с прежней командой", "na_legend", "", "", 0, "", ""); nodes.put(mira);

            JSONObject legend = storyNode("na_legend", "dialogue", "Последний сигнал",
                "«Они нашли лунный осколок у затонувшего корабля. Без него зеркало маяка не открыть. Но гарпун тоже может перерезать цепь», — шепчет Мира. За окном гудит пустой колокол.", "");
            addStoryChoice(legend, "Пора собираться", "na_market", "", "", 0, "", ""); nodes.put(legend);

            JSONObject market = storyNode("na_market", "shop", "Лавка у причала",
                "Старый Торм продаёт последние вещи перед закрытием гавани. У тебя 45 марок: купи зелья или снаряжение и оставь часть денег на дорогу.", "");
            addStoryChoice(market, "Зелье северной мяты · 12 марок", "na_wreck", "buy", "Зелье северной мяты", 12, "", "");
            addStoryChoice(market, "Сигнальный гарпун · 20 марок", "na_wreck", "buy", "Сигнальный гарпун", 20, "", "");
            addStoryChoice(market, "Не покупать ничего", "na_wreck", "", "", 0, "", ""); nodes.put(market);

            JSONObject wreck = storyNode("na_wreck", "travel", "Затонувший корабль",
                "На острове волны выбросили на лёд старое судно. Под треснувшей палубой мерцает голубой осколок. Вдалеке уже слышен вой.", "");
            addStoryChoice(wreck, "Достать лунный осколок", "na_wolf", "item", "Лунный осколок", 0, "", "");
            addStoryChoice(wreck, "Оставить осколок и идти прямо на вой", "na_wolf", "", "", 0, "", "");
            addStoryChoice(wreck, "Проверить рюкзак", "na_inventory", "", "", 0, "", ""); nodes.put(wreck);

            JSONObject inventory = storyNode("na_inventory", "inventory", "Рюкзак путешественника",
                "Всё найденное остаётся с тобой. Зелье можно выпить во время боя; осколок или гарпун помогут открыть маяк.", "");
            inventory.put("next", "na_wolf"); nodes.put(inventory);

            JSONObject wolf = storyNode("na_wolf", "battle", "Ледяной пёс",
                "Из снежной пелены выскакивает зверь с прозрачными клыками. Он преграждает дорогу к маяку.\n\nБой пошаговый: сила героя и надетое снаряжение влияют на урон. Зелье восстанавливает 8 здоровья.", "");
            wolf.put("enemyName", "Ледяной пёс"); wolf.put("enemyHp", 5); wolf.put("enemyAttack", 1);
            wolf.put("victoryTarget", "na_lighthouse"); wolf.put("defeatTarget", "na_retreat"); wolf.put("fleeTarget", "na_retreat");
            wolf.put("rewardGold", 10); wolf.put("rewardXp", 15); wolf.put("rewardItem", "Клык ледяного пса"); nodes.put(wolf);

            JSONObject lighthouse = storyNode("na_lighthouse", "quest", "Зеркальный замок",
                "Дверь маяка скована тремя цепями. На бронзовой плите вырезано: «Луна покажет путь; сталь разбудит бурю».\n\nМожно использовать осколок, гарпун или потерять 2 здоровья, ломая замок.", "");
            addStoryChoice(lighthouse, "Вставить лунный осколок", "na_bridge", "flag", "mirror_open", 1, "Лунный осколок", "na_bridge");
            addStoryChoice(lighthouse, "Перерезать цепь гарпуном", "na_bridge", "consume", "Сигнальный гарпун", 0, "Сигнальный гарпун", "");
            addStoryChoice(lighthouse, "Выломать дверь плечом · −2 здоровья", "na_bridge", "damage", "", 2, "", "na_retreat");
            addStoryChoice(lighthouse, "Вернуться к Торму за припасами", "na_market", "", "", 0, "", ""); nodes.put(lighthouse);

            JSONObject bridge = storyNode("na_bridge", "dialogue", "Лестница под небом",
                "Внутри башни линзы вращаются сами собой. Мира шепчет пароль, и на вершине раскрывается штормовое окно. За ним поднимается силуэт — огромный, как сама башня. Чтобы открыть путь к Вейру, разожги маяк, заверши звёздное поручение и достигни 6 уровня.", "");
            addStoryChoice(bridge, "Подняться навстречу дракону", "na_wyrm", "", "", 0, "", "");
            try { JSONObject finalChoice = bridge.optJSONArray("choices").optJSONObject(0); finalChoice.put("requiresLevel", 6); finalChoice.put("requiresQuest", "stars"); } catch (Exception ignored) { }
            nodes.put(bridge);

            JSONObject wyrm = storyNode("na_wyrm", "battle", "Вейр, дракон белой бури",
                "Древний дракон расправляет крылья. В центре груди горит Полярное Сердце. Его удары страшны даже закалённому герою. Экипируй найденную броню и возьми запас зелий.\n\nПобеди — и выбери, кому достанется сила сердца.", "03-ice-wyrm.jpg");
            wyrm.put("enemyName", "Вейр, дракон белой бури"); wyrm.put("enemyHp", 48); wyrm.put("enemyAttack", 8);
            wyrm.put("victoryTarget", "na_ending"); wyrm.put("defeatTarget", "na_retreat"); wyrm.put("fleeTarget", "na_retreat");
            wyrm.put("rewardGold", 100); wyrm.put("rewardXp", 350); wyrm.put("rewardItem", "Полярное Сердце"); nodes.put(wyrm);

            JSONObject ending = storyNode("na_ending", "loot", "Судьба Полярного Сердца",
                "Вейр падает, и буря стихает. Полярное Сердце пульсирует у тебя в руках, а Мира ждёт ответа: вернуть его маяку и спасти гавань — или забрать древнюю силу себе? Трофей дракона уже в рюкзаке.", "01-north-harbor.jpg");
            addStoryChoice(ending, "Вернуть Сердце маяку", "na_ending_lighthouse", "", "", 0, "", "");
            addStoryChoice(ending, "Оставить силу себе", "na_ending_power", "", "", 0, "", ""); nodes.put(ending);

            JSONObject endingLighthouse = storyNode("na_ending_lighthouse", "loot", "Хранитель маяка",
                "Ты помещаешь Сердце в линзу. Над Нордхеймом вспыхивает золотой луч, ледяной туман отступает, а рыбаки зажигают огни в честь спасителя. Мира отдаёт тебе компас: теперь твоя история начинается на рассвете.", "01-north-harbor.jpg");
            addStoryChoice(endingLighthouse, "Сыграть ещё раз", "na_intro", "reset", "", 0, "", ""); nodes.put(endingLighthouse);

            JSONObject endingPower = storyNode("na_ending_power", "loot", "Повелитель белой бури",
                "Ты прячешь Полярное Сердце под плащом. Маяк остаётся тёмным, зато ледяная буря расступается перед тобой. Мира уходит в Нордхейм одна; однажды архипелагу придётся решить, был ли ты героем или новой легендой страха.", "03-ice-wyrm.jpg");
            addStoryChoice(endingPower, "Сыграть ещё раз", "na_intro", "reset", "", 0, "", ""); nodes.put(endingPower);

            JSONObject retreat = storyNode("na_retreat", "dialogue", "Возвращение в Нордхейм",
                "Ты отступаешь к рыбацким огням. Мира перевязывает раны и говорит: «Маяк подождёт до следующей попытки». Найди припасы и попробуй другой путь.", "01-north-harbor.jpg");
            addStoryChoice(retreat, "Вернуться к лавке", "na_market", "heal", "", 3, "", "");
            addStoryChoice(retreat, "Начать историю заново", "na_intro", "reset", "", 0, "", ""); nodes.put(retreat);

            world.put("nodes", nodes);
            seedOpenWorld(world);
            seedGridWorld(world);
            world.put("campaignVersion", 3);
            if (existing == null) projects.add(world);
            saveProjects();
            if (preferences.getString(ACTIVE_WORLD_KEY, "").isEmpty()) preferences.edit().putString(ACTIVE_WORLD_KEY, STARTER_WORLD_ID).apply();
        } catch (Exception ignored) { }
    }

    private void seedOpenWorld(JSONObject world) {
        try {
            JSONArray regions = new JSONArray();
            addOpenRegion(regions, "harbor", "Нордхейм · Порт огней", "Здесь начинается северный тракт. У причала можно отдохнуть, купить припасы и поговорить с Мирой о странных огнях на островах.", 1, 0, 0, 0, 0, "", "forest,village", 0, 0);
            addOpenRegion(regions, "forest", "Шепчущий лес", "Сосны здесь растут так тесно, что даже в полдень царит сумрак. В снегу видны свежие волчьи следы, а между деревьями мерцают огоньки.", 1, 7, 2, 22, 8, "wolf_fang", "harbor,shipwreck,wolf_cliffs", 1, 0);
            addOpenRegion(regions, "village", "Забытая деревня", "Дома покосились под тяжестью снега. В кузнице ещё теплится жар, а на площади стоит колокол, который звонит без ветра.", 2, 12, 3, 32, 12, "guard_badge", "harbor,mine,watchtower", 0, 1);
            addOpenRegion(regions, "shipwreck", "Мыс разбитых кораблей", "Обломки судов застряли в ледяной бухте. Под водой блестят старые монеты, но шум прибоя привлекает утопленников.", 2, 10, 3, 36, 14, "silver_compass", "forest,crystal_cave", 2, 0);
            addOpenRegion(regions, "mine", "Шахта синего угля", "Рельсы уходят в гору, где когда-то добывали светящийся уголь. В штольнях слышны удары кирки — хотя шахта закрыта много лет.", 3, 17, 4, 48, 18, "crystal_mail", "village,crystal_cave,high_pass", 0, 2);
            addOpenRegion(regions, "crystal_cave", "Хрустальные пещеры", "Ледяные кристаллы растут здесь, как деревья. Их свет открывает древние фрески — и будит существ, спящих в стенах.", 3, 19, 4, 55, 20, "crystal_mail", "shipwreck,mine,wolf_cliffs", 3, 1);
            addOpenRegion(regions, "wolf_cliffs", "Волчьи утёсы", "Ветер свистит над обрывом. Стая делит добычу у старой сигнальной башни; на самом верху оставлена карта перевала.", 3, 20, 5, 62, 23, "wolf_pelt", "forest,crystal_cave,observatory", 2, 1);
            addOpenRegion(regions, "watchtower", "Сторожевая башня", "Башня дозорных окружена пустыми кострами. На стенах вырезаны знаки, похожие на созвездия.", 3, 21, 4, 60, 22, "guard_badge", "village,lighthouse", 1, 2);
            addOpenRegion(regions, "lighthouse", "Маяк без света", "Под лестницей башни скрыты старые механизмы. Кто-то заново зажёг сигнальные чаши, но их охраняет штормовой культ.", 4, 27, 6, 78, 30, "beacon_spear", "watchtower,high_pass", 3, 2);
            addOpenRegion(regions, "high_pass", "Перевал белых великанов", "Узкая тропа проходит между ледяными стенами. Следы ведут к пещерам великанов и к древней дороге на восток.", 4, 31, 7, 92, 34, "giant_mantle", "mine,lighthouse,observatory,storm_isle", 2, 3);
            addOpenRegion(regions, "observatory", "Обсерватория звёзд", "Медные купола вращаются сами собой. В дневнике астронома записан путь к месту, где буря держит небо в плену.", 5, 36, 8, 110, 40, "star_lens", "wolf_cliffs,high_pass,storm_isle", 4, 1);
            addOpenRegion(regions, "storm_isle", "Остров белой бури", "Молнии застыли в облаках, словно трещины на стекле. Каждая победа здесь приближает тебя к логову Вейра.", 6, 44, 9, 145, 55, "storm_ring", "high_pass,observatory,heart_peak", 4, 3);
            addOpenRegion(regions, "heart_peak", "Пик Полярного Сердца", "На вершине ждёт Вейр. Под чешуёй дракона бьётся сердце, из-за которого архипелаг забыл, что такое рассвет.", 7, 58, 11, 240, 100, "wyrm_armor", "storm_isle", 5, 4);
            world.put("regions", regions);
        } catch (Exception ignored) { }
    }

    private void seedGridWorld(JSONObject world) {
        JSONArray previous = world.optJSONArray("worldEvents");
        JSONObject existingGrid = world.optJSONObject("grid");
        if (previous != null && previous.length() >= 600 && existingGrid != null && existingGrid.optInt("width") == 100) return;
        String[] kinds = {"mob", "mob", "mob", "mob", "mob", "npc", "npc", "cache", "shrine", "mystery"};
        String[] titles = {
            "Колокольня подо льдом", "Застывший караван", "Станция картографов", "Пещера гулких рун",
            "Сломанный маяк", "Лагерь пропавшей экспедиции", "Колодец синего пламени", "Сад каменных птиц",
            "Разлом в леднике", "Забытая сигнальная башня", "Мост без реки", "Дом на краю фьорда",
            "Погребённый архив", "Костяной круг", "Лавка странника", "Часовня семи ветров",
            "Тропа белых лис", "Зеркальная заводь", "Обсерваторный осколок", "Корабль в снегу",
            "Пост дозора", "Рунический столб", "Ледяной сад", "Дорога эха",
            "Таверна без дверей", "Логово под корнями", "Снежная кузница", "Склеп мореходов",
            "Кристальный водопад", "Площадь пустых фонарей", "Покинутая почта", "Волчий тотем"
        };
        String[] epithets = {"первого следопыта", "из карты Миры", "над старой тропой", "у северного ветра", "под тремя звёздами",
            "с письмом без адреса", "из пропавшего дневника", "у чёрного камня", "после девятой метели", "за стеной льда",
            "с чужим гербом", "у последней сосны", "с огнём в стекле", "из забытого дозора", "под звон колокола",
            "с отметиной дракона", "из глубины фьорда", "на пути к маяку", "после белой бури", "без следов назад"};
        String[] openings = {"В снегу виден свежий след, ведущий к", "Слабый свет пробивается сквозь лёд у", "На полях карты Мира отмечено место —", "Из-под земли доносится тихий звон возле",
            "Над этим местом кружат белые птицы: рядом", "На камне вырезан старый знак, указывающий на", "У костра ещё тлеет уголёк; здесь недавно", "Ветер повторяет чьё-то имя рядом с",
            "Следы внезапно обрываются у", "Здесь снег не тает даже у тёплой воды; под ним", "Одинокий фонарь качается над", "Из ледяной стены торчит край письма — оно спрятано возле",
            "Компас вращается, пока не укажет на", "Из глубины пещеры отвечает эхо; источник —", "На свежем снегу выложен круг, в центре которого", "На ветке висит серебряная лента с меткой"};
        String[] discoveries = {"В старой записи сказано: «Не всякая тропа ведёт туда, куда смотришь».", "На обороте выцарапано: «Маяк помнит каждого, кто зажигал огонь».",
            "Ветер приносит голос: «Если услышишь второй колокол — не отвечай».", "В разбитом футляре лежит карта с одним пустым квадратом.",
            "Местные называют это место дверью, которую открывает только поступок.", "Кто-то оставил знак Миры: две серебряные линии и точку посередине.",
            "Из трещины доносится: «Сначала выбери, кому верить, потом — куда идти».", "На камне свежая надпись: «У каждой находки есть цена». ",
            "В старом журнале отмечено: здесь можно встретить и союзника, и добычу.", "В снегу блестит крошечный осколок Полярного Сердца.",
            "Дневник путника обрывается на фразе: «Я слышу шаги, но нас здесь трое».", "Местная легенда обещает награду тому, кто не свернёт с прямого пути.",
            "В нише оставлено предупреждение: «Сильному оружию нужна спокойная рука».", "На двери нарисованы четыре стрелки — диагонального пути нет.",
            "Среди пепла сохранилось имя человека, который однажды спас целую деревню.", "Вдалеке вспыхивает огонёк; кажется, кто-то тоже заметил тебя.",
            "На стене нарисован остров, которого нет ни на одной карте.", "Последняя строка гласит: «Возвращайся сюда, когда станешь сильнее».",
            "Сквозь лёд слышно сердцебиение — оно совпадает с ритмом далёкой бури.", "В свёртке лежит приглашение в тайное братство хранителей маяка.",
            "Старый компас указывает не на север, а на самое важное обещание.", "Под снегом найдена серебряная пуговица с гербом Нордхейма.",
            "Кто-то записал: «Самая опасная тварь — та, которую ты недооценил». ", "На камне отмечены маршруты, которыми можно пройти только по четырём сторонам света.",
            "Внутри тайника спрятано послание для следующего героя, а не для нашедшего.", "Среди обломков лежит письмо Миры, написанное почерком, которого она не знает.",
            "Снежная пыль складывается в символ Полярного Сердца.", "На морозном стекле проступает обещание: «Свет вернётся, если его разделить». ",
            "След ведёт к развилке, где каждый путь хранит отдельную историю.", "В старой песне упоминаются семьсот шагов до рассвета.",
            "В сундуке обнаружена отметка: «Возьми только то, что сможешь защитить». ", "Путешественник оставил короткую записку: «Спасибо, что пришёл». ",
            "Слабое тепло исходит от земли — здесь пробуждается ещё один фрагмент мира.", "На краю рисунка нарисован герой с плащом цвета твоего скина.",
            "Старый дозорный предупреждает: «Победа — это только начало следующей ветки». ", "Под мостом кто-то оставил вторую половину карты.",
            "Здесь найден ключ без замка; возможно, он нужен кому-то в другом конце архипелага.", "Надпись гласит: «Ищи не самую короткую дорогу, а ту, которую выберешь сам». ",
            "В ладони статуи лежит монета с датой будущего рассвета.", "На двери выжжены буквы: «Нордхейм ещё ждёт»."};
        String[] mobs = {"Снежный волк", "Утопленник с разбитого судна", "Тень деревенского дозорного", "Кристальный страж",
            "Призрак шахтёра", "Буревой ревенант", "Ледяной великан", "Звёздный призрак", "Культист белой бури", "Голодная снежная лиса",
            "Сломанный небесный дозорный", "Порождение синего угля", "Ледяной краб", "Потерянный охотник", "Страж старой тропы", "Дух маячного огня"};
        String[] loot = {"wolf_fang", "wolf_pelt", "silver_compass", "miner_pick", "crystal_mail", "guard_badge", "beacon_spear", "giant_mantle", "star_lens", "storm_ring", "wyrm_armor", "rune_blade", "crystal_armor", "star_amulet"};
        try {
            JSONObject grid = new JSONObject(); grid.put("width", 100); grid.put("height", 100); grid.put("viewport", 7); grid.put("startX", 50); grid.put("startY", 50);
            JSONArray events = new JSONArray();
            for (int i = 0; i < 640; i++) {
                int x = 3 + (i % 32) * 3; int y = 2 + (i / 32) * 5;
                String kind = kinds[i % kinds.length]; int rank = 1 + ((x * 7 + y * 11 + i / 10) % 7);
                JSONObject event = new JSONObject();
                event.put("id", String.format(Locale.US, "grid_poi_%03d", i)); event.put("x", x); event.put("y", y);
                event.put("kind", kind); event.put("rank", rank);
                event.put("title", titles[i % titles.length] + " · " + epithets[i / 32]);
                event.put("text", openings[i % openings.length] + " " + titles[i % titles.length].toLowerCase(Locale.ROOT) + ". " + discoveries[i / 16]);
                event.put("enemy", mobs[(i * 7 + rank) % mobs.length]); event.put("enemyHp", 5 + rank * 5);
                event.put("enemyAttack", 1 + rank); event.put("xp", 14 + rank * 11); event.put("gold", 5 + rank * 6);
                event.put("loot", loot[(i * 3 + rank) % loot.length]); events.put(event);
            }
            world.put("grid", grid); world.put("worldEvents", events); world.put("worldEventCount", events.length());
        } catch (Exception ignored) { }
    }

    private void upgradeCampaignStory(JSONObject world) {
        JSONArray nodes = worldNodes(world);
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i); if (node == null) continue;
            try {
                String nodeId = node.optString("id", "");
                if ("na_wyrm".equals(nodeId)) {
                    node.put("enemyHp", 48); node.put("enemyAttack", 8); node.put("rewardGold", 100); node.put("rewardXp", 350);
                    String body = node.optString("text", "").replace("Его удары наносят 2 урона; зелье сработает прямо в бою.", "Его удары страшны даже закалённому герою. Экипируй найденную броню и возьми запас зелий.");
                    node.put("text", body);
                }
                if ("na_intro".equals(nodeId)) node.put("text", node.optString("text", "").replace("28 серебряных марок", "45 серебряных марок"));
                if ("na_market".equals(nodeId)) node.put("text", node.optString("text", "").replace("У тебя 28 марок: выбери одну покупку или сохрани деньги.", "У тебя 45 марок: купи зелья или снаряжение и оставь часть денег на дорогу."));
                if ("na_wolf".equals(nodeId)) node.put("text", node.optString("text", "").replace("Удар наносит 2 урона; зелье лечит 4 здоровья.", "Бой пошаговый: сила героя и надетое снаряжение влияют на урон. Зелье восстанавливает 8 здоровья."));
                if ("na_bridge".equals(nodeId)) {
                    JSONArray choices = node.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) { JSONObject choice = choices.optJSONObject(0); if (choice != null) { choice.put("requiresLevel", 6); choice.put("requiresQuest", "stars"); } }
                    String body = node.optString("text", "");
                    if (!body.contains("6 уровня")) node.put("text", body + "\n\nЧтобы открыть путь к Вейру, разожги маяк, заверши звёздное поручение и достигни 6 уровня.");
                }
            } catch (Exception ignored) { }
        }
    }

    private void addOpenRegion(JSONArray regions, String id, String title, String text, int minLevel, int enemyHp, int enemyAttack, int xp, int gold, String loot, String exits, int x, int y) throws Exception {
        JSONObject region = new JSONObject();
        region.put("id", id); region.put("title", title); region.put("text", text);
        region.put("minLevel", minLevel); region.put("enemyHp", enemyHp); region.put("enemyAttack", enemyAttack);
        region.put("xp", xp); region.put("gold", gold); region.put("loot", loot);
        JSONArray paths = new JSONArray();
        for (String exit : exits.split(",")) if (!exit.isEmpty()) paths.put(exit);
        region.put("exits", paths); region.put("mapX", x); region.put("mapY", y);
        region.put("enemy", enemyForRegion(id));
        regions.put(region);
    }

    private String enemyForRegion(String id) {
        switch (id) {
            case "forest": return "Снежный волк"; case "village": return "Тень деревенского дозорного";
            case "shipwreck": return "Утопленник с разбитого судна"; case "mine": return "Призрак шахтёра";
            case "crystal_cave": return "Кристальный страж"; case "wolf_cliffs": return "Вожак снежной стаи";
            case "watchtower": return "Сломанный небесный дозорный"; case "lighthouse": return "Культист белой бури";
            case "high_pass": return "Ледяной великан"; case "observatory": return "Звёздный призрак";
            case "storm_isle": return "Буревой ревенант"; case "heart_peak": return "Вейр, древний дракон";
            default: return "";
        }
    }

    private JSONObject storyNode(String id, String type, String title, String text, String image) {
        JSONObject node = createNode(findTemplate(type));
        try { node.put("id", id); node.put("type", type); node.put("title", title); node.put("text", text); node.put("image", image); }
        catch (Exception ignored) { }
        return node;
    }

    private void addStoryChoice(JSONObject node, String label, String target, String effectType, String item, int amount, String requiresItem, String fallbackTarget) {
        try {
            JSONObject choice = new JSONObject();
            choice.put("label", label); choice.put("target", target);
            if (!effectType.isEmpty()) {
                JSONObject effect = new JSONObject(); effect.put("type", effectType);
                if (!item.isEmpty()) effect.put("item", item);
                if (amount != 0) effect.put(effectType.equals("buy") ? "cost" : "amount", amount);
                choice.put("effect", effect);
            }
            if (!requiresItem.isEmpty()) choice.put("requiresItem", requiresItem);
            if (!fallbackTarget.isEmpty()) choice.put("fallbackTarget", fallbackTarget);
            JSONArray choices = node.optJSONArray("choices");
            if (choices == null) { choices = new JSONArray(); node.put("choices", choices); }
            choices.put(choice);
        } catch (Exception ignored) { }
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

        LinearLayout campaign = cardColumn();
        TextView campaignBadge = text("14 СЦЕН · 13 ЛОКАЦИЙ · 2 КОНЦОВКИ", 10, colorPrimary(), true);
        campaign.addView(campaignBadge);
        TextView campaignTitle = text("Сердце Полуночного Архипелага", 18, colorText(), true);
        LinearLayout.LayoutParams campaignTitleLp = params(-1, -2); campaignTitleLp.topMargin = dp(7); campaign.addView(campaignTitle, campaignTitleLp);
        TextView campaignCopy = text("Большая карта архипелага, свободные переходы между островами, охота на мобов, уровни, квесты и добыча экипировки. Главный сюжет можно проходить в своём темпе.", 12, colorMuted(), false);
        LinearLayout.LayoutParams campaignCopyLp = params(-1, -2); campaignCopyLp.topMargin = dp(5); campaign.addView(campaignCopy, campaignCopyLp);
        TextView playCampaign = actionButton("Настроить готовую кампанию", false);
        LinearLayout.LayoutParams playLp = params(-1, 46); playLp.topMargin = dp(11); campaign.addView(playCampaign, playLp);
        playCampaign.setOnClickListener(v -> openProjectEditor(STARTER_WORLD_ID));
        LinearLayout.LayoutParams campaignLp = params(-1, -2); campaignLp.topMargin = dp(14); page.addView(campaign, campaignLp);
        addCampaignSkinStudio();

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

    private void addCampaignSkinStudio() {
        LinearLayout card = cardColumn();
        LinearLayout.LayoutParams cardLp = params(-1, -2); cardLp.topMargin = dp(12); page.addView(card, cardLp);
        card.addView(text("ГАРДЕРОБ ПЕРСОНАЖА", 10, colorPrimary(), true));
        TextView title = text("Настрой облик героя", 17, colorText(), true);
        LinearLayout.LayoutParams titleLp = params(-1, -2); titleLp.topMargin = dp(5); card.addView(title, titleLp);
        TextView hint = text("Класс новых героев — лучник. Выбери стартовый мужской или женский рисунок; одежда не меняет класс и характеристики.", 12, colorMuted(), false);
        LinearLayout.LayoutParams hintLp = params(-1, -2); hintLp.topMargin = dp(4); card.addView(hint, hintLp);
        String selected = preferences.getString(CAMPAIGN_SKIN_KEY, "archer_trail_f");
        if (!selected.startsWith("archer_trail_")) selected = "archer_trail_f";
        for (int row = 0; row < (CAMPAIGN_SKINS.length + 1) / 2; row++) {
            LinearLayout options = new LinearLayout(this); options.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = params(-1, -2); rowLp.topMargin = dp(8); card.addView(options, rowLp);
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col; if (index >= CAMPAIGN_SKINS.length) break;
                String[] skin = CAMPAIGN_SKINS[index];
                boolean active = skin[0].equals(selected);
                LinearLayout choice = new LinearLayout(this); choice.setOrientation(LinearLayout.VERTICAL); choice.setGravity(Gravity.CENTER);
                choice.setPadding(dp(3), dp(6), dp(3), dp(6));
                choice.setBackground(round(active ? (dark ? "#34304D" : "#EEEFFF") : (dark ? "#242532" : "#F5F6FA"), 12));
                ImageView portrait = campaignSkinPreview(skin[3]); choice.addView(portrait, new LinearLayout.LayoutParams(dp(96), dp(144)));
                TextView label = text(skin[2] + (active ? " ✓" : ""), 11, active ? colorPrimary() : colorText(), active);
                LinearLayout.LayoutParams labelLp = params(-1, -2); labelLp.topMargin = dp(4); choice.addView(label, labelLp); label.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams choiceLp = new LinearLayout.LayoutParams(0, -2, 1); choiceLp.setMargins(dp(3), 0, dp(3), 0); options.addView(choice, choiceLp);
                choice.setOnClickListener(v -> { preferences.edit().putString(CAMPAIGN_SKIN_KEY, skin[0]).apply(); render(); });
            }
        }
    }

    private ImageView campaignSkinPreview(String assetName) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round(dark ? "#161927" : "#DDE4F0", 12));
        image.setClipToOutline(true);
        try (InputStream input = getAssets().open("botcraft/" + assetName)) { image.setImageBitmap(BitmapFactory.decodeStream(input)); }
        catch (Exception ignored) { }
        return image;
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
        admin.setOnClickListener(v -> { adminReturnScreen = "editor"; screen = "admin"; render(); });
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
            ImageView sceneImage = nodeImageView(node, dp(180));
            if (sceneImage != null) { LinearLayout.LayoutParams imageLp = params(-1, dp(180)); imageLp.topMargin = dp(10); card.addView(sceneImage, imageLp); }
            else if (!node.optString("image", "").isEmpty()) { TextView missingImage = text("▧  Изображение узла недоступно", 12, colorMuted(), false); LinearLayout.LayoutParams missingLp = params(-1, -2); missingLp.topMargin = dp(8); card.addView(missingImage, missingLp); }
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
            remove.setOnClickListener(v -> confirmNodeDelete(project, nodeId));
            LinearLayout.LayoutParams cardLp = params(-1, -2); cardLp.topMargin = dp(10); page.addView(card, cardLp);
            String nextId = node.optString("next", "");
            if (!nextId.isEmpty()) {
                TextView arrow = text("↓  ДАЛЕЕ: " + nodeTitle(project, nextId), 10, colorMuted(), true);
                arrow.setGravity(Gravity.CENTER);
                page.addView(arrow, params(-1, 28));
            }
        }
        TextView botSetup = actionButton("Открыть панель администратора", true);
        LinearLayout.LayoutParams xp = params(-1, 50); xp.topMargin = dp(18); page.addView(botSetup, xp);
        botSetup.setOnClickListener(v -> { adminReturnScreen = "editor"; screen = "admin"; render(); });
        TextView backendNote = text("Управление миром остаётся в APK. Чтобы игроки запускали Mini App через Telegram, нужен публичный HTTPS-адрес игры и обработчик событий бота; токен сам по себе не публикует данные с телефона.", 12, colorMuted(), false);
        LinearLayout.LayoutParams bn = params(-1, -2); bn.topMargin = dp(10); page.addView(backendNote, bn);
    }

    private String nodeTitle(JSONObject project, String id) {
        JSONObject node = findNode(project, id);
        return node == null ? "не подключён" : node.optString("title", "Узел");
    }

    private void showNodeLibrary() {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        TextView intro = text("Выбери блок, который добавит новый шаг или механику в сценарий.", 13, colorMuted(), false);
        LinearLayout.LayoutParams introLp = params(-1, -2); introLp.bottomMargin = dp(10); content.addView(intro, introLp);
        for (Template template : TEMPLATES) {
            LinearLayout row = cardColumn(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text(template.emoji, 22, colorPrimary(), true); icon.setGravity(Gravity.CENTER); icon.setBackground(round(dark ? "#30314C" : "#F0EFFF", 12));
            row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1); labelsLp.setMargins(dp(10), 0, 0, 0);
            labels.addView(text(template.title, 15, colorText(), true));
            TextView description = text(template.description, 12, colorMuted(), false); LinearLayout.LayoutParams descLp = params(-1, -2); descLp.topMargin = dp(3); labels.addView(description, descLp);
            row.addView(labels, labelsLp);
            LinearLayout.LayoutParams rowLp = params(-1, -2); rowLp.bottomMargin = dp(8); content.addView(row, rowLp);
            row.setOnClickListener(v -> { dialog.dismiss(); addWorldNode(template); });
        }
        showStudioDialog(dialog, "Добавить игровой блок", content, "Готово", dialog::dismiss, null, null);
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
        selectedNodeId = nodeId;
        originalNodeImage = node.optString("image", "");
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(fieldLabel("Заголовок"));
        EditText title = editText("Название сцены"); title.setText(node.optString("title", "")); fields.addView(title, params(-1, 50));
        fields.addView(fieldLabel("Текст сцены"));
        EditText body = editText("Описание, диалог или результат"); body.setSingleLine(false); body.setMinLines(3); body.setGravity(Gravity.TOP); body.setText(node.optString("text", "")); LinearLayout.LayoutParams bp = params(-1, 108); fields.addView(body, bp);
        fields.addView(fieldLabel("Иллюстрация"));
        LinearLayout imageCard = cardColumn(); imageCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        nodeImagePreview = nodeImageView(node, dp(190));
        String imagePath = node.optString("image", "");
        nodeImageStatus = text(imagePath.isEmpty() ? "Изображение не выбрано" : nodeImagePreview == null ? "Изображение указано, но не удалось открыть файл" : "Текущее изображение сцены", 12, colorMuted(), false);
        imageCard.addView(nodeImageStatus);
        if (nodeImagePreview != null) { LinearLayout.LayoutParams previewLp = params(-1, dp(190)); previewLp.topMargin = dp(8); imageCard.addView(nodeImagePreview, previewLp); }
        LinearLayout imageActions = new LinearLayout(this); imageActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView replaceImage = actionButton("Выбрать / заменить", true); TextView clearImage = actionButton("Убрать", false);
        LinearLayout.LayoutParams replaceLp = new LinearLayout.LayoutParams(0, dp(44), 1); replaceLp.topMargin = dp(9); imageActions.addView(replaceImage, replaceLp);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(44), 1); clearLp.setMargins(dp(7), dp(9), 0, 0); imageActions.addView(clearImage, clearLp);
        imageCard.addView(imageActions); fields.addView(imageCard, params(-1, -2));
        replaceImage.setOnClickListener(v -> openNodeImagePicker());
        clearImage.setOnClickListener(v -> { try { node.put("image", ""); } catch (Exception ignored) { } refreshNodeImageEditor(node); });
        fields.addView(fieldLabel("Варианты ответа · по одному в строке"));
        EditText options = editText("Например: Поговорить\nУйти"); options.setSingleLine(false); options.setMinLines(2); options.setGravity(Gravity.TOP);
        JSONArray oldChoices = node.optJSONArray("choices"); StringBuilder optionText = new StringBuilder();
        if (oldChoices != null) for (int i = 0; i < oldChoices.length(); i++) { JSONObject ch = oldChoices.optJSONObject(i); if (ch != null) { if (optionText.length() > 0) optionText.append('\n'); optionText.append(ch.optString("label", "")); } }
        options.setText(optionText.toString()); LinearLayout.LayoutParams op = params(-1, 84); fields.addView(options, op);
        fields.addView(fieldLabel("Условие для этого узла"));
        EditText rule = editText("gold>=50, xp>=20, has:Зелье"); rule.setText(node.optString("condition", "")); fields.addView(rule, params(-1, 50));
        Dialog dialog = new Dialog(this);
        showStudioDialog(dialog, "Редактировать узел", fields, "Сохранить", () -> {
            try {
                node.put("title", title.getText().toString().trim().isEmpty() ? "Без названия" : title.getText().toString().trim());
                node.put("text", body.getText().toString().trim());
                JSONArray choices = new JSONArray(); JSONArray previousChoices = node.optJSONArray("choices");
                for (String line : options.getText().toString().split("\\n")) {
                    String label = line.trim(); if (label.isEmpty()) continue;
                    int index = choices.length(); JSONObject ch = new JSONObject(); ch.put("label", label);
                    JSONObject previous = previousChoices == null ? null : previousChoices.optJSONObject(index);
                    ch.put("target", previous == null ? "" : previous.optString("target", ""));
                    if (previous != null) for (String property : new String[] {"effect", "requiresItem", "fallbackTarget"}) if (previous.has(property)) ch.put(property, previous.get(property));
                    choices.put(ch);
                }
                node.put("choices", choices); node.put("condition", rule.getText().toString().trim()); touchProject(project);
            } catch (Exception ignored) { }
            dialog.dismiss(); selectedNodeId = ""; render();
        }, "Отмена", () -> { try { node.put("image", originalNodeImage); } catch (Exception ignored) { } dialog.dismiss(); selectedNodeId = ""; });
        dialog.setOnCancelListener(d -> { try { node.put("image", originalNodeImage); } catch (Exception ignored) { } selectedNodeId = ""; });
    }

    private TextView fieldLabel(String label) {
        TextView view = text(label, 12, colorPrimary(), true);
        LinearLayout.LayoutParams lp = params(-1, -2); lp.topMargin = dp(12); lp.bottomMargin = dp(5); view.setLayoutParams(lp); return view;
    }

    private void openNodeImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, NODE_IMAGE_REQUEST); }
        catch (Exception e) { Toast.makeText(this, "Не удалось открыть выбор изображения", Toast.LENGTH_SHORT).show(); }
    }

    private void refreshNodeImageEditor(JSONObject node) {
        if (nodeImagePreview != null) {
            ViewGroup parent = (ViewGroup) nodeImagePreview.getParent(); if (parent != null) parent.removeView(nodeImagePreview);
            nodeImagePreview = null;
        }
        if (nodeImageStatus != null) nodeImageStatus.setText(node.optString("image", "").isEmpty() ? "Изображение не выбрано" : "Изображение сцены обновлено");
        ImageView preview = nodeImageView(node, dp(190));
        if (preview != null && nodeImageStatus != null && nodeImageStatus.getParent() instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) nodeImageStatus.getParent();
            parent.addView(preview, 1, params(-1, dp(190))); nodeImagePreview = preview;
        }
    }

    private ImageView nodeImageView(JSONObject node, int height) {
        String image = node == null ? "" : node.optString("image", "");
        if (image.isEmpty()) return null;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            if (image.startsWith("node-images/")) BitmapFactory.decodeFile(new File(getFilesDir(), image).getAbsolutePath(), bounds);
            else try (InputStream input = getAssets().open("botcraft/" + image)) { BitmapFactory.decodeStream(input, null, bounds); }
            int target = Math.max(dp(220), height);
            int sample = 1; while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
            if (image.startsWith("node-images/")) bitmap = BitmapFactory.decodeFile(new File(getFilesDir(), image).getAbsolutePath(), options);
            else try (InputStream input = getAssets().open("botcraft/" + image)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        } catch (Exception ignored) { }
        if (bitmap == null) return null;
        ImageView view = new ImageView(this); view.setScaleType(ImageView.ScaleType.CENTER_CROP); view.setImageBitmap(bitmap);
        view.setBackground(round(dark ? "#202438" : "#E8ECF5", 14)); view.setClipToOutline(true);
        return view;
    }

    private void showStudioDialog(Dialog dialog, String title, View content, String primary, Runnable onPrimary, String secondary, Runnable onSecondary) {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        panel.setBackground(round(dark ? "#191D2B" : "#FFFFFF", 24));
        TextView heading = text(title, 20, colorText(), true); LinearLayout.LayoutParams headingLp = params(-1, -2); headingLp.bottomMargin = dp(12); panel.addView(heading, headingLp);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false); scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        if (secondary != null) {
            TextView secondaryButton = actionButton(secondary, false); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(48), 1); slp.setMargins(0, dp(12), dp(7), 0); buttons.addView(secondaryButton, slp);
            secondaryButton.setOnClickListener(v -> { if (onSecondary != null) onSecondary.run(); });
        }
        TextView primaryButton = actionButton(primary, true); LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(48), 1); plp.topMargin = dp(12); if (secondary != null) plp.setMargins(dp(7), dp(12), 0, 0); buttons.addView(primaryButton, plp);
        primaryButton.setOnClickListener(v -> { if (onPrimary != null) onPrimary.run(); });
        panel.addView(buttons);
        dialog.setContentView(panel);
        Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawableResource(android.R.color.transparent); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams attrs = window.getAttributes(); attrs.dimAmount = 0.58f; window.setAttributes(attrs); }
        dialog.show();
        if (window != null) window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520)), Math.min(getResources().getDisplayMetrics().heightPixels - dp(100), dp(760)));
    }

    private void confirmNodeDelete(JSONObject project, String nodeId) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text("Связи на этот узел станут пустыми. Это действие нельзя отменить.", 14, colorMuted(), false));
        Dialog dialog = new Dialog(this);
        showStudioDialog(dialog, "Удалить узел?", content, "Удалить", () -> { deleteNode(project, nodeId); dialog.dismiss(); render(); }, "Оставить", dialog::dismiss);
    }

    private void chooseChoiceTarget(JSONObject project, String sourceId, int choiceIndex) {
        JSONObject source = findNode(project, sourceId); JSONArray nodes = worldNodes(project);
        JSONArray choices = source == null ? null : source.optJSONArray("choices");
        if (choices == null || choiceIndex < 0 || choiceIndex >= choices.length()) return;
        JSONObject choice = choices.optJSONObject(choiceIndex); if (choice == null) return;
        showTargetPicker("Переход для варианта", "Выбери сцену, которая откроется после этого ответа.", project, sourceId, choice.optString("target", ""), true, target -> {
            try { choice.put("target", target); touchProject(project); } catch (Exception ignored) { }
            render();
        });
    }

    private interface TargetSelected { void onSelected(String target); }

    private void chooseNodeTarget(JSONObject project, String sourceId) {
        JSONObject source = findNode(project, sourceId); JSONArray nodes = worldNodes(project);
        if (source == null || nodes.length() < 2) { Toast.makeText(this, "Сначала добавь ещё один узел", Toast.LENGTH_SHORT).show(); return; }
        showTargetPicker("Следующий узел", "Свяжи эту сцену со следующим шагом сценария.", project, sourceId, source.optString("next", ""), false, target -> {
            try { source.put("next", target); touchProject(project); } catch (Exception ignored) { }
            render();
        });
    }

    private void showTargetPicker(String title, String description, JSONObject project, String sourceId, String selectedTarget, boolean allowEnd, TargetSelected callback) {
        JSONArray nodes = worldNodes(project); Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        TextView copy = text(description, 13, colorMuted(), false); LinearLayout.LayoutParams copyLp = params(-1, -2); copyLp.bottomMargin = dp(10); content.addView(copy, copyLp);
        if (allowEnd) addTargetOption(content, dialog, "Завершить ветку", "Сценарий заканчивается на этом выборе", "", selectedTarget, callback);
        else addTargetOption(content, dialog, "Без перехода", "Оставить сцену без следующего шага", "", selectedTarget, callback);
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i); if (node == null) continue;
            String id = node.optString("id", ""); if (id.equals(sourceId)) continue;
            Template type = findTemplate(node.optString("type", "dialogue"));
            addTargetOption(content, dialog, type.emoji + "  " + node.optString("title", type.title), type.title + " · " + id.substring(0, Math.min(6, id.length())), id, selectedTarget, callback);
        }
        showStudioDialog(dialog, title, content, "Закрыть", dialog::dismiss, null, null);
    }

    private void addTargetOption(LinearLayout list, Dialog dialog, String title, String subtitle, String target, String selected, TargetSelected callback) {
        LinearLayout row = cardColumn(); row.setOrientation(LinearLayout.VERTICAL);
        row.addView(text((target.equals(selected) ? "✓  " : "") + title, 15, target.equals(selected) ? colorPrimary() : colorText(), true));
        TextView detail = text(subtitle, 11, colorMuted(), false); LinearLayout.LayoutParams dp = params(-1, -2); dp.topMargin = dp(3); row.addView(detail, dp);
        LinearLayout.LayoutParams rp = params(-1, -2); rp.bottomMargin = dp(8); list.addView(row, rp);
        row.setOnClickListener(v -> { dialog.dismiss(); callback.onSelected(target); });
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
        JSONObject project = findProject(editingId); if (project == null) { screen = adminReturnScreen; return; }
        JSONArray nodes = worldNodes(project);
        JSONObject grid = project.optJSONObject("grid"); JSONArray events = project.optJSONArray("worldEvents");
        addBrandHeader("Панель администратора", "УПРАВЛЕНИЕ МИРОМ"); addSpace(12);
        page.addView(text(project.optString("name", "Без названия"), 23, colorText(), true));
        TextView summary = text("Ваш мир · " + nodes.length() + " сюжетных узлов · " + (events == null ? 0 : events.length()) + " клеточных точек", 13, colorMuted(), false);
        LinearLayout.LayoutParams summaryLp=params(-1,-2); summaryLp.topMargin=dp(5); page.addView(summary,summaryLp);
        addSectionTitle("Разделы управления", "Выбери, что настроить");
        addAdminMenuCard("🤖", "Telegram-бот", "Подключение, запуск polling и диагностика", () -> { adminReturnScreen="admin"; screen="admin_bot"; render(); });
        addAdminMenuCard("🗺️", "Мир и карта", "Название, карта 100 × 100, события и экспорт", () -> { screen="admin_map"; render(); });
        addAdminMenuCard("👥", "Игроки", "Игроки бота на этом устройстве, прогресс и сброс", () -> { screen="admin_players"; render(); });
        addAdminMenuCard("🏹", "Облики и награды", "Доступные образы лучника и игровые награды", () -> { screen="admin_skins"; render(); });
        addAdminMenuCard("🪙", "Экономика", "Баланс и техническая готовность платежей", () -> { screen="admin_economy"; render(); });
        TextView editor = actionButton("Вернуться в конструктор мира", false); LinearLayout.LayoutParams ep=params(-1,46); ep.topMargin=dp(12); page.addView(editor,ep);
        editor.setOnClickListener(v -> { screen=adminReturnScreen.equals("admin") ? "editor" : adminReturnScreen; render(); });
    }

    private void addAdminMenuCard(String emoji, String title, String description, Runnable onClick) {
        LinearLayout card=cardColumn(); LinearLayout.LayoutParams cp=params(-1,-2); cp.topMargin=dp(9); page.addView(card,cp);
        TextView heading=text(emoji+"   "+title,17,colorText(),true); card.addView(heading);
        TextView detail=text(description,12,colorMuted(),false); LinearLayout.LayoutParams dp1=params(-1,-2); dp1.topMargin=dp(4); card.addView(detail,dp1);
        TextView open=actionButton("Открыть раздел  ›",false); LinearLayout.LayoutParams op=params(-1,40); op.topMargin=dp(9); card.addView(open,op);
        card.setOnClickListener(v -> onClick.run()); open.setOnClickListener(v -> onClick.run()); card.setFocusable(true);
    }

    private void adminBack(String title, String subtitle) {
        addBrandHeader(title, subtitle); TextView back=actionButton("← Панель администратора",false);
        LinearLayout.LayoutParams lp=params(-1,42); lp.topMargin=dp(8); page.addView(back,lp);
        back.setOnClickListener(v -> { screen="admin"; render(); });
    }

    private void renderAdminBot() {
        addBrandHeader("Telegram-бот", "ПОДКЛЮЧЕНИЕ И ДИАГНОСТИКА");
        TextView back=actionButton("← Панель администратора",false); LinearLayout.LayoutParams bp=params(-1,42); bp.topMargin=dp(8); page.addView(back,bp);
        back.setOnClickListener(v->{screen="admin";render();}); addBotConnectionCard();
        LinearLayout info=cardColumn(); LinearLayout.LayoutParams ip=params(-1,-2); ip.topMargin=dp(10); page.addView(info,ip);
        info.addView(text("Состояние хранилища",15,colorText(),true));
        info.addView(text("Токен шифруется Android Keystore на этом устройстве. Long polling работает, пока Android разрешает фоновую работу и есть сеть; закрытый APK не заменяет публичный сервер Mini App.",12,colorMuted(),false));
    }

    private void renderAdminPlayers() {
        adminBack("Игроки", "ЛОКАЛЬНЫЕ ДАННЫЕ БОТА"); addSpace(10);
        JSONObject all=readPlayerStates(); Iterator<String> ids=all.keys(); int count=all.length();
        addSectionTitle("Сохранения на этом устройстве", count+" игроков · не синхронизируются между телефонами");
        if(count==0) {
            LinearLayout empty=cardColumn(); empty.addView(text("Пока нет игроков",16,colorText(),true));
            empty.addView(text("Игроки появятся здесь после запуска бота и первого сообщения ему. Данные хранятся только на телефоне, где работает APK.",12,colorMuted(),false)); page.addView(empty);
        }
        while(ids.hasNext()) {
            String id=ids.next(); JSONObject player=all.optJSONObject(id); if(player==null) continue;
            LinearLayout card=cardColumn(); LinearLayout.LayoutParams cp=params(-1,-2); cp.topMargin=dp(8); page.addView(card,cp);
            card.addView(text(player.optString("firstName", player.optString("username", "Игрок"))+" · Telegram ID "+id,14,colorText(),true));
            card.addView(text("Уровень "+player.optInt("level",1)+" · ❤️ "+player.optInt("hp",0)+" · "+adminWalletLabel(player)+" · "+player.optString("currentRegion","—"),12,colorMuted(),false));
            TextView manage=actionButton("Управлять персонажем · уровень и валюта",true); LinearLayout.LayoutParams mp=params(-1,44); mp.topMargin=dp(8); card.addView(manage,mp);
            manage.setOnClickListener(v -> showAdminPlayerDialog(id));
            TextView reset=actionButton("Сбросить прогресс игрока",false); LinearLayout.LayoutParams rp=params(-1,42); rp.topMargin=dp(6); card.addView(reset,rp);
            reset.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Сбросить прогресс?").setMessage("Данные игрока "+id+" будут удалены с этого устройства.").setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(d,w)->{ JSONObject latest=readPlayerStates(); latest.remove(id); writePlayerStates(latest); render(); }).show());
        }
        if(count>0) { TextView clear=actionButton("Удалить сохранения всех игроков",false); LinearLayout.LayoutParams cp=params(-1,46); cp.topMargin=dp(12); page.addView(clear,cp);
            clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Удалить все сохранения?").setMessage("Это действие удалит локальный прогресс всех игроков на этом телефоне.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{writePlayerStates(new JSONObject());render();}).show()); }
        TextView warning=text("Здесь можно менять уровень и баланс игроков, чьи сохранения есть на этом устройстве. Для управления игроками на чужих устройствах понадобится серверная база и проверка прав администратора.",12,colorMuted(),false); LinearLayout.LayoutParams wp=params(-1,-2); wp.topMargin=dp(14); page.addView(warning,wp);
    }

    private String adminWalletLabel(JSONObject player) {
        normalizeAdminWallet(player);
        return "🪙 "+player.optInt("gold")+" · 🥈 "+player.optInt("silver")+" · 🟤 "+player.optInt("bronze");
    }

    private void showAdminPlayerDialog(String playerId) {
        JSONObject player=readPlayerStates().optJSONObject(playerId); if(player==null)return;
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(8),dp(20),dp(4));
        TextView current=text("Сейчас: уровень "+player.optInt("level",1)+" · "+adminWalletLabel(player),13,colorMuted(),false); form.addView(current);
        EditText levels=adminDeltaField("Изменение уровня", "0"); form.addView(levels);
        EditText experience=adminDeltaField("Добавить / убрать опыт", "0"); form.addView(experience);
        addFormCaption(form,"Баланс: можно ввести отрицательное число для снятия валюты");
        EditText gold=adminDeltaField("Золото Δ", "0"); form.addView(gold);
        EditText silver=adminDeltaField("Серебро Δ", "0"); form.addView(silver);
        EditText bronze=adminDeltaField("Бронза Δ", "0"); form.addView(bronze);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Персонаж · "+playerId).setView(scroll)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Добавить уровень +1",(d,w)->{JSONObject latest=readPlayerStates().optJSONObject(playerId);if(latest!=null){applyAdminLevelDelta(latest,1);saveAdminPlayer(playerId,latest);}})
            .setPositiveButton("Применить изменения",(d,w)->{
                JSONObject latest=readPlayerStates().optJSONObject(playerId);if(latest==null)return;
                applyAdminLevelDelta(latest,parseAdminDelta(levels)); applyAdminExperience(latest,parseAdminDelta(experience));
                addAdminWallet(latest,"gold",parseAdminDelta(gold));addAdminWallet(latest,"silver",parseAdminDelta(silver));addAdminWallet(latest,"bronze",parseAdminDelta(bronze));
                saveAdminPlayer(playerId,latest);
            }).show();
    }

    private EditText adminDeltaField(String label,String initial) {
        EditText field=editText(label);field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        field.setText(initial);LinearLayout.LayoutParams lp=params(-1,48);lp.topMargin=dp(6);field.setLayoutParams(lp);return field;
    }
    private void addFormCaption(LinearLayout form,String label) { TextView t=text(label,12,colorMuted(),false);LinearLayout.LayoutParams lp=params(-1,-2);lp.topMargin=dp(10);form.addView(t,lp); }
    private int parseAdminDelta(EditText field) { try{return Math.max(-1000000,Math.min(1000000,Integer.parseInt(field.getText().toString().trim())));}catch(Exception e){return 0;} }
    private void applyAdminLevelDelta(JSONObject player,int delta) {
        int before=player.optInt("level",1),after=Math.max(1,Math.min(1000,before+delta));
        try{player.put("level",after);int hpCap=Math.max(16,player.optInt("maxHp",16)+(after-before)*3);player.put("maxHp",hpCap);player.put("hp",after>before?hpCap:Math.min(player.optInt("hp",hpCap),hpCap));}catch(Exception ignored){}
    }
    private void applyAdminExperience(JSONObject player,int delta) {
        long xp=Math.max(0L,(long)player.optInt("xp",0)+delta);int level=player.optInt("level",1),gained=0;
        while(level<20&&xp>=level*70L){xp-=level*70L;level++;gained++;}
        try{player.put("xp",(int)Math.min(Integer.MAX_VALUE,xp));if(gained>0){player.put("level",level);int hp=player.optInt("maxHp",16)+gained*3;player.put("maxHp",hp);player.put("hp",hp);}}catch(Exception ignored){}
    }
    private void addAdminWallet(JSONObject player,String denomination,int delta) {
        normalizeAdminWallet(player);long total=(long)player.optInt("gold")*100L+(long)player.optInt("silver")*10L+player.optInt("bronze");
        long scale="gold".equals(denomination)?100L:"silver".equals(denomination)?10L:1L;
        long max=(long)Integer.MAX_VALUE*100L+99L;total=Math.max(0L,Math.min(max,total+(long)delta*scale));
        try{player.put("gold",(int)(total/100));player.put("silver",(int)((total%100)/10));player.put("bronze",(int)(total%10));}catch(Exception ignored){}
    }
    private void normalizeAdminWallet(JSONObject player) {
        long total=Math.max(0L,(long)player.optInt("gold",0)*100L+(long)player.optInt("silver",0)*10L+player.optInt("bronze",0));
        long max=(long)Integer.MAX_VALUE*100L+99L;total=Math.min(total,max);
        try{player.put("gold",(int)(total/100));player.put("silver",(int)((total%100)/10));player.put("bronze",(int)(total%10));}catch(Exception ignored){}
    }
    private void saveAdminPlayer(String id,JSONObject player) {
        normalizeAdminWallet(player);JSONObject all=readPlayerStates();
        try { all.put(id,player); } catch(Exception ignored) { Toast.makeText(this,"Не удалось сохранить персонажа",Toast.LENGTH_SHORT).show(); return; }
        writePlayerStates(all);Toast.makeText(this,"Изменения персонажа сохранены",Toast.LENGTH_SHORT).show();render();
    }

    private JSONObject readPlayerStates() { try { return new JSONObject(preferences.getString("telegram_player_states","{}")); } catch(Exception e) { return new JSONObject(); } }
    private void writePlayerStates(JSONObject players) { preferences.edit().putString("telegram_player_states",players.toString()).apply(); }

    private void renderAdminMap() {
        adminBack("Мир и карта", "НАСТРОЙКИ КАМПАНИИ"); JSONObject project=findProject(editingId); if(project==null)return;
        addSectionTitle("Общие параметры", "Сохраняются в выбранном локальном проекте");
        LinearLayout card=cardColumn(); page.addView(card); EditText name=editText("Название мира"); name.setText(project.optString("name","")); card.addView(name,params(-1,50));
        card.addView(text("Карта игры отображается как графическая область 7 × 7 вокруг героя; перемещение выполняется стрелками. Полный мир содержит 100 × 100 координат.",12,colorMuted(),false));
        JSONArray events=project.optJSONArray("worldEvents"); JSONArray nodes=worldNodes(project);
        LinearLayout stats=cardColumn(); LinearLayout.LayoutParams sp=params(-1,-2); sp.topMargin=dp(10); page.addView(stats,sp);
        stats.addView(text("Сюжетных узлов: "+nodes.length(),14,colorText(),true)); stats.addView(text("Клеточных точек интереса: "+(events==null?0:events.length()),14,colorText(),true)); stats.addView(text("Клеточная сетка: "+(project.optJSONObject("grid") == null ? "не настроена" : "готова"),14,colorText(),true));
        TextView save=actionButton("Сохранить название",true); LinearLayout.LayoutParams sl=params(-1,46); sl.topMargin=dp(10); page.addView(save,sl);
        save.setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Укажи название");return;}try{project.put("name",value);}catch(Exception ignored){}touchProject(project);Toast.makeText(this,"Название мира сохранено",Toast.LENGTH_SHORT).show();render();});
        TextView export=actionButton("Поделиться резервной копией мира",false); LinearLayout.LayoutParams el=params(-1,46); el.topMargin=dp(8); page.addView(export,el);
        export.setOnClickListener(v->{Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/json");send.putExtra(Intent.EXTRA_SUBJECT,"Резервная копия · "+project.optString("name"));send.putExtra(Intent.EXTRA_TEXT,project.toString());startActivity(Intent.createChooser(send,"Экспортировать мир"));});
        TextView warning=text("Экспорт передаст сценарий выбранному приложению через системное меню Android. Секретный токен бота в файл мира не включается.",12,colorMuted(),false); LinearLayout.LayoutParams wl=params(-1,-2);wl.topMargin=dp(10);page.addView(warning,wl);
    }

    private void renderAdminSkins() {
        adminBack("Облики и награды", "КОЛЛЕКЦИЯ ЛУЧНИКА"); addSpace(10);
        String[][] skins={{"archer-free-female.png","Странница · женский","Стартовый облик"},{"archer-free-male.jpg","Следопыт · мужской","Стартовый облик"},{"archer-frost-female.png","Иней · женский","Сюжетное открытие"},{"archer-frost-male.jpg","Иней · мужской","Сюжетное открытие"},{"archer-gold-female.jpg","Золотой рассвет · женский","Игровая валюта"},{"archer-gold-male.png","Золотой рассвет · мужской","Игровая валюта"},{"archer-starfall-female.gif","Звездопад · женский","Telegram Stars · GIF"},{"archer-starfall-male.gif","Звездопад · мужской","Telegram Stars · GIF"}};
        addSectionTitle("Доступные образы", "Нажми на карточку, чтобы открыть крупный предпросмотр");
        for(String[] skin:skins){
            LinearLayout card=cardColumn();LinearLayout.LayoutParams cp=params(-1,-2);cp.topMargin=dp(7);page.addView(card,cp);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);
            ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);Bitmap art=loadSkinArt(skin[0],dp(66),dp(90));if(art!=null)preview.setImageBitmap(art);
            row.addView(preview,new LinearLayout.LayoutParams(dp(66),dp(90)));
            LinearLayout detail=new LinearLayout(this);detail.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1);dl.setMargins(dp(12),0,0,0);row.addView(detail,dl);
            detail.addView(text("🏹  "+skin[1],14,colorText(),true));detail.addView(text(skin[2]+" · "+skin[0],11,colorMuted(),false));
            TextView open=actionButton("Показать облик  ›",false);LinearLayout.LayoutParams op=params(-1,38);op.topMargin=dp(7);card.addView(open,op);
            card.setOnClickListener(v->showSkinPreview(skin[0],skin[1],skin[2]));open.setOnClickListener(v->showSkinPreview(skin[0],skin[1],skin[2]));
        }
        TextView note=text("Сюжетные открытия и локальный прогресс доступны в этом прототипе. Реальное выставление цен/покупок и Telegram Stars требует серверной валидации платежей.",12,colorMuted(),false);LinearLayout.LayoutParams np=params(-1,-2);np.topMargin=dp(12);page.addView(note,np);
    }

    private Bitmap loadSkinArt(String filename,int width,int height) {
        try(InputStream input=getAssets().open("botcraft/"+filename)) {
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeStream(input,null,options);
            int sample=1;while(options.outWidth/sample>Math.max(1,width*2)||options.outHeight/sample>Math.max(1,height*2))sample*=2;
            try(InputStream scaled=getAssets().open("botcraft/"+filename)){BitmapFactory.Options actual=new BitmapFactory.Options();actual.inSampleSize=sample;actual.inPreferredConfig=Bitmap.Config.ARGB_8888;return BitmapFactory.decodeStream(scaled,null,actual);}
        }catch(Exception ignored){return null;}
    }

    private void showSkinPreview(String filename,String title,String detail) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(14),dp(16),dp(14));
        panel.setBackground(round(colorCard(),24,colorLine(),1));

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(title,19,colorText(),true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);header.addView(heading,hp);
        TextView closeTop=actionButton("✕",false);LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(dp(44),dp(42));closeLp.leftMargin=dp(8);header.addView(closeTop,closeLp);
        panel.addView(header);
        TextView sub=text(detail+"  ·  "+(filename.toLowerCase(Locale.ROOT).endsWith(".gif")?"GIF · анимация":"ПРЕДПРОСМОТР"),11,colorMuted(),true);
        LinearLayout.LayoutParams subLp=params(-1,-2);subLp.topMargin=dp(3);panel.addView(sub,subLp);

        FrameLayout viewport=new FrameLayout(this);viewport.setBackground(round(colorSurface(),18,colorLine(),1));
        viewport.setClipToOutline(true);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,0,1);vp.topMargin=dp(14);vp.bottomMargin=dp(12);panel.addView(viewport,vp);
        boolean gif=filename.toLowerCase(Locale.ROOT).endsWith(".gif");
        if(gif) {
            GifPreviewView animation=new GifPreviewView(this,filename);
            viewport.addView(animation,new FrameLayout.LayoutParams(-1,-1));
            TextView badge=text("●  GIF",11,Color.WHITE,true);badge.setPadding(dp(10),dp(6),dp(10),dp(6));badge.setBackground(round(dark?"#B936A98F":"#B021796D",999));
            FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);badgeLp.setMargins(0,dp(10),dp(10),0);viewport.addView(badge,badgeLp);
        } else {
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap art=loadSkinArt(filename,dp(340),dp(560));if(art!=null)image.setImageBitmap(art);
            viewport.addView(image,new FrameLayout.LayoutParams(-1,-1));
        }
        TextView copy=text("Облик меняет только внешний вид, не класс и характеристики.",12,colorMuted(),false);
        LinearLayout.LayoutParams cp=params(-1,-2);cp.bottomMargin=dp(10);panel.addView(copy,cp);
        TextView close=actionButton("Закрыть просмотр",true);panel.addView(close,params(-1,46));
        dialog.setContentView(panel);dialog.setCancelable(true);
        closeTop.setOnClickListener(v->dialog.dismiss());close.setOnClickListener(v->dialog.dismiss());
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.72f;window.setAttributes(attrs);}
        dialog.show();
        if(window!=null){int width=getResources().getDisplayMetrics().widthPixels;int height=getResources().getDisplayMetrics().heightPixels;window.setLayout((int)(width*.92f),(int)(height*.84f));}
    }

    private final class GifPreviewView extends View {
        private Movie movie;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private long startedAt;
        GifPreviewView(Context context,String filename) {
            super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            try(InputStream input=getAssets().open("botcraft/"+filename)){movie=Movie.decodeStream(input);}catch(Exception ignored){movie=null;}
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();startedAt=SystemClock.uptimeMillis();invalidate();}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            if(movie==null){paint.setColor(colorMuted());paint.setTextSize(dp(14));canvas.drawText("Не удалось загрузить GIF",dp(20),getHeight()/2f,paint);return;}
            int duration=movie.duration();if(duration<=0)duration=1000;
            if(startedAt==0)startedAt=SystemClock.uptimeMillis();movie.setTime((int)((SystemClock.uptimeMillis()-startedAt)%duration));
            float scale=Math.min((float)getWidth()/Math.max(1,movie.width()),(float)getHeight()/Math.max(1,movie.height()));
            float drawW=movie.width()*scale,drawH=movie.height()*scale;int save=canvas.save();
            canvas.translate((getWidth()-drawW)/2f,(getHeight()-drawH)/2f);canvas.scale(scale,scale);movie.draw(canvas,0,0);canvas.restoreToCount(save);
            postInvalidateDelayed(33L);
        }
    }

    private void renderAdminEconomy() {
        adminBack("Экономика", "ВАЛЮТЫ И ПЛАТЕЖИ"); addSpace(10);
        addSectionTitle("Игровой кошелёк", "Три внутриигровые валюты с автоматическим разменом");
        LinearLayout wallet=cardColumn();page.addView(wallet);
        wallet.addView(text("🟤 Бронза  →  🥈 Серебро  →  🪙 Золото",17,colorText(),true));
        wallet.addView(text("10 бронзовых = 1 серебряная\n10 серебряных = 1 золотая\n100 бронзовых = 1 золотая",14,colorPrimary(),true));
        TextView rule=text("Баланс хранится в нормализованном виде: при накоплении десяти монет меньшего номинала они автоматически превращаются в одну монету следующего номинала. Начисления и списания доступны в карточке каждого игрока.",12,colorMuted(),false);LinearLayout.LayoutParams rp=params(-1,-2);rp.topMargin=dp(8);wallet.addView(rule,rp);
        TextView players=actionButton("Управлять балансом и уровнями игроков",true);LinearLayout.LayoutParams pl=params(-1,46);pl.topMargin=dp(10);page.addView(players,pl);
        players.setOnClickListener(v->{screen="admin_players";render();});
        addSectionTitle("Telegram Stars", "Отдельно от внутриигровых монет");
        LinearLayout stars=cardColumn();page.addView(stars);
        stars.addView(text("⭐ Stars используются только для внешних покупок",15,colorText(),true));
        stars.addView(text("Telegram Stars не входят в курс обмена бронзы, серебра и золота. APK не хранит и не переводит Stars игрокам. Для подтверждения платежей нужен сервер с проверкой pre_checkout_query и успешного платежа Telegram.",13,colorMuted(),false));
        TextView status=actionButton("Открыть настройки Telegram-бота",false);LinearLayout.LayoutParams sp=params(-1,46);sp.topMargin=dp(12);page.addView(status,sp);
        status.setOnClickListener(v->{adminReturnScreen="admin";screen="admin_bot";render();});
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
        } else if (requestCode == NODE_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            saveSelectedNodeImage(data.getData());
        }
    }

    private void saveSelectedNodeImage(Uri uri) {
        JSONObject project = findProject(editingId); JSONObject node = project == null ? null : findNode(project, selectedNodeId);
        if (node == null) return;
        String extension = "jpg";
        String mime = getContentResolver().getType(uri);
        boolean convertWebp = "image/webp".equals(mime);
        if (mime != null) { if (mime.equals("image/png")) extension = "png"; else if (mime.equals("image/gif")) extension = "gif"; }
        File directory = new File(getFilesDir(), "node-images");
        if (!directory.exists() && !directory.mkdirs()) { Toast.makeText(this, "Не удалось создать хранилище изображения", Toast.LENGTH_SHORT).show(); return; }
        File output = new File(directory, node.optString("id", UUID.randomUUID().toString()) + "." + extension);
        long maxImageBytes = ("gif".equals(extension) ? 50L : 10L) * 1024L * 1024L;
        try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(output)) {
            if (input == null) throw new IllegalStateException("Image stream unavailable");
            if (convertWebp) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count; long total = 0;
                while ((count = input.read(buffer)) != -1) { total += count; if (total > maxImageBytes) throw new IllegalArgumentException("Image too large"); bytes.write(buffer, 0, count); }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size());
                if (bitmap == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IllegalArgumentException("Unsupported image");
                bitmap.recycle();
            } else {
                byte[] buffer = new byte[8192]; int count; long total = 0;
                while ((count = input.read(buffer)) != -1) { total += count; if (total > maxImageBytes) throw new IllegalArgumentException("Image too large"); out.write(buffer, 0, count); }
            }
            node.put("image", "node-images/" + output.getName());
            refreshNodeImageEditor(node);
            Toast.makeText(this, "Иллюстрация добавлена к узлу", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            output.delete(); Toast.makeText(this, "Не удалось загрузить изображение (лимит: JPEG/PNG 10 МБ, GIF 50 МБ)", Toast.LENGTH_LONG).show();
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
