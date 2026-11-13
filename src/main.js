import JSZip from 'jszip';
import { Browser } from '@capacitor/browser';
import './style.css';
import './games.css';
import './update.css';

const APP_VERSION = import.meta.env.VITE_APP_VERSION || '0.1.0';

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
let currentTemplate = 'quiz';
let previewMode = 'miniapp';
let previewGameState = {};

const gameTemplates = [
  { id: 'quiz', emoji: '🧠', title: 'Квиз: битва знаний', desc: 'Раунды вопросов, варианты ответов, очки и результат', time: '05 мин', color: 'violet' },
  { id: 'clicker', emoji: '⚡', title: 'Кликер: набери заряд', desc: 'Тапай, копи очки, открывай уровни и улучшения', time: '04 мин', color: 'blue' },
  { id: 'memory', emoji: '🃏', title: 'Найди пару', desc: 'Игра на память, поле карточек, ходы и таймер', time: '05 мин', color: 'mint' },
];
const gameById = id => gameTemplates.find(game => game.id === id) || gameTemplates[0];

function persist() { localStorage.setItem('botcraft-projects', JSON.stringify(projects)); }
function safe(value = '') { return String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
function slugify(value) { return value.toLowerCase().trim().replace(/[^a-z0-9а-яё]+/gi, '-').replace(/^-|-$/g, '') || 'my-telegram-app'; }
function templateLabel(id) { return gameById(id).title; }

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
  const content = `<div class="welcome-row"><div><div class="eyebrow"><span class="eyebrow-line"></span> ВАША СТУДИЯ TELEGRAM</div><h1>От идеи до бота.<br><span>Без лишнего кода.</span></h1><p class="intro">Соберите игрового Telegram-бота и Mini App. Выберите механику, поиграйте в предпросмотре и скачайте исходники проекта.</p><button class="primary-btn" id="create-project">${icon('plus', 18)} Создать проект <span class="btn-arrow">${icon('arrow', 17)}</span></button></div><div class="hero-art"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="hero-phone"><div class="phone-camera"></div><div class="phone-head"><span>‹</span><b>Мой бот</b><span>•••</span></div><div class="chat-line"></div><div class="chat-bubble">Готов сыграть? Выбирай игру 🎮</div><div class="mock-product"><span>⚡</span><div><b>Заряд: 48</b><small>Кликер — новый рекорд!</small></div><span class="mock-go">🎮</span></div><div class="mock-input">Сообщение... <span>➤</span></div></div><div class="float-chip chip-bot">${icon('bot', 17)} <span>Telegram Bot</span><i></i></div><div class="float-chip chip-app">${icon('phone', 17)} <span>Mini App</span><i></i></div><div class="sparkle sparkle-a">✳</div><div class="sparkle sparkle-b">✦</div></div></div>
    <div class="section-heading"><div><span class="eyebrow small-eyebrow">СТАРТ ЗА ПАРУ МИНУТ</span><h2>Выберите сценарий</h2></div><button class="text-link" data-page="templates">Все шаблоны ${icon('arrow', 16)}</button></div>
    <div class="template-grid">${gameTemplates.map(t => templateCard(t.id,t.emoji,t.title,t.desc,t.time,t.color)).join('')}</div>
    <div class="section-heading recent-heading"><div><span class="eyebrow small-eyebrow">ВАШИ РАБОТЫ</span><h2>Последние проекты</h2></div></div>
    ${recent.length ? `<div class="project-list">${recent.map(p => `<button class="project-row" data-open="${safe(p.id)}"><span class="project-icon ${safe(p.template)}">${gameById(p.template).emoji}</span><span class="project-info"><b>${safe(p.name)}</b><small>${safe(templateLabel(p.template))} · обновлён ${new Date(p.updatedAt).toLocaleDateString('ru-RU')}</small></span><span class="project-state"><i></i> Черновик</span>${icon('arrow', 17)}</button>`).join('')}</div>` : `<div class="empty-projects"><div class="empty-icon">${icon('layers', 22)}</div><div><b>Здесь появятся ваши проекты</b><p>Создайте первый — он сохранится на этом устройстве.</p></div><button class="secondary-btn" id="create-empty">${icon('plus', 16)} Новый проект</button></div>`}`;
  shell(content);
  app.querySelector('#create-project')?.addEventListener('click', () => openBuilder());
  app.querySelector('#create-empty')?.addEventListener('click', () => openBuilder());
  app.querySelectorAll('[data-template]').forEach(b => b.addEventListener('click', () => openBuilder(null, b.dataset.template)));
  app.querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', () => openBuilder(projects.find(p => p.id === b.dataset.open))));
}
function templateCard(id, emoji, title, desc, time, color) { return `<button class="template-card ${color}" data-template="${id}"><div class="template-top"><span class="template-emoji">${emoji}</span><span class="template-time">${time} НАСТРОЙКА</span></div><h3>${title}</h3><p>${desc}</p><span class="card-link">Использовать шаблон ${icon('arrow', 16)}</span></button>`; }

