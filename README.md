# TMC StaffWork

`twomc.su StaffWork` — плагин управления персоналом и учёта рабочего времени сотрудников Minecraft-сервера. Техническое имя проекта — `tmc-staffwork`, имя плагина — `TMCStaffWork`.

Текущая версия: **0.1**.

## Возможности

- сотрудники идентифицируются по UUID; ник хранится только как последнее известное имя;
- внутренние ранги, включение и отключение учётной записи;
- рабочие сессии и статусы `WORKING`, `AFK`, `OFF_DUTY`, `BREAK`, `MEETING`, `TRAINING`;
- статистика за сегодня, текущую неделю, месяц и всё время;
- интервальная модель времени без ежесекундных записей в БД;
- YAML, SQLite, H2 и MySQL/MariaDB через единый интерфейс;
- асинхронные SQL- и HTTP-операции;
- Folia-aware планировщик для глобальных задач и операций с игроками;
- мягкая интеграция PlaceholderAPI;
- отключённая по умолчанию Telegram-интеграция с polling, одноразовыми кодами и уведомлениями;
- MiniMessage-локализация и строгие server-side permissions.

## Совместимость

Подтверждено сборкой, автоматическими тестами и реальным запуском:

- Java 17 и Java 21;
- Paper 1.20.1 build 196 на Java 17;
- Paper 1.21.11 build 132 на Java 21;
- Folia 1.21.11 build 14 на Java 21, включая определение Folia scheduler path;
- Paper API 1.20.1 как минимальная compile-time база без NMS;
- SQLite, H2 и YAML на уровне интеграционных тестов хранилища.

Архитектурно поддерживаются, но требуют runtime-проверки на конкретной сборке сервера:

- промежуточные Minecraft 1.20.2–1.21.10;
- Spigot и совместимые ядра, использующие Bukkit/Spigot API;
- другие сборки Folia 1.20+;
- MySQL 8+ и MariaDB 10.6+.

`PaperSpigot` и `UniversalSpigot` не имеют отдельной compile-time цели: совместимость обеспечивается только через стандартный Bukkit API и должна проверяться на конкретном форке. Код не использует NMS или CraftBukkit. Новые версии после 1.21.11 ожидаемо совместимы при сохранении Bukkit API, но не заявлены как проверенные.

Для Minecraft 1.20–1.20.4 плагин собран с bytecode Java 17. Java-версия самого сервера определяется его ядром; современные Paper 1.20–1.21.11 рекомендуют Java 21.

## Установка и первый запуск

1. Скачайте `TMCStaffWork-0.1.jar` из GitHub Release или артефакта Actions.
2. Поместите JAR в `plugins/` и запустите сервер.
3. Остановите сервер и проверьте `plugins/TMCStaffWork/config.yml`.
4. По умолчанию используется SQLite: `plugins/TMCStaffWork/staffwork.db`.
5. Запустите сервер и выполните `/sw version` и `/sw help`.

PlaceholderAPI не обязателен. Telegram выключен и без токена не выполняет сетевых запросов.

## Команды

- `/staffwork help` — справка;
- `/staffwork info [игрок]` — карточка сотрудника;
- `/staffwork list` — список сотрудников;
- `/staffwork add <онлайн-игрок|UUID> [ранг]` — добавление;
- `/staffwork remove <игрок>` — удаление учётной записи;
- `/staffwork rank set <игрок> <ранг>` — изменение ранга;
- `/staffwork enable|disable <игрок>` — включение или отключение;
- `/staffwork status <статус>` — изменение своего статуса;
- `/staffwork status set <игрок> <статус>` — административное изменение;
- `/staffwork start` и `/staffwork stop` — управление своей сессией;
- `/staffwork stats [игрок] [today|week|month|all]` — статистика;
- `/staffwork telegram link|unlink` — привязка Telegram;
- `/staffwork reload` — безопасная перезагрузка сообщений и изменяемых настроек;
- `/staffwork version` — версия и платформа.

Короткий алиас: `/sw`. Новый офлайн-игрок добавляется по UUID. Ник не используется как постоянный идентификатор, а потенциально блокирующий сетевой поиск профиля не выполняется.

## Permissions

Все права начинаются с `tmc.staffwork.`. Родительские группы `tmc.staffwork.user`, `tmc.staffwork.staff`, `tmc.staffwork.moderator`, `tmc.staffwork.admin` и `tmc.staffwork.*` объявлены в `plugin.yml`. Обычным игрокам права по умолчанию не выдаются; административные группы доступны только операторам, пока владелец не настроит плагин прав.

Основные узлы:

- `tmc.staffwork.command.help`, `tmc.staffwork.command.version`;
- `tmc.staffwork.staff.list`, `tmc.staffwork.staff.info.self`, `tmc.staffwork.staff.info.others`;
- `tmc.staffwork.staff.add`, `tmc.staffwork.staff.remove`, `tmc.staffwork.staff.rank.set`;
- `tmc.staffwork.status.set.self`, `tmc.staffwork.status.set.others`;
- `tmc.staffwork.session.start.self`, `tmc.staffwork.session.stop.self`, `tmc.staffwork.session.manage.others`;
- `tmc.staffwork.statistics.view.self`, `tmc.staffwork.statistics.view.others`;
- `tmc.staffwork.admin.reload`, `tmc.staffwork.admin.database.migrate`.

