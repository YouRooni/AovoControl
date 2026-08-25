<p align="center">
  <img src="assets/logo.png" width="120" height="120" alt="Aovo Control Logo" />
</p>

<h1 align="center">Aovo Control</h1>

<p align="center">
  <b>Современное, быстрое и открытое Android-приложение для управления и настройки электросамокатов на контроллерах Aovo, ViCont, Samik, Benben и ZYD.</b>
</p>

<p align="center">
  <a href="https://github.com/YouRooni/AovoControl/releases"><img src="https://img.shields.io/github/v/release/YouRooni/AovoControl?style=for-the-badge&color=2979FF&label=Release" alt="Release" /></a>
  <a href="https://4pda.to/forum/index.php?showtopic=1125489"><img src="https://img.shields.io/badge/4PDA-Topic-brightgreen?style=for-the-badge&logo=android" alt="4PDA" /></a>
  <a href="https://t.me/YouRooni"><img src="https://img.shields.io/badge/Telegram-@YouRooni-2CA5E0?style=for-the-badge&logo=telegram" alt="Telegram" /></a>
  <a href="https://github.com/YouRooni/AovoControl/blob/master/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="License" /></a>
</p>

<p align="center">
  <b><a href="README.md">Русский</a></b> | <b><a href="README.en.md">English</a></b>
</p>

---

## 🚀 О проекте

**Aovo Control** — это независимый клиент для электросамокатов, созданный как полноценная и гораздо более функциональная замена стоковым китайским приложениям (*AovoPro*, *ViCont*, *Benben* и др.). 

Приложение работает **напрямую по Bluetooth Low Energy (BLE)**, без обязательной регистрации, рекламы, китайских облаков и сбора аналитики.

---

## ✨ Основные возможности

### 📊 Приборная панель и телеметрия
* **Живой спидометр** с плавным отображением текущей скорости, перегрузок и мощности.
* **Детальное состояние батареи:** уровень заряда (%), напряжение в вольтах ($V$) и ток ($A$).
* **Счётчики пробега:** текущая поездка (Trip) и общий одометр (ODO).
* **Быстрое управление:** включение фары, блокировка мотора, круиз-контроль и выбор передач в один тап.

### ⚙️ Настройки и кастомизация езды
* **Раздельные скоростные лимиты:** независимая регулировка максималки для Eco, Drive, Sport, пешеходного режима и 5-й передачи.
* **Старт с нуля (Zero-Start):** отключение необходимости отталкиваться ногой.
* **RGB-подсветка деки (ViCont):** выбор любого цвета из палитры и 5 динамических режимов свечения (переливание, дыхание, статика).
* **Голосовые подсказки и модуль:** управление громкостью, переименование Bluetooth-имени и смена пароля.

### 🧪 Экспертный режим и калибровка
* **Прямой доступ к регистрам контроллера:** токи разряда и рекуперативного торможения, пороги защиты от переразряда, диаметр колёс, число пар полюсов мотора и ШИМ-частота (PWM).
* **Инженерное меню ViCont:** чтение и запись параметров дисплея/мотора в EEPROM.
* **Калибровка мотора:** сохранение и настройка фазового порядка и коэффициентов датчиков Холла.
* **Терминал ручных команд:** отправка любых сырых HEX-пакетов в самокат для исследования протокола.

### 🔄 Прошивка и обновления
* **OTA онлайн-проверка обновлений:** прямая загрузка и сверка прошивок дисплея и контроллера с серверов Vicont Cloud.
* **Локальная прошивка:** безопасная загрузка бинарных файлов прошивки (`.bin`) из памяти устройства.
* **Автоматический бэкап и профили настроек:** сохранение и быстрое переключение пресетов езды.

### 🎨 Дизайн и эргономика
* Полное следование концепции **Material 3 Expressive**.
* Динамические системные цвета **Material You (Monet)** и поддержка глубокой **AMOLED-темы**.
* Плавные пружинные анимации, настраиваемый виброотклик (Haptic Feedback) и секретная пасхалка.

---

## 🛴 Поддерживаемые устройства

* **Aovo / AovoPro** (M365 clone, ES80, Pro и аналоги)
* **ViCont** (контроллеры и дисплеи BD-MAX, 032＆039ZQ, BD1 и др.)
* **Samik / Benben**
* **ZYD / YF** совместимые BLE-контроллеры

---

## 📥 Установка и скачивание

1. Перейдите в раздел **[Releases](https://github.com/YouRooni/AovoControl/releases)** на GitHub или в **[Тему на 4PDA](https://4pda.to/forum/index.php?showtopic=1125489)**.
2. Скачайте свежий `AovoControl-x.x.x.apk`.
3. Установите на устройство под управлением **Android 8.0 (API 26)** или новее.
4. Выдайте разрешение на поиск Bluetooth-устройств (Nearby Devices / Location).

---

## 🛠 Стек технологий

* **Язык:** Kotlin
* **UI:** Jetpack Compose + Material 3 Expressive
* **Архитектура:** Single Activity, Modern Android Architecture (StateFlow, ViewModel, Coroutines)
* **Связь:** Bluetooth Low Energy (BLE GATT), HTTP клиент без тяжелых зависимостей

---

## 👨‍💻 Автор и благодарности

* **Разработчик:** Данил ([@YouRooni](https://t.me/YouRooni))
* **Тема на 4PDA:** [Aovo Control](https://4pda.to/forum/index.php?showtopic=1125489)
* **Исследование протокола:** [@vova7878](https://t.me/vova7878) — за документацию и реверс протокола ViCont
* **Поддержка проекта:** [payRooni.t.me](https://t.me/payRooni) 💖

---

## 📄 Лицензия

Проект распространяется под свободной копилефт-лицензией **GNU General Public License v3.0 (GPLv3)**. Исходный код остаётся полностью открытым: форки и модификации разрешены с обязательным сохранением авторства и открытием производного кода под этой же лицензией.