function renderTemplates() {
  shell(`<div class="eyebrow"><span class="eyebrow-line"></span> СТАРТОВАЯ ТОЧКА</div><h1 class="page-title">Игровые шаблоны</h1><p class="page-lead">Три готовых игровых механики для Telegram Mini App: быстрый квиз, кликер и игра на память. Каждую можно менять и расширять.</p><div class="template-grid templates-page">${gameTemplates.map(t => templateCard(t.id,t.emoji,t.title,t.desc,t.time,t.color)).join('')}</div><div class="notice-box">${icon('check', 18)} <div><b>Игры сразу запускаются в предпросмотре</b><p>Поиграйте прямо в конструкторе, затем скачайте исходный Mini App и подключённого Telegram-бота.</p></div></div>`);
  app.querySelectorAll('[data-template]').forEach(b => b.addEventListener('click', () => openBuilder(null, b.dataset.template)));
}

function renderGuide() {
  shell(`<div class="eyebrow"><span class="eyebrow-line"></span> ПУТЬ К ЗАПУСКУ</div><h1 class="page-title">Как запустить проект</h1><p class="page-lead">Конструктор создаёт исходный код. Для публикации понадобится токен Telegram-бота и публичный HTTPS-адрес сервера.</p><div class="guide-list">${[['01','Создайте бота в Telegram','Откройте @BotFather, отправьте /newbot и сохраните выданный токен. Никому его не отправляйте.'],['02','Соберите проект здесь','Выберите шаблон, укажите название и скачайте архив с исходным кодом.'],['03','Настройте адрес Mini App','Разверните проект на сервере с HTTPS. В BotFather откройте настройки бота и добавьте URL Mini App.'],['04','Запустите бота','Укажите токен в .env, установите зависимости командой npm install и запустите npm start.']] .map(([n,t,d])=>`<article class="guide-step"><span class="step-number">${n}</span><div><h3>${t}</h3><p>${d}</p></div>${icon('check', 18)}</article>`).join('')}</div><div class="update-card"><div><span class="eyebrow small-eyebrow">BOTCRAFT STUDIO</span><h3>Обновления приложения</h3><p>Проверить свежую версию в GitHub Releases. Установка скачанного APK подтверждается самим Android.</p></div><button class="secondary-btn" id="check-updates">Проверить обновления</button><small id="update-status">Текущая версия ${APP_VERSION}</small></div><div class="security-note"><span>🔒</span><div><b>Токен — это пароль от бота</b><p>Не публикуйте его в исходном коде и не загружайте файл .env в GitHub. Если токен утёк, отзовите его через @BotFather.</p></div></div>`);
  app.querySelector('#check-updates').addEventListener('click', checkForUpdates);
}

async function checkForUpdates() {
  const button = app.querySelector('#check-updates');
  const status = app.querySelector('#update-status');
  if (!button || !status) return;
  button.disabled = true;
  button.textContent = 'Проверяю…';
  try {
    const response = await fetch('https://api.github.com/repos/AlfaGideon/konstTGminiapp-bot/releases/latest', { headers: { Accept: 'application/vnd.github+json' } });
    if (!response.ok) throw new Error('Не удалось получить список релизов');
    const release = await response.json();
    const latest = String(release.tag_name || '').replace(/^v/, '');
    const asset = (release.assets || []).find(item => item.name === 'botcraft-studio.apk') || (release.assets || []).find(item => item.name.toLowerCase().endsWith('.apk'));
    const versionParts = value => String(value).split('.').map(part => Number.parseInt(part, 10) || 0);
    const [a = 0,b = 0,c = 0] = versionParts(latest);
    const [x = 0,y = 0,z = 0] = versionParts(APP_VERSION);
    const isNewer = a > x || (a === x && (b > y || (b === y && c > z)));
    if (!asset) { status.textContent = `Найден релиз ${latest}, но APK-файл не приложен.`; return; }
    if (!isNewer) { status.textContent = `У вас актуальная версия ${APP_VERSION}. Последний релиз: ${latest}.`; return; }
    status.textContent = `Доступно обновление ${latest}.`;
    if (window.confirm(`Доступна Botcraft Studio ${latest}. Скачать APK? Android попросит подтвердить установку.`)) {
      await Browser.open({ url: asset.browser_download_url });
    }
  } catch (error) {
    status.textContent = 'Пока нет опубликованных обновлений или нет соединения.';
  } finally {
    button.disabled = false;
    button.textContent = 'Проверить снова';
  }
}

