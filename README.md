# Botcraft Studio

Мобильный конструктор стартовых проектов «Telegram-бот + Telegram Mini App». Интерфейс на русском, проекты сохраняются локально в браузере, а генератор собирает исходный код в ZIP.

## Запуск в режиме разработки

```bash
npm install
npm run dev
```

Откройте адрес, который покажет Vite. Для production-сборки выполните `npm run build`.

## Сборка Android APK

Проект подготовлен к упаковке через Capacitor. Для APK нужны Android Studio/Android SDK и JDK 21 (в текущем sandbox Android toolchain не установлен).

```bash
npm install
npm run android:add       # один раз создать android/
npm run android:sync      # собрать web assets и синхронизировать с Android
npm run android:open      # открыть проект в Android Studio
```

В Android Studio выберите **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Debug APK появится в `android/app/build/outputs/apk/debug/`. Для release APK/AAB настройте подпись приложения в Android Studio.

## Что умеет MVP

- шаблоны для магазина, записи и сообщества;
- настройки стартового экрана Mini App и приветствия Telegram-бота;
- предпросмотр, сохранение черновиков в localStorage;
- скачивание ZIP: Node.js bot + Express-сервер + Telegram Mini App;
- опциональная локальная генерация `.env` с токеном бота.

Сгенерированные шаблоны — исходная основа, не готовая система заказов или платежей. Перед публикацией добавьте собственную бизнес-логику, разместите Mini App на публичном HTTPS-сервере и настройте домен через @BotFather. Не загружайте `.env` с токеном в Git.
