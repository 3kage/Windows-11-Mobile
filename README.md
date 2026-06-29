# Windows 11 Mobile

Android-додаток для запуску Windows 11 у user-space (PRoot + QEMU) без root-прав.

## Останній реліз

**v1.3.3:** https://github.com/3kage/Windows-11-Mobile/releases/latest

## Встановлення / оновлення

### Якщо встановлення каже «конфліктує з наявним пакетом»

Це стосується версій **v1.3.2 і старіших** — вони мали інший підпис APK.

**v1.3.3** має новий ID пакета (`com.w11mobile.windows11`) і **встановлюється окремо** без видалення старого додатку:

1. Встановіть **v1.3.3** з [Releases](https://github.com/3kage/Windows-11-Mobile/releases)
2. Переконайтесь, що новий додаток працює
3. Видаліть старий «Windows 11 Mobile» (v1.3.2 або раніше) вручну

### Після v1.3.3

Усі наступні версії оновлюються поверх v1.3.3 **без видалення** (однаковий підпис і ID пакета).

## Використання з Win11 ARM64 ISO

1. **Локальний файл** → оберіть `.iso` (наприклад `Win11_25H2_Ukrainian_Arm64_v2.iso`)
2. Архітектура: **ARM64**
3. **Ініціалізувати Windows 11**
4. **Запустити Windows 11**

## Збірка

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