function render() {
  if (page === 'templates') return renderTemplates();
  if (page === 'guide') return renderGuide();
  if (page === 'builder') return renderBuilder();
  renderHome();
}

function openBuilder(project = null, template = 'quiz') {
  currentId = project?.id || null;
  currentTemplate = gameTemplates.some(t => t.id === project?.template) ? project.template : (gameTemplates.some(t => t.id === template) ? template : 'quiz');
  previewGameState = {};
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
  const game = gameById(currentTemplate);
  shell(`<div class="builder-heading"><div><button class="back-link" id="back-home">← <span>Все проекты</span></button><div class="eyebrow"><span class="eyebrow-line"></span> TELEGRAM GAME STUDIO</div><h1 class="page-title">Соберите свою игру</h1><p class="page-lead">Выберите механику, настройте бота и проверьте игру прямо в работающем предпросмотре.</p></div><div class="step-pill"><span>01</span> Настройка <i></i><span>02</span> Экспорт</div></div>
    <div class="builder-layout"><form id="builder-form" class="builder-form"><section class="form-section"><div class="form-title"><span class="form-ico">${icon('layers', 19)}</span><div><h2>Игровая механика</h2><p>Выберите базу — каждую игру можно развивать дальше</p></div></div><div class="choice-row">${gameTemplates.map(t=>`<label class="choice-card ${currentTemplate === t.id ? 'selected' : ''}"><input type="radio" name="template" value="${t.id}" ${currentTemplate === t.id ? 'checked' : ''}><span>${t.emoji}</span><b>${t.title}</b>${currentTemplate === t.id ? `<i>${icon('check', 13)}</i>` : ''}</label>`).join('')}</div></section>
    <section class="form-section"><div class="form-title"><span class="form-ico">${icon('bot', 19)}</span><div><h2>Название и бот</h2><p>Персонализируйте вход в игру</p></div></div><label class="field-label" for="projectName">Название игры <span>*</span></label><input id="projectName" name="projectName" class="text-input" placeholder="Например, Космический кликер" required maxlength="48"><div class="field-hint">Покажем это название в Mini App и списке проектов.</div><label class="field-label mt-field" for="botName">Username бота <span class="optional">необязательно</span></label><div class="input-with-prefix"><span>@</span><input id="botName" name="botName" class="text-input" placeholder="my_game_bot" maxlength="32"></div><label class="field-label mt-field" for="welcome">Сообщение бота /start</label><textarea id="welcome" name="welcome" class="text-input text-area" maxlength="250">Готов сыграть? Нажми кнопку и начинай!</textarea><div class="field-hint">Это сообщение будет приходить перед открытием игры.</div></section>
    <section class="form-section last-section"><div class="form-title"><span class="form-ico">✦</span><div><h2>Оформление</h2><p>Цвет кнопок и игровых элементов</p></div></div><label class="field-label" for="buttonLabel">Кнопка в чате бота</label><input id="buttonLabel" name="buttonLabel" class="text-input" value="Играть 🎮" maxlength="32"><label class="field-label mt-field">Цвет акцента</label><div class="color-row"><label class="color-choice"><input type="radio" name="accent" value="#7664f4" checked><span style="--swatch:#7664f4"></span><b>Лавандовый</b></label><label class="color-choice"><input type="radio" name="accent" value="#2394e8"><span style="--swatch:#2394e8"></span><b>Синий</b></label><label class="color-choice"><input type="radio" name="accent" value="#2eae83"><span style="--swatch:#2eae83"></span><b>Мятный</b></label><label class="color-choice"><input type="radio" name="accent" value="#f08a4b"><span style="--swatch:#f08a4b"></span><b>Персиковый</b></label></div></section>
    <section class="form-section token-section"><div class="form-title"><span class="form-ico">🔐</span><div><h2>Токен бота</h2><p>Можно добавить сейчас или позже в файле .env</p></div></div><label class="field-label" for="botToken">Токен от @BotFather <span class="optional">необязательно</span></label><input id="botToken" name="botToken" class="text-input" type="password" autocomplete="new-password" placeholder="123456789:AA..." spellcheck="false"><div class="field-hint">Токен остаётся на устройстве. Он добавляется только в скачанный .env.</div></section>
    <div class="builder-actions"><button type="button" class="secondary-btn" id="save-draft">Сохранить черновик</button><button type="submit" class="primary-btn">Скачать игру ${icon('download', 17)}</button></div></form>
    <aside class="preview-column"><div class="preview-label"><span><i></i> ЖИВОЙ ПРЕДПРОСМОТР</span><div class="preview-mode-tabs"><button type="button" data-mode="bot">Бот</button><button type="button" data-mode="miniapp" class="active">Игра</button></div></div><div class="phone-preview" data-mode="miniapp"><div class="preview-status"><span>9:41</span><span>●●●  ▰</span></div><div class="preview-top"><span>‹</span><b class="preview-top-title">Telegram</b><span>•••</span></div><div class="preview-chat"><span class="preview-avatar">${icon('bot', 17)}</span><div class="preview-message"><div class="preview-sender">Ваш игровой бот</div><div class="preview-welcome">Готов сыграть? Нажми кнопку и начинай!</div><button type="button" class="preview-telegram-cta" id="preview-open-app">${safe(document.querySelector('#builder-form')?.elements.buttonLabel.value || 'Играть 🎮')}　↗</button></div></div><div class="preview-app"><div class="app-topline"><span class="app-logo">🎮</span><span class="app-menu">•••</span></div><span class="app-mini-tag">TELEGRAM MINI APP · GAME</span><h3 class="preview-app-title">${safe(game.title)}</h3><p class="preview-app-desc">Нажми и сыграй прямо в Telegram</p><div id="preview-game-content" class="preview-game-content"></div><div class="app-powered">ИГРАЕТСЯ В TELEGRAM</div></div></div><div class="preview-footnote">${icon('check', 15)} Нажимайте на элементы игры в окне</div><div class="tip-card"><span>💡</span><p><b>Не просто картинка</b><br>Квиз, кликер и карточки уже реагируют на нажатия в предпросмотре.</p></div></aside></div>`);
  const form = app.querySelector('#builder-form');
  app.querySelector('#back-home').addEventListener('click', () => { page = 'home'; render(); });
  form.addEventListener('input', updatePreview);
  form.elements.template.forEach(input => input.addEventListener('change', () => {
    currentTemplate = input.value;
    previewGameState = {};
    form.querySelectorAll('.choice-card').forEach(c => c.classList.toggle('selected', c.querySelector('input').checked));
    updatePreview();
  }));
  app.querySelectorAll('[data-mode]').forEach(button => button.addEventListener('click', () => setPreviewMode(button.dataset.mode)));
  app.querySelector('#preview-open-app').addEventListener('click', () => setPreviewMode('miniapp'));
  app.querySelector('#save-draft').addEventListener('click', () => saveProject());
  form.addEventListener('submit', async e => { e.preventDefault(); if (!form.reportValidity()) return; const project = saveProject(false); await downloadProject(project, form.elements.botToken.value); });
  previewMode = 'miniapp';
  updatePreview();
}

