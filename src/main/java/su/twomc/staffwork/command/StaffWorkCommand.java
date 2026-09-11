package su.twomc.staffwork.command;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.twomc.staffwork.TMCStaffWork;
import su.twomc.staffwork.message.MessageService;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.StatisticsPeriod;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.service.DurationFormatter;
import su.twomc.staffwork.service.OperationResult;

public final class StaffWorkCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = List.of(
            "help",
            "info",
            "list",
            "add",
            "remove",
            "rank",
            "enable",
            "disable",
            "status",
            "start",
            "stop",
            "stats",
            "telegram",
            "reload",
            "version");

    private final TMCStaffWork plugin;
    private final MessageService messages;

    public StaffWorkCommand(TMCStaffWork plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!plugin.isReady() && (args.length == 0 || !args[0].equalsIgnoreCase("version"))) {
            messages.send(sender, "error.not-ready");
            return true;
        }
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (subcommand) {
                case "help" -> help(sender);
                case "version" -> version(sender);
                case "list" -> list(sender);
                case "info" -> info(sender, args);
                case "add" -> add(sender, args);
                case "remove" -> remove(sender, args);
                case "rank" -> rank(sender, args);
                case "enable" -> setEnabled(sender, args, true);
                case "disable" -> setEnabled(sender, args, false);
                case "start" -> start(sender);
                case "stop" -> stop(sender);
                case "status" -> status(sender, args);
                case "stats" -> stats(sender, args);
                case "telegram" -> telegram(sender, args);
                case "reload" -> reload(sender);
                default -> messages.send(sender, "error.invalid-arguments");
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка обработки команды /" + label, exception);
            messages.send(sender, "error.internal");
        }
        return true;
    }

    private void help(CommandSender sender) {
        if (require(sender, Permissions.HELP)) {
            messages.send(sender, "command.help");
        }
    }

    private void version(CommandSender sender) {
        if (require(sender, Permissions.VERSION)) {
            messages.send(
                    sender,
                    "command.version",
                    "version",
                    plugin.getDescription().getVersion(),
                    "platform",
                    plugin.scheduler().isFolia() ? "Folia" : Bukkit.getName());
        }
    }

    private void list(CommandSender sender) {
        if (!require(sender, Permissions.LIST)) {
            return;
        }
        complete(sender, plugin.staffService().list(), members -> {
            messages.send(sender, "staff.list-header", "count", Integer.toString(members.size()));
            members.forEach(member -> messages.send(
                    sender,
                    "staff.list-item",
                    "player",
                    member.lastKnownName(),
                    "rank",
                    member.rank(),
                    "status",
                    member.enabled() ? "активен" : "отключён"));
        });
    }

    private void info(CommandSender sender, String[] args) {
        UUID self = playerUuid(sender);
        if (args.length == 1) {
            if (self == null || !require(sender, Permissions.INFO_SELF)) {
                return;
            }
            showInfo(sender, plugin.staffService().find(self));
            return;
        }
        if (args.length != 2 || !require(sender, Permissions.INFO_OTHERS)) {
            if (args.length != 2) {
                messages.send(sender, "error.invalid-arguments");
            }
            return;
        }
        showInfo(sender, resolveStaff(args[1]));
    }

    private void showInfo(CommandSender sender, CompletableFuture<Optional<StaffMember>> future) {
        complete(sender, future, result -> {
            if (result.isEmpty()) {
                messages.send(sender, "staff.not-found");
                return;
            }
            StaffMember member = result.get();
            messages.send(
                    sender,
                    "staff.info",
                    "player",
                    member.lastKnownName(),
                    "uuid",
                    member.uuid().toString(),
                    "rank",
                    member.rank(),
                    "enabled",
                    member.enabled() ? "да" : "нет",
                    "added",
                    formatInstant(member.addedAt(), plugin.settings().zoneId()));
        });
    }

    private void add(CommandSender sender, String[] args) {
        if (!require(sender, Permissions.ADD)) {
            return;
        }
        if (args.length < 2 || args.length > 3) {
            messages.send(sender, "error.invalid-arguments");
            return;
        }
        String rank = args.length == 3 ? args[2] : "staff";
        Player online = Bukkit.getPlayerExact(args[1]);
        Optional<UUID> explicitUuid = CommandInput.uuid(args[1]);
        if (online == null && explicitUuid.isEmpty()) {
            messages.send(sender, "error.player-not-found");
            return;
        }
        UUID uuid = online == null ? explicitUuid.orElseThrow() : online.getUniqueId();
        String name = online == null ? "UUID-" + uuid.toString().substring(0, 8) : online.getName();
        UUID addedBy = playerUuid(sender);
        completeResult(sender, plugin.staffService().add(uuid, name, rank, addedBy));
    }

    private void remove(CommandSender sender, String[] args) {
        if (!require(sender, Permissions.REMOVE) || !exactArgs(sender, args, 2)) {
            return;
        }
        withStaff(sender, args[1], member -> {
            plugin.workService().stop(member.uuid()).exceptionally(error -> null);
            completeResult(sender, plugin.staffService().remove(member.uuid()));
        });
    }

    private void rank(CommandSender sender, String[] args) {
        if (!require(sender, Permissions.RANK_SET)) {
            return;
        }
        if (args.length != 4 || !args[1].equalsIgnoreCase("set")) {
            messages.send(sender, "error.invalid-arguments");
            return;
        }
        withStaff(
                sender,
                args[2],
                member -> completeResult(sender, plugin.staffService().setRank(member.uuid(), args[3])));
    }

    private void setEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (!require(sender, Permissions.ENABLED_SET) || !exactArgs(sender, args, 2)) {
            return;
        }
        withStaff(
                sender,
                args[1],
                member -> completeResult(sender, plugin.staffService().setEnabled(member.uuid(), enabled)));
    }

    private void start(CommandSender sender) {
        UUID uuid = requirePlayer(sender, Permissions.SESSION_START);
        if (uuid != null) {
            completeWorkResult(sender, uuid, plugin.workService().start(uuid), "session-start");
        }
    }

    private void stop(CommandSender sender) {
        UUID uuid = requirePlayer(sender, Permissions.SESSION_STOP);
        if (uuid != null) {
            completeWorkResult(sender, uuid, plugin.workService().stop(uuid), "session-stop");
        }
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length == 2) {
            UUID uuid = requirePlayer(sender, Permissions.STATUS_SELF);
            WorkStatus parsed = WorkStatus.parse(args[1]).orElse(null);
            if (uuid == null) {
                return;
            }
            if (parsed == null) {
                messages.send(sender, "error.invalid-status");
                return;
            }
            completeWorkResult(sender, uuid, plugin.workService().changeStatus(uuid, parsed), null);
            return;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            if (!require(sender, Permissions.STATUS_OTHERS)) {
                return;
            }
            WorkStatus parsed = WorkStatus.parse(args[3]).orElse(null);
            if (parsed == null) {
                messages.send(sender, "error.invalid-status");
                return;
            }
            withStaff(
                    sender,
                    args[2],
                    member -> completeWorkResult(
                            sender, member.uuid(), plugin.workService().changeStatus(member.uuid(), parsed), null));
            return;
        }
        messages.send(sender, "error.invalid-arguments");
    }

    private void stats(CommandSender sender, String[] args) {
        StatisticsPeriod period = StatisticsPeriod.TODAY;
        CompletableFuture<Optional<StaffMember>> staffFuture;
        if (args.length == 1
                || (args.length == 2 && StatisticsPeriod.parse(args[1]).isPresent())) {
            UUID uuid = requirePlayer(sender, Permissions.STATS_SELF);
            if (uuid == null) {
                return;
            }
            if (args.length == 2) {
                period = StatisticsPeriod.parse(args[1]).orElseThrow();
            }
            staffFuture = plugin.staffService().find(uuid);
        } else if (args.length == 2 || args.length == 3) {
            if (!require(sender, Permissions.STATS_OTHERS)) {
                return;
            }
            staffFuture = resolveStaff(args[1]);
            if (args.length == 3) {
                Optional<StatisticsPeriod> parsed = StatisticsPeriod.parse(args[2]);
                if (parsed.isEmpty()) {
                    messages.send(sender, "error.invalid-period");
                    return;
                }
                period = parsed.get();
            }
        } else {
            messages.send(sender, "error.invalid-arguments");
            return;
        }
        StatisticsPeriod selected = period;
        complete(sender, staffFuture, result -> {
            if (result.isEmpty()) {
                messages.send(sender, "staff.not-found");
                return;
            }
            StaffMember member = result.get();
            complete(
                    sender,
                    plugin.workService().statistics(member.uuid(), selected),
                    statistics -> messages.send(
                            sender,
                            "statistics.result",
                            "player",
                            member.lastKnownName(),
                            "time",
                            DurationFormatter.format(statistics.countedTime()),
                            "current",
                            DurationFormatter.format(statistics.currentSessionTime())));
        });
    }

    private void telegram(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.players-only");
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("link")) {
            if (!require(sender, Permissions.TELEGRAM_LINK)) {
                return;
            }
            plugin.telegram()
                    .createLinkCode(player.getUniqueId())
                    .ifPresentOrElse(
                            code -> messages.send(
                                    sender,
                                    "telegram.code",
                                    "code",
                                    code.value(),
                                    "seconds",
                                    Integer.toString(code.ttlSeconds())),
                            () -> messages.send(
                                    sender, plugin.telegram().isEnabled() ? "error.rate-limit" : "telegram.disabled"));
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("unlink")) {
            if (require(sender, Permissions.TELEGRAM_UNLINK)) {
                plugin.telegram().unlink(player.getUniqueId());
                messages.send(sender, "telegram.unlinked");
            }
            return;
        }
        messages.send(sender, "error.invalid-arguments");
    }

    private void reload(CommandSender sender) {
        if (!require(sender, Permissions.RELOAD)) {
            return;
        }
        boolean restartRequired = plugin.reloadSafeSettings();
        messages.send(sender, restartRequired ? "command.restart-required" : "command.reloaded");
    }

    private void completeWorkResult(
            CommandSender sender,
            UUID uuid,
            CompletableFuture<OperationResult<WorkSession>> future,
            @Nullable String telegramEvent) {
        complete(sender, future, result -> {
            sendResult(sender, result);
            if (result.success()) {
                plugin.placeholderCache().refresh(uuid);
                if (telegramEvent != null) {
                    plugin.telegram().notifyEvent(telegramEvent, sender.getName());
                }
            }
        });
    }

    private <T> void completeResult(CommandSender sender, CompletableFuture<OperationResult<T>> future) {
        complete(sender, future, result -> sendResult(sender, result));
    }

    private void sendResult(CommandSender sender, OperationResult<?> result) {
        Object value = result.value();
        if (value instanceof StaffMember member) {
            messages.send(sender, result.messageKey(), "player", member.lastKnownName(), "rank", member.rank());
        } else if (value instanceof WorkSession session) {
            messages.send(
                    sender,
                    result.messageKey(),
                    "status",
                    session.currentStatus().displayName());
        } else {
            messages.send(sender, result.messageKey());
        }
    }

    private void withStaff(CommandSender sender, String input, java.util.function.Consumer<StaffMember> action) {
        complete(sender, resolveStaff(input), result -> {
            if (result.isEmpty()) {
                messages.send(sender, "staff.not-found");
            } else {
                action.accept(result.get());
            }
        });
    }

    private CompletableFuture<Optional<StaffMember>> resolveStaff(String input) {
        Optional<UUID> uuid = CommandInput.uuid(input);
        return uuid.map(value -> plugin.staffService().find(value))
                .orElseGet(() -> plugin.staffService().findByName(input));
    }

    private <T> void complete(
            CommandSender sender, CompletableFuture<T> future, java.util.function.Consumer<T> action) {
        future.whenComplete((result, error) -> reply(sender, () -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Асинхронная операция команды завершилась ошибкой", error);
                messages.send(sender, "error.internal");
            } else {
                action.accept(result);
            }
        }));
    }

    private void reply(CommandSender sender, Runnable action) {
        if (sender instanceof Player player && player.isOnline()) {
            plugin.scheduler().runEntity(player, action);
        } else {
            plugin.scheduler().runGlobal(action);
        }
    }

    private boolean require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "error.no-permission");
            return false;
        }
        return true;
    }

    private UUID requirePlayer(CommandSender sender, String permission) {
        if (!require(sender, permission)) {
            return null;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.players-only");
            return null;
        }
        return player.getUniqueId();
    }

    private static UUID playerUuid(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    private boolean exactArgs(CommandSender sender, String[] args, int count) {
        if (args.length != count) {
            messages.send(sender, "error.invalid-arguments");
            return false;
        }
        return true;
    }

    private static String formatInstant(Instant instant, ZoneId zone) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(instant);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT_COMMANDS.stream(), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            return filter(Arrays.stream(WorkStatus.values()).map(Enum::name), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("telegram")) {
            return filter(Stream.of("link", "unlink"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
            return filter(Stream.of("set"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            return onlineNames(args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("rank")) {
            return filter(plugin.settings().ranks().stream(), args[3]);
        }
        if (args.length == 2
                && List.of("info", "remove", "enable", "disable").contains(args[0].toLowerCase(Locale.ROOT))) {
            return onlineNames(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return filter(Stream.concat(Stream.of("today", "week", "month", "all"), onlineNameStream()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("stats")) {
            return filter(Stream.of("today", "week", "month", "all"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("status") && args[1].equalsIgnoreCase("set")) {
            return onlineNames(args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("status") && args[1].equalsIgnoreCase("set")) {
            return filter(Arrays.stream(WorkStatus.values()).map(Enum::name), args[3]);
        }
        return List.of();
    }

    private static List<String> onlineNames(String prefix) {
        return filter(onlineNameStream(), prefix);
    }

    private static Stream<String> onlineNameStream() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName);
    }

    private static List<String> filter(Stream<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(50)
                .toList();
    }
}
