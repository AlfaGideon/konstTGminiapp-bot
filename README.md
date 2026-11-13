# Botcraft Studio

Мобильный конструктор Telegram-ботов и игровых Mini Apps. В приложение упаковывается Android APK через Capacitor; сборка APK выполняется в GitHub Actions.

## Игровые шаблоны

- **Квиз** — вопросы, варианты ответов, очки и экран результата.
- **Кликер** — тапы, счётчик заряда, цель и новый раунд.
- **Найди пару** — перемешанные карточки, ходы, совпадения и рестарт.

Игры можно запускать прямо в живом предпросмотре конструктора. Переключатель **Бот / Игра** меняет экран Telegram-чата и Mini App; игровые кнопки в preview интерактивны. Экспорт создаёт ZIP с работающей браузерной игрой, Express-сервером и Telegram-ботом на long polling.

## Разработка

```bash
npm ci
npm run dev
npm run build
```

## Отладочный APK

На каждый push в `arena/**` GitHub Actions собирает debug APK. Артефакт появляется в запуске workflow `Build Android APK`.

## OTA-релизы Android

Android не разрешает стороннему приложению тихо установить APK без подтверждения пользователя. Botcraft может проверять GitHub Releases, открыть загрузку свежего APK, после чего Android покажет системное подтверждение установки. Для непрерывного обновления все релизы должны быть подписаны **одним и тем же приватным ключом**.

Подписанный release workflow находится в `.github/workflows/android-release.yml`. Он запускается push-ем git-тега формата `v1.0.1`, подписывает APK и публикует `botcraft-studio.apk` в GitHub Releases. В настройках репозитория → **Settings → Secrets and variables → Actions** должны быть настроены секреты:

- `ANDROID_KEYSTORE_BASE64` — base64 от PKCS#12 signing keystore;
- `ANDROID_KEYSTORE_PASSWORD` — пароль keystore;
- `ANDROID_KEY_ALIAS` — alias ключа;
- `ANDROID_KEY_PASSWORD` — пароль ключа.

`GITHUB_TOKEN` для публикации релиза выдаётся GitHub Actions автоматически, вручную создавать его не нужно. Сохраните резервную копию signing keystore и пароль в надёжном менеджере паролей: без прежнего ключа Android не примет обновление поверх установленной версии.

Первый signed release использует отдельный ключ от debug APK. Поэтому уже установленную debug-версию нужно удалить и установить первый release APK вручную; следующие signed-релизы можно будет устанавливать поверх предыдущих. Для публикации новой версии увеличьте tag, например `v1.0.2`.

## Сборка Android локально

Нужны Node.js 22, Android Studio/Android SDK и JDK 21:

```bash
npm ci
npm run android:add       # один раз создать android/
npm run android:sync
cd android && ./gradlew assembleDebug
```

Для игры, которая будет работать с реальными рейтингами или мультиплеером, добавьте backend и серверную проверку Telegram `initData`; данные стартовых демо-игр пока хранятся только в текущей сессии.