function setPreviewMode(mode) {
  previewMode = mode;
  const phone = app.querySelector('.phone-preview');
  if (!phone) return;
  phone.dataset.mode = mode;
  app.querySelectorAll('.preview-mode-tabs [data-mode]').forEach(button => button.classList.toggle('active', button.dataset.mode === mode));
  const title = app.querySelector('.preview-top-title');
  if (title) title.textContent = mode === 'bot' ? 'Telegram' : 'Mini App';
}

function renderPreviewGame() {
  const root = app.querySelector('#preview-game-content');
  if (!root) return;
  const state = previewGameState;
  if (currentTemplate === 'clicker') {
    state.score ??= 0;
    root.innerHTML = `<div class="game-score-label">ТВОЙ ЗАРЯД</div><div class="game-score">${state.score}<span> ⚡</span></div><button type="button" class="game-tap-button" id="preview-tap">${state.score >= 10 ? 'НОВЫЙ РАУНД' : 'ТАП!'}</button><div class="game-hint">${state.score >= 10 ? 'Цель выполнена — круто!' : 'Цель: набрать 10 очков'}</div>`;
    root.querySelector('#preview-tap').addEventListener('click', () => { state.score = state.score >= 10 ? 0 : state.score + 1; renderPreviewGame(); });
  } else if (currentTemplate === 'memory') {
    state.cards ??= ['🌙', '⭐', '🌙', '⭐']; state.open ??= []; state.matched ??= []; state.moves ??= 0;
    root.innerHTML = `<div class="game-memory-top"><span>ХОДЫ <b>${state.moves}</b></span><span>ПАРЫ <b>${state.matched.length / 2}/2</b></span></div><div class="memory-grid">${state.cards.map((card,index)=>`<button type="button" class="memory-card ${state.open.includes(index) || state.matched.includes(index) ? 'revealed' : ''} ${state.matched.includes(index) ? 'matched' : ''}" data-card="${index}">${state.open.includes(index) || state.matched.includes(index) ? card : '?'}</button>`).join('')}</div><div class="game-hint">${state.matched.length === 4 ? 'Все пары найдены! 🎉' : 'Найди одинаковые карточки'}</div>`;
    root.querySelectorAll('[data-card]').forEach(button => button.addEventListener('click', () => {
      const index = Number(button.dataset.card);
      if (state.open.length >= 2 || state.open.includes(index) || state.matched.includes(index)) return;
      state.open.push(index);
      if (state.open.length === 2) {
        state.moves++;
        if (state.cards[state.open[0]] === state.cards[state.open[1]]) { state.matched.push(...state.open); state.open = []; renderPreviewGame(); }
        else { renderPreviewGame(); setTimeout(() => { state.open = []; if (app.querySelector('#preview-game-content')) renderPreviewGame(); }, 650); }
      } else renderPreviewGame();
    }));
  } else {
    state.question ??= 0; state.score ??= 0;
    const questions = [
      { text: 'Сколько планет в Солнечной системе?', answers: ['6', '8', '9'], correct: 1 },
      { text: 'Какой цвет получится из синего и жёлтого?', answers: ['Зелёный', 'Фиолетовый', 'Оранжевый'], correct: 0 },
      { text: 'Сколько будет 7 × 6?', answers: ['36', '40', '42'], correct: 2 },
    ];
    const q = questions[state.question % questions.length];
    root.innerHTML = `<div class="game-quiz-meta">ВОПРОС ${state.question + 1}　·　${state.score} ★</div><div class="game-question">${q.text}</div><div class="quiz-options">${q.answers.map((answer,index)=>`<button type="button" data-answer="${index}">${answer}</button>`).join('')}</div><div class="game-hint">${state.feedback || 'Выбери ответ'}</div>`;
    root.querySelectorAll('[data-answer]').forEach(button => button.addEventListener('click', () => { state.score += Number(button.dataset.answer) === q.correct ? 1 : 0; state.question++; state.feedback = Number(button.dataset.answer) === q.correct ? 'Верно! Следующий вопрос →' : 'Не угадал — следующий вопрос →'; renderPreviewGame(); }));
  }
}
function updatePreview() {
  const f = app.querySelector('#builder-form'); if (!f) return;
  const name = f.elements.projectName.value.trim() || gameById(currentTemplate).title;
  const welcome = f.elements.welcome.value.trim() || 'Готов сыграть? Нажми кнопку и начинай!';
  app.querySelector('.preview-sender').textContent = name;
  app.querySelector('.preview-welcome').textContent = welcome;
  app.querySelector('#preview-open-app').textContent = `${f.elements.buttonLabel.value || 'Играть 🎮'}　↗`;
  app.querySelector('.preview-app-title').textContent = name;
  app.querySelector('.phone-preview').style.setProperty('--accent', f.elements.accent.value);
  app.querySelector('.preview-top-title').textContent = previewMode === 'bot' ? 'Telegram' : 'Mini App';
  renderPreviewGame();
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

function generatedGameScript(template) {
  const common = `const tg = window.Telegram?.WebApp;
if (tg) { tg.ready(); tg.expand(); }
const surface = document.querySelector('#game-surface');
const haptic = (type = 'light') => tg?.HapticFeedback?.impactOccurred(type);
`;
  const scripts = {
    quiz: `const questions = [
  { q: 'Сколько планет в Солнечной системе?', a: ['6', '8', '9', '10'], ok: 1 },
  { q: 'Какой цвет получится из синего и жёлтого?', a: ['Зелёный', 'Фиолетовый', 'Оранжевый', 'Красный'], ok: 0 },
  { q: 'Сколько будет 7 × 6?', a: ['36', '40', '42', '48'], ok: 2 },
  { q: 'Как называется самая большая планета?', a: ['Марс', 'Сатурн', 'Земля', 'Юпитер'], ok: 3 },
  { q: 'Сколько минут в одном часе?', a: ['50', '60', '70', '100'], ok: 1 },
];
let round = 0; let score = 0;
function render() {
  if (round >= questions.length) {
    surface.innerHTML = '<article class="game-card result"><div class="game-emoji">🏆</div><h2>Раунд завершён!</h2><p>Твой результат: <b>' + score + ' из ' + questions.length + '</b></p><button class="primary" id="restart">Сыграть ещё</button></article>';
    surface.querySelector('#restart').onclick = () => { round = 0; score = 0; render(); }; return;
  }
  const item = questions[round];
  surface.innerHTML = '<div class="game-meta">ВОПРОС ' + (round + 1) + ' ИЗ ' + questions.length + '<span>★ ' + score + '</span></div><article class="game-card"><h2>' + item.q + '</h2><div class="answers">' + item.a.map((text, i) => '<button class="answer" data-answer="' + i + '">' + text + '</button>').join('') + '</div></article>';
  surface.querySelectorAll('[data-answer]').forEach(button => button.onclick = () => { if (+button.dataset.answer === item.ok) score++; haptic('light'); round++; render(); });
}
render();`,
    clicker: `let score = 0; const target = 30;
function render() {
  surface.innerHTML = score >= target
    ? '<article class="game-card result"><div class="game-emoji">⚡</div><h2>Заряд набран!</h2><p>Ты сделал это за ' + score + ' тапов.</p><button class="primary" id="restart">Новый раунд</button></article>'
    : '<div class="game-meta">ЦЕЛЬ: ' + target + ' ОЧКОВ<span>УРОВЕНЬ 1</span></div><article class="clicker-card"><div class="score">' + score + '<small> / ' + target + '</small></div><div class="progress"><i style="width:' + Math.min(100, score / target * 100) + '%"></i></div><button class="tap" id="tap">⚡<span>ТАП!</span></button><p>Нажимай, чтобы набрать заряд</p></article>';
  const tap = surface.querySelector('#tap'); if (tap) tap.onclick = () => { score++; haptic('medium'); render(); };
  const restart = surface.querySelector('#restart'); if (restart) restart.onclick = () => { score = 0; render(); };
}
render();`,
    memory: `const symbols = ['🍒', '🪐', '🚀', '💎', '⭐', '👾'];
let cards = []; let opened = []; let matched = []; let moves = 0; let locked = false;
function reset() { cards = [...symbols, ...symbols].sort(() => Math.random() - .5); opened = []; matched = []; moves = 0; locked = false; render(); }
function render() {
  if (matched.length === cards.length) {
    surface.innerHTML = '<article class="game-card result"><div class="game-emoji">🎉</div><h2>Все пары найдены!</h2><p>Количество ходов: <b>' + moves + '</b></p><button class="primary" id="restart">Играть снова</button></article>';
    surface.querySelector('#restart').onclick = reset; return;
  }
  surface.innerHTML = '<div class="game-meta">НАЙДИ ВСЕ ПАРЫ<span>ХОДЫ ' + moves + '</span></div><div class="memory-board">' + cards.map((symbol, i) => '<button class="memory-card ' + (opened.includes(i) || matched.includes(i) ? 'shown' : '') + ' ' + (matched.includes(i) ? 'matched' : '') + '" data-card="' + i + '">' + (opened.includes(i) || matched.includes(i) ? symbol : '?') + '</button>').join('') + '</div>';
  surface.querySelectorAll('[data-card]').forEach(button => button.onclick = () => {
    const index = +button.dataset.card;
    if (locked || opened.includes(index) || matched.includes(index)) return;
    opened.push(index); haptic('light');
    if (opened.length === 2) {
      moves++; locked = true; render();
      if (cards[opened[0]] === cards[opened[1]]) { matched.push(...opened); opened = []; locked = false; render(); }
      else setTimeout(() => { opened = []; locked = false; render(); }, 700);
    } else render();
  });
}
reset();`,
  };
  return `${common}\n${scripts[template] || scripts.quiz}`;
}

async function downloadProject(project, token) {
  const zip = new JSZip();
  const slug = slugify(project.name);
  const welcome = project.welcome || 'Готов сыграть? Нажми кнопку и начинай!';
  const title = project.name || 'Моя Telegram-игра';
  const button = project.buttonLabel || 'Играть 🎮';
  const accent = project.accent || '#7664f4';
  const copy = {
    quiz: 'Отвечай на вопросы, собирай очки и проверь, кто здесь главный знаток.',
    clicker: 'Набирай заряд тапами, выполняй цель раунда и бей личный рекорд.',
    memory: 'Открывай карточки, находи пары и постарайся уложиться в меньшее число ходов.',
  }[project.template] || 'Мини-игра работает прямо внутри Telegram.';
  const npmName = slugify(slug.replace(/[^a-z0-9-]/gi, '-')) || 'telegram-game';
  zip.file('package.json', JSON.stringify({ name: npmName, version: '1.0.0', private: true, type: 'module', scripts: { start: 'node bot/index.js', dev: 'node --watch bot/index.js' }, dependencies: { dotenv: '^16.4.7', express: '^4.21.2', 'node-telegram-bot-api': '^0.66.0' } }, null, 2));
  zip.file('.gitignore', 'node_modules/\n.env\n.DS_Store\n');
  zip.file('.env.example', 'BOT_TOKEN=вставьте_токен_от_BotFather\nWEBAPP_URL=https://your-domain.com\nPORT=3000\n');
  if (token.trim()) zip.file('.env', `BOT_TOKEN=${token.trim()}\nWEBAPP_URL=https://your-domain.com\nPORT=3000\n`);
  zip.file('bot/index.js', `import 'dotenv/config';
import express from 'express';
import TelegramBot from 'node-telegram-bot-api';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const port = Number(process.env.PORT || 3000);
const webappUrl = process.env.WEBAPP_URL;
const token = process.env.BOT_TOKEN;
if (!token || token.includes('вставьте_токен')) throw new Error('Укажите BOT_TOKEN в .env');
if (!webappUrl || !webappUrl.startsWith('https://')) throw new Error('Укажите публичный HTTPS WEBAPP_URL в .env');
app.use(express.static(path.join(__dirname, '../miniapp')));
app.get('/api/health', (_req, res) => res.json({ ok: true }));
app.listen(port, () => console.log('Game server listening on port ' + port));
const bot = new TelegramBot(token, { polling: true });
bot.onText(/\\/start/, (msg) => {
  const keyboard = { inline_keyboard: [[{ text: ${JSON.stringify(button)}, web_app: { url: webappUrl } }]] };
  bot.sendMessage(msg.chat.id, ${JSON.stringify(welcome)}, { reply_markup: keyboard });
});
bot.on('polling_error', error => console.error('Telegram polling error:', error.message));
console.log('Telegram game bot is running');
`);
  zip.file('miniapp/index.html', `<!doctype html>
<html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="${accent}"><title>${safe(title)}</title><link rel="stylesheet" href="./style.css"></head>
<body><main class="app"><header><span class="logo">🎮</span><span class="tag">TELEGRAM MINI GAME</span></header><div class="game-heading"><span class="eyebrow">${safe(gameById(project.template).title)}</span><h1>${safe(title)}</h1><p>${safe(copy)}</p></div><div id="game-surface" aria-live="polite"></div><footer>Игра запущена внутри Telegram</footer></main><script src="https://telegram.org/js/telegram-web-app.js"></script><script src="./app.js"></script></body></html>`);
  zip.file('miniapp/style.css', `:root{font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#24283b;background:#f4f5fa;--accent:${accent}}*{box-sizing:border-box}body{margin:0;min-height:100vh;padding:18px;background:radial-gradient(ellipse at 100% 0%,color-mix(in srgb,var(--accent) 15%,transparent),transparent 42%),#f4f5fa}.app{max-width:520px;margin:0 auto;padding:12px 8px 30px}header{display:flex;align-items:center;gap:10px;margin-bottom:28px}.logo{width:38px;height:38px;border-radius:13px;background:var(--accent);display:grid;place-items:center;font-size:19px}.tag,.eyebrow{font-size:9px;font-weight:800;letter-spacing:1.4px;color:#8e93a4}.game-heading{margin-bottom:20px}.game-heading h1{font-size:29px;letter-spacing:-1px;margin:6px 0}.game-heading p{font-size:13px;color:#767d90;line-height:1.55;margin:0}.game-meta{display:flex;justify-content:space-between;font-size:10px;font-weight:800;letter-spacing:.7px;color:#898fa1;margin:0 3px 9px}.game-card,.clicker-card{background:#fff;border:1px solid #e8eaf1;border-radius:22px;padding:20px;box-shadow:0 12px 36px #25294a0d}.game-card h2{font-size:19px;line-height:1.35;margin:0 0 17px}.answers{display:grid;gap:9px}.answer{width:100%;text-align:left;padding:13px 14px;background:#fbfbfe;border:1px solid #e8eaf1;border-radius:12px;color:#454a5d;font-weight:700;font-size:14px}.answer:active{border-color:var(--accent);background:color-mix(in srgb,var(--accent) 9%,white)}.result{text-align:center}.result h2{margin:8px 0}.result p{color:#777d8f}.game-emoji{font-size:48px}.primary{width:100%;border:0;border-radius:12px;padding:14px;background:var(--accent);color:white;font-weight:800;font-size:14px}.score{text-align:center;font-weight:900;font-size:47px;color:#30354a}.score small{font-size:18px;color:#9da2b0}.progress{height:9px;background:#eff0f5;border-radius:8px;overflow:hidden;margin:8px 0 22px}.progress i{display:block;height:100%;background:var(--accent);border-radius:8px;transition:width .2s}.tap{width:176px;height:176px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;border:0;border-radius:50%;margin:0 auto;background:linear-gradient(145deg,var(--accent),color-mix(in srgb,var(--accent) 70%,#31255c));color:white;box-shadow:0 10px 0 color-mix(in srgb,var(--accent) 65%,#22243c);font-size:53px}.tap span{font-size:13px;font-weight:900;letter-spacing:1px}.tap:active{transform:translateY(7px);box-shadow:0 3px 0 color-mix(in srgb,var(--accent) 65%,#22243c)}.clicker-card p{text-align:center;color:#8d92a1;font-size:12px;margin:22px 0 0}.memory-board{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.memory-card{aspect-ratio:1;border:1px solid #e5e7ef;border-radius:17px;background:#fff;color:var(--accent);font-size:29px;font-weight:800;box-shadow:0 5px 15px #20243a0b}.memory-card:not(.shown){background:color-mix(in srgb,var(--accent) 13%,white)}.memory-card.matched{background:#e7f8f0;border-color:#74cba4}footer{text-align:center;color:#9ba0af;font-size:11px;margin-top:18px}`);
  zip.file('miniapp/app.js', generatedGameScript(project.template));
  zip.file('README.md', `# ${title}

Игровой Telegram-бот и Telegram Mini App. Шаблон: **${gameById(project.template).title}**.

## Готово в проекте
- Telegram-бот отвечает на /start и открывает игру кнопкой Web App.
- Mini App содержит работающую игру: квиз со счётом, кликер с целью или карточную игру на память.
- Адаптивный интерфейс и тактильная отдача Telegram (если доступна).

## Запуск
1. Требуется Node.js 20+.
2. Укажите токен BotFather в .env (скопируйте .env.example) и публичный HTTPS адрес игры в WEBAPP_URL.
3. Запустите npm install и npm start.
4. Настройте адрес Mini App в @BotFather и откройте бота командой /start.

Счёт в стартовом шаблоне хранится только в текущей сессии на устройстве. Для рейтингов и синхронизации между игроками добавьте backend, базу данных и серверную проверку Telegram initData. Не публикуйте .env.
`);
  const blob = await zip.generateAsync({ type: 'blob' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `${slug}.zip`; a.click(); URL.revokeObjectURL(a.href);
  toast('ZIP с игрой и ботом скачан');
}

function toast(message) {
  let el = document.querySelector('.toast');
  if (!el) { el = document.createElement('div'); el.className = 'toast'; document.body.append(el); }
  el.innerHTML = `${icon('check', 17)} ${safe(message)}`;
  requestAnimationFrame(() => el.classList.add('show'));
  clearTimeout(window.toastTimer); window.toastTimer = setTimeout(() => el.classList.remove('show'), 2600);
}

render();