Каждая команда повторно проверяет permission на стороне сервера.

## Хранилища

Настройка находится в секции `storage`. Смена типа хранилища через reload не применяется и требует перезапуска.

- `YAML` — атомарная запись во временный файл с последующей заменой;
- `SQLITE` — режим по умолчанию;
- `H2` — локальная встроенная БД;
- `MYSQL` — HikariCP и prepared statements, подходит для общей БД нескольких серверов.

Пример MySQL/MariaDB:

```yaml
general:
  server-id: lobby-1
storage:
  type: MYSQL
  mysql:
    host: db.internal.example
    port: 3306
    database: tmc_staffwork
    username: staffwork
    password: 'задайте-только-на-сервере'
```

Не публикуйте рабочий `config.yml`. Пароли не выводятся в журнал. Схема версионируется таблицей `tmc_schema_history`, создаётся автоматически и содержит индексы по сотруднику и времени.

## Рабочее время и автоматизация

Время хранится в UTC. `general.time-zone` используется только для границ календарных периодов и отображения. Неделя начинается в понедельник.

По умолчанию учитываются `WORKING`, `MEETING`, `TRAINING`; статусы `AFK`, `OFF_DUTY`, `BREAK` не учитываются. Список задаётся в `work-time.counted-statuses`.

Секции `automation.join`, `automation.quit` и `automation.afk` управляют автозапуском, статусом входа, завершением/паузой при выходе и безопасным AFK по стандартным действиям Bukkit. Пакеты игроков не перехватываются.

При штатной остановке открытые сессии закрываются. После аварийного завершения оставшиеся открытые сессии закрываются при следующем запуске по времени восстановления; точный момент падения без внешнего heartbeat определить невозможно.

## PlaceholderAPI

Идентификатор расширения: `tmcstaff`.

- `%tmcstaff_staff_status%`;
- `%tmcstaff_staff_status_color%`;
- `%tmcstaff_staff_is_working%`;
- `%tmcstaff_staff_work_time_today%`;
- `%tmcstaff_staff_work_time_week%`;
- `%tmcstaff_staff_work_time_month%`;
- `%tmcstaff_staff_total_work_time%`;
- `%tmcstaff_staff_current_session_time%`.

Значения читаются из асинхронно обновляемого кеша и не обращаются к БД в потоке PlaceholderAPI. `status_color` возвращает legacy-префикс вида `&a`, настраиваемый в `placeholder-api.status-colors`. Сообщения самого плагина используют MiniMessage. Формат `%tmc.staff_...%` не поддерживается: точка не является надёжным идентификатором расширения PlaceholderAPI.

## Telegram

1. Создайте бота через BotFather.
2. На сервере задайте `telegram.enabled: true` и `telegram.bot-token`.
3. Перезапустите сервер.
4. Игрок выполняет `/sw telegram link` и отправляет боту `/link КОД`.

Код действует ограниченное время, хранится только как SHA-256-хеш с непубличной солью процесса и защищён rate limit. Polling и HTTP выполняются вне серверных потоков; повторные попытки ограничены и используют backoff. Привязки хранятся в `telegram-links.yml`. Доступные события и шаблоны задаются в конфигурации.

## Резервное копирование и обновление

Перед обновлением остановите сервер и сохраните каталог `plugins/TMCStaffWork/`. Для SQLite копируйте `staffwork.db` только после остановки. YAML-хранилище создаёт резервную копию перед миграцией формата. Перенос между разными типами хранилища в 0.1 автоматически не выполняется.

После замены JAR запустите сервер и проверьте журнал миграций, `/sw version` и `/sw stats`. Версия конфигурации обновляется безопасно добавлением значений по умолчанию; конфиг из более новой версии намеренно отклоняется.

## Сборка из исходников

Требуется JDK 17 или 21:

```bash
./gradlew clean build
```

Итоговый файл: `build/libs/TMCStaffWork-0.1.jar`. Отдельные проверки: `test`, `spotlessCheck`, `checkstyleMain`, `checkstyleTest`, `verifyJar`.

## Известные ограничения 0.1

- автоматический импорт данных между YAML/SQLite/H2/MySQL отсутствует;
- MySQL/MariaDB покрыты общей JDBC-реализацией, но для полноценного теста нужен внешний сервер БД;
- кросс-серверные мгновенные уведомления об изменениях общей БД не реализованы;
- runtime matrix всех сторонних форков невозможно подтвердить одной компиляцией;
- при аварии время до следующего запуска может попасть в незакрытый рабочий интервал.

Подробное описание параметров находится в [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Разработка, безопасность и лицензия

Автор: [younaxo](https://github.com/younaxo/). Сообщения об уязвимостях принимаются по правилам [SECURITY.md](SECURITY.md), вклад — по [CONTRIBUTING.md](CONTRIBUTING.md).

В репозитории изначально не было лицензии, а владелец не выбрал её явно. Поэтому отдельная лицензия намеренно не добавлена. До выбора лицензии стандартные авторские права сохраняются за владельцем; это не является разрешением на копирование или распространение.
