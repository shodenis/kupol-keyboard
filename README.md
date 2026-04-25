# Kupol Keyboard

Android IME (клавиатура) с переводом через выбранный LLM / Google.

## Сборка локально

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # путь к SDK
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/`.

## Выложить на GitHub

### Отдельный репозиторий (только эта папка)

Из каталога **`kupol-keyboard`** (скопируйте папку вне монорепо или используйте `git subtree`):

```bash
cd kupol-keyboard
git init
git add .
git commit -m "Initial commit: Kupol Keyboard"
```

Создайте пустой репозиторий на GitHub (веб → New repository), затем:

```bash
git remote add origin https://github.com/YOUR_USER/kupol-keyboard.git
git branch -M main
git push -u origin main
```

(или `gh repo create ... --push`, если установлен [GitHub CLI](https://cli.github.com/).)

### Компиляция на GitHub (CI)

В репозитории уже есть [`.github/workflows/android.yml`](.github/workflows/android.yml): на **push** и **pull request** в ветки **`main`** / **`master`** запускается **`./gradlew assembleDebug`**.

- Вкладка **Actions** в репозитории покажет статус сборки.
- В успешном прогоне артефакт **`app-debug`** — готовый debug-APK (**Artifacts** у job).

Секреты для подписи release-APK в CI не настроены — сейчас только **debug**-сборка.

### Если `kupol-keyboard` лежит внутри большого репозитория

Workflow из **`.github/workflows/`** должен выполняться из **корня того репозитория**, где лежит Gradle. Либо вынесите `kupol-keyboard` в отдельный репозиторий (как выше), либо скопируйте `android.yml` в **корневой** `.github/workflows/` и добавьте в job:

```yaml
defaults:
  run:
    working-directory: kupol-keyboard
```
