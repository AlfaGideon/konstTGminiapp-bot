import JSZip from 'jszip';
import './style.css';

const icon = (name, size = 20) => {
  const paths = {
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    layers: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/>',
    book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1 1.4 1.1-1.4 2.4-1.7-.7a8 8 0 0 1-1.7 1l-.3 1.8h-2.8l-.3-1.8a8 8 0 0 1-1.7-1l-1.7.7-1.4-2.4L7 15a8 8 0 0 1 0-2l-1.5-1.1 1.4-2.4 1.7.7a8 8 0 0 1 1.7-1l.3-1.8h2.8l.3 1.8a8 8 0 0 1 1.7 1l1.7-.7 1.4 2.4L19.4 13a8 8 0 0 1 0 2Z" transform="translate(-1 -1)"/>',
    arrow: '<path d="M5 12h14M12 5l7 7-7 7"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5M12 15V3"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    bot: '<path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2m16 0h2M9 13h.01M15 13h.01M9 17h6"/>',
    phone: '<rect width="14" height="20" x="5" y="2" rx="2"/><path d="M12 18h.01"/>',
    close: '<path d="m18 6-12 12M6 6l12 12"/>',
    external: '<path d="M15 3h6v6m-11 5L21 3"/><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
  };
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || ''}</svg>`;
};

const app = document.querySelector('#app');
const stored = JSON.parse(localStorage.getItem('botcraft-projects') || '[]');
let projects = Array.isArray(stored) ? stored : [];
let page = 'home';
let currentId = null;
let currentTemplate = 'shop';

function persist() { localStorage.setItem('botcraft-projects', JSON.stringify(projects)); }
function safe(value = '') { return String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
function slugify(value) { return value.toLowerCase().trim().replace(/[^a-z0-9а-яё]+/gi, '-').replace(/^-|-$/g, '') || 'my-telegram-app'; }
function templateLabel(id) { return ({ shop: 'Магазин', booking: 'Запись', community: 'Сообщество' })[id] || 'Магазин'; }

function shell(content) {
  app.innerHTML = `<div class="shell">
    <aside class="sidebar"><a class="brand" href="#" data-page="home"><span class="brand-mark">${icon('bot', 22)}</span><span>botcraft<span class="brand-dot">.</span><small>STUDIO</small></span></a>
      <div class="side-label">РАБОЧЕЕ ПРОСТРАНСТВО</div>
      <nav class="side-nav">
        <button class="nav-item ${page === 'home' ? 'active' : ''}" data-page="home">${icon('grid')}<span>Обзор</span></button>
        <button class="nav-item ${page === 'templates' ? 'active' : ''}" data-page="templates">${icon('layers')}<span>Шаблоны</span><span class="nav-count">3</span></button>
        <button class="nav-item" data-page="guide">${icon('book')}<span>Как запустить</span></button>
      </nav>
      <div class="side-bottom"><div class="help-card"><div class="help-icon">✦</div><b>Начните с простого</b><p>Выберите шаблон — бот и Mini App уже будут связаны.</p><button data-page="guide">Открыть инструкцию ${icon('arrow', 15)}</button></div><div class="profile"><div class="avatar">B</div><div><b>Мой проект</b><small>Локальное хранилище</small></div><span class="online-dot"></span></div></div>
    </aside>
    <main class="main-area"><header class="topbar"><div class="breadcrumb"><span>Рабочее пространство</span><span class="crumb-slash">/</span><b>${page === 'builder' ? 'Новый проект' : page === 'templates' ? 'Шаблоны' : page === 'guide' ? 'Как запустить' : 'Обзор'}</b></div><div class="top-right"><span class="local-badge"><i></i> Все данные хранятся на устройстве</span><div class="avatar small-avatar">B</div></div></header><section class="page-content">${content}</section></main>
    <nav class="mobile-nav"><button data-page="home" class="${page === 'home' ? 'active' : ''}">${icon('grid', 19)}<span>Обзор</span></button><button data-page="templates" class="${page === 'templates' ? 'active' : ''}">${icon('layers', 19)}<span>Шаблоны</span></button><button data-page="guide" class="${page === 'guide' ? 'active' : ''}">${icon('book', 19)}<span>Помощь</span></button></nav>
  </div>`;
  app.querySelectorAll('[data-page]').forEach(el => el.addEventListener('click', e => { e.preventDefault(); page = el.dataset.page; render(); }));
}

function renderHome() {
  const recent = projects.slice(-3).reverse();
  const content = `<div class="welcome-row"><div><div class="eyebrow"><span class="eyebrow-line"></span> ВАША СТУДИЯ TELEGRAM</div><h1>От идеи до бота.<br><span>Без лишнего кода.</span></h1><p class="intro">Соберите Telegram-бота и Mini App в одном месте. Выберите основу, настройте внешний вид и скачайте готовый проект.</p><button class="primary-btn" id="create-project">${icon('plus', 18)} Создать проект <span class="btn-arrow">${icon('arrow', 17)}</span></button></div><div class="hero-art"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="hero-phone"><div class="phone-camera"></div><div class="phone-head"><span>‹</span><b>Мой бот</b><span>•••</span></div><div class="chat-line"></div><div class="chat-bubble">Привет! Что хотите сделать?</div><div class="mock-product"><span>✦</span><div><b>Mini App</b><small>Прямо внутри Telegram</small></div><span class="mock-go">↗</span></div><div class="mock-input">Сообщение... <span>➤</span></div></div><div class="float-chip chip-bot">${icon('bot', 17)} <span>Telegram Bot</span><i></i></div><div class="float-chip chip-app">${icon('phone', 17)} <span>Mini App</span><i></i></div><div class="sparkle sparkle-a">✳</div><div class="sparkle sparkle-b">✦</div></div></div>
    <div class="section-heading"><div><span class="eyebrow small-eyebrow">СТАРТ ЗА ПАРУ МИНУТ</span><h2>Выберите сценарий</h2></div><button class="text-link" data-page="templates">Все шаблоны ${icon('arrow', 16)}</button></div>
    <div class="template-grid">${templateCard('shop','🛍️','Магазин','Каталог, карточки товаров и кнопка заказа','04 мин','violet')}${templateCard('booking','📅','Запись на услуги','Услуги, выбор времени и заявка администратору','05 мин','blue')}${templateCard('community','💬','Сообщество','Полезные ссылки, новости и быстрый контакт','03 мин','mint')}</div>
    <div class="section-heading recent-heading"><div><span class="eyebrow small-eyebrow">ВАШИ РАБОТЫ</span><h2>Последние проекты</h2></div></div>
    ${recent.length ? `<div class="project-list">${recent.map(p => `<button class="project-row" data-open="${safe(p.id)}"><span class="project-icon ${safe(p.template)}">${p.template === 'booking' ? '📅' : p.template === 'community' ? '💬' : '🛍️'}</span><span class="project-info"><b>${safe(p.name)}</b><small>${safe(templateLabel(p.template))} · обновлён ${new Date(p.updatedAt).toLocaleDateString('ru-RU')}</small></span><span class="project-state"><i></i> Черновик</span>${icon('arrow', 17)}</button>`).join('')}</div>` : `<div class="empty-projects"><div class="empty-icon">${icon('layers', 22)}</div><div><b>Здесь появятся ваши проекты</b><p>Создайте первый — он сохранится на этом устройстве.</p></div><button class="secondary-btn" id="create-empty">${icon('plus', 16)} Новый проект</button></div>`}`;
  shell(content);
  app.querySelector('#create-project')?.addEventListener('click', () => openBuilder());
  app.querySelector('#create-empty')?.addEventListener('click', () => openBuilder());
  app.querySelectorAll('[data-template]').forEach(b => b.addEventListener('click', () => openBuilder(null, b.dataset.template)));
  app.querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', () => openBuilder(projects.find(p => p.id === b.dataset.open))));
}
function templateCard(id, emoji, title, desc, time, color) { return `<button class="template-card ${color}" data-template="${id}"><div class="template-top"><span class="template-emoji">${emoji}</span><span class="template-time">${time} НАСТРОЙКА</span></div><h3>${title}</h3><p>${desc}</p><span class="card-link">Использовать шаблон ${icon('arrow', 16)}</span></button>`; }

function renderTemplates() {
  shell(`<div class="eyebrow"><span class="eyebrow-line"></span> СТАРТОВАЯ ТОЧКА</div><h1 class="page-title">Шаблоны</h1><p class="page-lead">Выберите тип проекта. Каждый шаблон включает стартовый интерфейс Mini App и готовую связку с ботом.</p><div class="template-grid templates-page">${templateCard('shop','🛍️','Магазин','Каталог, карточки товаров и кнопка заказа','04 мин','violet')}${templateCard('booking','📅','Запись на услуги','Услуги, выбор времени и заявка администратору','05 мин','blue')}${templateCard('community','💬','Сообщество','Полезные ссылки, новости и быстрый контакт','03 мин','mint')}</div><div class="notice-box">${icon('check', 18)} <div><b>Все шаблоны — это стартовые проекты</b><p>Скачанный код можно дорабатывать самостоятельно или передать разработчику.</p></div></div>`);
  app.querySelectorAll('[data-template]').forEach(b => b.addEventListener('click', () => openBuilder(null, b.dataset.template)));
}

function renderGuide() {
  shell(`<div class="eyebrow"><span class="eyebrow-line"></span> ПУТЬ К ЗАПУСКУ</div><h1 class="page-title">Как запустить проект</h1><p class="page-lead">Конструктор создаёт исходный код. Для публикации понадобится токен Telegram-бота и публичный HTTPS-адрес сервера.</p><div class="guide-list">${[['01','Создайте бота в Telegram','Откройте @BotFather, отправьте /newbot и сохраните выданный токен. Никому его не отправляйте.'],['02','Соберите проект здесь','Выберите шаблон, укажите название и скачайте архив с исходным кодом.'],['03','Настройте адрес Mini App','Разверните проект на сервере с HTTPS. В BotFather откройте настройки бота и добавьте URL Mini App.'],['04','Запустите бота','Укажите токен в .env, установите зависимости командой npm install и запустите npm start.']] .map(([n,t,d])=>`<article class="guide-step"><span class="step-number">${n}</span><div><h3>${t}</h3><p>${d}</p></div>${icon('check', 18)}</article>`).join('')}</div><div class="security-note"><span>🔒</span><div><b>Токен — это пароль от бота</b><p>Не публикуйте его в исходном коде и не загружайте файл .env в GitHub. Если токен утёк, отзовите его через @BotFather.</p></div></div>`);
}

function render() {
  if (page === 'templates') return renderTemplates();
  if (page === 'guide') return renderGuide();
  if (page === 'builder') return renderBuilder();
  renderHome();
}

function openBuilder(project = null, template = 'shop') {
  currentId = project?.id || null;
  currentTemplate = project?.template || template;
  page = 'builder';
  render();
  const form = app.querySelector('#builder-form');
  if (project) {
    form.elements.projectName.value = project.name || '';
    form.elements.botName.value = project.botName || '';
    form.elements.welcome.value = project.welcome || 'Привет! Рады вас видеть.';
    form.elements.buttonLabel.value = project.buttonLabel || 'Открыть приложение';
    form.elements.accent.value = project.accent || '#7664f4';
  }
  updatePreview();
}

function renderBuilder() {
  shell(`<div class="builder-heading"><div><button class="back-link" id="back-home">← <span>Все проекты</span></button><div class="eyebrow"><span class="eyebrow-line"></span> НОВЫЙ TELEGRAM-ПРОЕКТ</div><h1 class="page-title">Настройте свою основу</h1><p class="page-lead">Заполните главное — мы соберём стартовый код бота и Mini App.</p></div><div class="step-pill"><span>01</span> Настройка <i></i><span>02</span> Экспорт</div></div>
    <div class="builder-layout"><form id="builder-form" class="builder-form"><section class="form-section"><div class="form-title"><span class="form-ico">${icon('layers', 19)}</span><div><h2>Основа проекта</h2><p>Выберите сценарий, с которого начнём</p></div></div><div class="choice-row">${[['shop','🛍️','Магазин'],['booking','📅','Запись'],['community','💬','Сообщество']].map(([id,emoji,title])=>`<label class="choice-card ${currentTemplate === id ? 'selected' : ''}"><input type="radio" name="template" value="${id}" ${currentTemplate === id ? 'checked' : ''}><span>${emoji}</span><b>${title}</b>${currentTemplate === id ? `<i>${icon('check', 13)}</i>` : ''}</label>`).join('')}</div></section>
    <section class="form-section"><div class="form-title"><span class="form-ico">${icon('bot', 19)}</span><div><h2>О вашем проекте</h2><p>Эти данные попадут в стартовый шаблон</p></div></div><label class="field-label" for="projectName">Название проекта <span>*</span></label><input id="projectName" name="projectName" class="text-input" placeholder="Например, Цветочная лавка" required maxlength="48"><div class="field-hint">Название будет показано в вашем списке проектов.</div><label class="field-label mt-field" for="botName">Имя бота <span class="optional">необязательно</span></label><div class="input-with-prefix"><span>@</span><input id="botName" name="botName" class="text-input" placeholder="my_flower_shop_bot" maxlength="32"></div><label class="field-label mt-field" for="welcome">Приветствие бота</label><textarea id="welcome" name="welcome" class="text-input text-area" maxlength="250">Привет! Рады вас видеть.</textarea><div class="field-hint">Короткое сообщение, которое бот отправит при команде /start.</div></section>
    <section class="form-section last-section"><div class="form-title"><span class="form-ico">✦</span><div><h2>Внешний вид</h2><p>Базовый акцент для вашего Mini App</p></div></div><label class="field-label" for="buttonLabel">Текст кнопки</label><input id="buttonLabel" name="buttonLabel" class="text-input" value="Открыть приложение" maxlength="32"><label class="field-label mt-field">Цвет акцента</label><div class="color-row"><label class="color-choice"><input type="radio" name="accent" value="#7664f4" checked><span style="--swatch:#7664f4"></span><b>Лавандовый</b></label><label class="color-choice"><input type="radio" name="accent" value="#2394e8"><span style="--swatch:#2394e8"></span><b>Синий</b></label><label class="color-choice"><input type="radio" name="accent" value="#2eae83"><span style="--swatch:#2eae83"></span><b>Мятный</b></label><label class="color-choice"><input type="radio" name="accent" value="#f08a4b"><span style="--swatch:#f08a4b"></span><b>Персиковый</b></label></div></section>
    <section class="form-section token-section"><div class="form-title"><span class="form-ico">🔐</span><div><h2>Токен бота</h2><p>Можно добавить сейчас или позже в файле .env</p></div></div><label class="field-label" for="botToken">Токен от @BotFather <span class="optional">необязательно</span></label><input id="botToken" name="botToken" class="text-input" type="password" autocomplete="new-password" placeholder="123456789:AA..." spellcheck="false"><div class="field-hint">Токен остаётся на вашем устройстве. Если введёте его, он попадёт только в локально скачанный .env.</div></section>
    <div class="builder-actions"><button type="button" class="secondary-btn" id="save-draft">Сохранить черновик</button><button type="submit" class="primary-btn">Скачать проект ${icon('download', 17)}</button></div></form>
    <aside class="preview-column"><div class="preview-label"><span><i></i> ПРЕДПРОСМОТР</span><button type="button" id="phone-toggle">${icon('phone', 16)} Mini App</button></div><div class="phone-preview"><div class="preview-status"><span>9:41</span><span>●●●  ▰</span></div><div class="preview-top"><span>‹</span><b>Telegram</b><span>•••</span></div><div class="preview-chat"><span class="preview-avatar">${icon('bot', 17)}</span><div><div class="preview-sender">${safe(document.querySelector('#projectName')?.value || 'Ваш бот')}</div><div class="preview-welcome">Привет! Рады вас видеть.</div></div></div><div class="preview-app"><div class="app-topline"><span class="app-logo">✦</span><span class="app-menu">•••</span></div><span class="app-mini-tag">TELEGRAM MINI APP</span><h3 class="preview-app-title">Добро пожаловать!</h3><p class="preview-app-desc">Ваше приложение уже готово к работе</p><div class="app-placeholder"><span class="placeholder-sun">✦</span><span class="placeholder-line"></span><span class="placeholder-line short"></span><div class="placeholder-cards"><i>✧</i><i>◈</i><i>↗</i></div></div><button class="preview-cta">${safe(document.querySelector('#builder-form')?.elements.buttonLabel.value || 'Открыть приложение')} <span>→</span></button><div class="app-powered">СОЗДАНО В BOTCRAFT</div></div></div><div class="preview-footnote">${icon('check', 15)} Так будет выглядеть стартовый экран</div><div class="tip-card"><span>💡</span><p><b>Следующий шаг после скачивания</b><br>Добавьте публичный HTTPS-адрес в настройки бота через @BotFather.</p></div></aside></div>`);
  const form = app.querySelector('#builder-form');
  app.querySelector('#back-home').addEventListener('click', () => { page = 'home'; render(); });
  form.addEventListener('input', updatePreview);
  form.elements.template.forEach(input => input.addEventListener('change', () => { currentTemplate = input.value; form.querySelectorAll('.choice-card').forEach(c => c.classList.toggle('selected', c.querySelector('input').checked)); updatePreview(); }));
  app.querySelector('#save-draft').addEventListener('click', () => saveProject(false));
  form.addEventListener('submit', async e => { e.preventDefault(); if (!form.reportValidity()) return; const project = saveProject(false); await downloadProject(project, form.elements.botToken.value); });
}

function updatePreview() {
  const f = app.querySelector('#builder-form'); if (!f) return;
  const name = f.elements.projectName.value || 'Ваш бот';
  app.querySelector('.preview-sender').textContent = name;
  app.querySelector('.preview-welcome').textContent = f.elements.welcome.value || 'Привет! Рады вас видеть.';
  app.querySelector('.preview-cta').firstChild.textContent = `${f.elements.buttonLabel.value || 'Открыть приложение'} `;
  const accent = f.elements.accent.value;
  app.querySelector('.phone-preview').style.setProperty('--accent', accent);
}

function saveProject(showToast = true) {
  const f = app.querySelector('#builder-form');
  const data = { id: currentId || crypto.randomUUID(), name: f.elements.projectName.value.trim(), botName: f.elements.botName.value.trim(), welcome: f.elements.welcome.value.trim(), buttonLabel: f.elements.buttonLabel.value.trim() || 'Открыть приложение', accent: f.elements.accent.value, template: f.elements.template.value, updatedAt: new Date().toISOString() };
  const index = projects.findIndex(p => p.id === data.id);
  if (index >= 0) projects[index] = data; else projects.push(data);
  currentId = data.id; persist();
  if (showToast) toast('Черновик сохранён на устройстве');
  return data;
}

async function downloadProject(project, token) {
  const zip = new JSZip();
  const slug = slugify(project.name);
  const welcome = project.welcome || 'Привет! Рады вас видеть.';
  const title = project.name || 'Моё приложение';
  const button = project.buttonLabel || 'Открыть приложение';
  const accent = project.accent || '#7664f4';
  const templateText = project.template === 'booking' ? 'Запишитесь на удобное время — мы скоро подтвердим заявку.' : project.template === 'community' ? 'Все важные ссылки и новости сообщества — в одном месте.' : 'Выберите то, что вам нравится, а мы поможем с заказом.';
  zip.file('package.json', JSON.stringify({ name: slugify(slug.replace(/[^a-z0-9-]/gi, '-')), version: '1.0.0', private: true, type: 'module', scripts: { start: 'node bot/index.js', dev: 'node --watch bot/index.js' }, dependencies: { dotenv: '^16.4.7', express: '^4.21.2', 'node-telegram-bot-api': '^0.66.0' } }, null, 2));
  zip.file('.gitignore', 'node_modules/\n.env\n.DS_Store\n');
  zip.file('.env.example', `BOT_TOKEN=вставьте_токен_от_BotFather\nWEBAPP_URL=https://your-domain.com\nPORT=3000\n`);
  if (token.trim()) zip.file('.env', `BOT_TOKEN=${token.trim()}\nWEBAPP_URL=https://your-domain.com\nPORT=3000\n`);
  zip.file('bot/index.js', `import 'dotenv/config';\nimport express from 'express';\nimport TelegramBot from 'node-telegram-bot-api';\nimport path from 'node:path';\nimport { fileURLToPath } from 'node:url';\n\nconst __dirname = path.dirname(fileURLToPath(import.meta.url));\nconst app = express();\nconst port = Number(process.env.PORT || 3000);\nconst webappUrl = process.env.WEBAPP_URL;\nconst token = process.env.BOT_TOKEN;\n\nif (!token || token.includes('вставьте_токен')) throw new Error('Укажите BOT_TOKEN в .env');\nif (!webappUrl || !webappUrl.startsWith('https://')) throw new Error('Укажите публичный HTTPS WEBAPP_URL в .env');\n\napp.use(express.static(path.join(__dirname, '../miniapp')));\napp.get('/api/health', (_req, res) => res.json({ ok: true }));\napp.listen(port, () => console.log('Mini App server listening on port ' + port));\n\nconst bot = new TelegramBot(token, { polling: true });\nbot.onText(/\\/start/, (msg) => {\n  const keyboard = { inline_keyboard: [[{ text: ${JSON.stringify(button)}, web_app: { url: webappUrl } }]] };\n  bot.sendMessage(msg.chat.id, ${JSON.stringify(welcome)}, { reply_markup: keyboard });\n});\nbot.on('polling_error', (error) => console.error('Telegram polling error:', error.message));\nconsole.log('Telegram bot is running');\n`);
  zip.file('miniapp/index.html', `<!doctype html>\n<html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="${accent}"><title>${safe(title)}</title><link rel="stylesheet" href="./style.css"></head><body><main class="app"><header><span class="logo">✦</span><span class="tag">TELEGRAM MINI APP</span></header><h1>${safe(title)}</h1><p class="intro">${safe(templateText)}</p><section class="content-card"><div class="decoration">✦</div><div class="skeleton"></div><div class="skeleton short"></div><div class="cards"><div>✧</div><div>◈</div><div>↗</div></div></section><button id="action">${safe(button)} <span>→</span></button><p class="hint" id="user-info">Откройте приложение внутри Telegram</p></main><script src="https://telegram.org/js/telegram-web-app.js"></script><script src="./app.js"></script></body></html>`);
  zip.file('miniapp/style.css', `:root{font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#20253a;background:#f5f6fb;--accent:${accent}}*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:24px 18px;background:radial-gradient(ellipse at 100% 0%,color-mix(in srgb,var(--accent) 13%,transparent),transparent 40%),#f5f6fb}.app{max-width:520px;margin:0 auto;padding:20px 18px 32px}header{display:flex;align-items:center;gap:10px;margin-bottom:38px}.logo{display:grid;place-items:center;width:36px;height:36px;border-radius:12px;background:var(--accent);color:white;font-size:22px}.tag{font-size:10px;letter-spacing:1.5px;font-weight:800;color:#9298aa}h1{font-size:30px;letter-spacing:-1px;margin:0 0 9px}.intro{color:#737b8e;line-height:1.6;margin:0 0 28px}.content-card{border:1px solid #e9eaf1;border-radius:24px;background:white;padding:24px;box-shadow:0 14px 40px #25294a0c}.decoration{height:126px;border-radius:17px;background:linear-gradient(135deg,color-mix(in srgb,var(--accent) 18%,white),#fff);display:grid;place-items:center;color:var(--accent);font-size:64px}.skeleton{height:10px;border-radius:6px;background:#f0f1f6;margin-top:22px}.skeleton.short{width:55%;margin-top:9px}.cards{display:flex;gap:10px;margin-top:22px}.cards div{flex:1;height:64px;display:grid;place-items:center;border-radius:14px;background:#f7f7fb;color:var(--accent);font-size:22px}button{width:100%;margin-top:22px;background:var(--accent);border:0;border-radius:15px;color:white;padding:16px;font-weight:700;font-size:15px;box-shadow:0 8px 20px color-mix(in srgb,var(--accent) 25%,transparent)}button span{float:right}.hint{text-align:center;color:#949aab;font-size:12px;margin-top:16px}`);
  zip.file('miniapp/app.js', `const tg = window.Telegram?.WebApp;\nif (tg) {\n  tg.ready();\n  tg.expand();\n  const user = tg.initDataUnsafe?.user;\n  if (user) document.querySelector('#user-info').textContent = 'Добро пожаловать, ' + (user.first_name || 'друг') + '!';\n  document.querySelector('#action').addEventListener('click', () => tg.showAlert('Mini App работает. Здесь можно добавить свой сценарий.'));\n} else {\n  document.querySelector('#action').addEventListener('click', () => alert('Откройте Mini App внутри Telegram.'));\n}\n`);
  zip.file('README.md', `# ${title}\n\nСтартовый проект Telegram-бота и Mini App, собранный в Botcraft Studio.\n\n## Что внутри\n- \`bot/index.js\` — Telegram-бот на long polling и Express-сервер для Mini App.\n- \`miniapp/\` — веб-интерфейс, адаптированный для Telegram Web Apps.\n- Бот отвечает на \`/start\` и показывает кнопку открытия Mini App.\n\n## Локальный запуск\n1. Установите Node.js 20 или новее.\n2. Скопируйте \`.env.example\` в \`.env\` (если файл \`.env\` уже есть, проверьте токен).\n3. Впишите токен от @BotFather в \`BOT_TOKEN\`.\n4. Разверните приложение на публичном сервере с HTTPS и укажите URL в \`WEBAPP_URL\`. Mini App должен открываться по этому адресу.\n5. Выполните \`npm install\`, затем \`npm start\`.\n6. В @BotFather настройте домен/адрес Web App для вашего бота.\n\nДля локальной проверки Mini App нужен публичный HTTPS-туннель. Не публикуйте файл \`.env\`: он содержит секретный токен бота. Не запускайте два экземпляра бота с одним токеном одновременно.\n\nШаблон — основа для разработки, а не готовый интернет-магазин/сервис записи: подключите свою базу данных и обработчики заказов перед использованием в продакшене.\n`);
  const blob = await zip.generateAsync({ type: 'blob' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `${slug}.zip`; a.click(); URL.revokeObjectURL(a.href);
  toast('Архив проекта скачан');
}

function toast(message) {
  let el = document.querySelector('.toast');
  if (!el) { el = document.createElement('div'); el.className = 'toast'; document.body.append(el); }
  el.innerHTML = `${icon('check', 17)} ${safe(message)}`;
  requestAnimationFrame(() => el.classList.add('show'));
  clearTimeout(window.toastTimer); window.toastTimer = setTimeout(() => el.classList.remove('show'), 2600);
}

render();
