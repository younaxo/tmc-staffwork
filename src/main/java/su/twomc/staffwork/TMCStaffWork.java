package su.twomc.staffwork;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import su.twomc.staffwork.command.StaffWorkCommand;
import su.twomc.staffwork.config.ConfigManager;
import su.twomc.staffwork.config.PluginSettings;
import su.twomc.staffwork.listener.StaffActivityListener;
import su.twomc.staffwork.message.MessageService;
import su.twomc.staffwork.placeholder.PlaceholderCache;
import su.twomc.staffwork.placeholder.PlaceholderController;
import su.twomc.staffwork.placeholder.PlaceholderHook;
import su.twomc.staffwork.platform.PlatformScheduler;
import su.twomc.staffwork.service.StaffService;
import su.twomc.staffwork.service.WorkSessionService;
import su.twomc.staffwork.storage.JdbcStaffRepository;
import su.twomc.staffwork.storage.StaffRepository;
import su.twomc.staffwork.storage.StorageType;
import su.twomc.staffwork.storage.YamlStaffRepository;
import su.twomc.staffwork.telegram.TelegramService;

public final class TMCStaffWork extends JavaPlugin {
    private final AtomicBoolean ready = new AtomicBoolean();
    private final Clock clock = Clock.systemUTC();
    private ExecutorService databaseExecutor;
    private ScheduledExecutorService integrationExecutor;
    private PlatformScheduler scheduler;
    private ConfigManager configManager;
    private PluginSettings settings;
    private MessageService messages;
    private StaffRepository repository;
    private StaffService staffService;
    private WorkSessionService workService;
    private PlaceholderCache placeholderCache;
    private PlaceholderController placeholderController = PlaceholderController.NONE;
    private TelegramService telegram;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        databaseExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "TMCStaffWork-Database");
            thread.setDaemon(true);
            return thread;
        });
        integrationExecutor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "TMCStaffWork-Integration");
            thread.setDaemon(true);
            return thread;
        });
        scheduler = new PlatformScheduler(this);
        configManager = new ConfigManager(this);
        settings = configManager.load();
        messages = new MessageService(this);

        PluginCommand command = Objects.requireNonNull(getCommand("staffwork"), "Команда staffwork не объявлена");
        StaffWorkCommand handler = new StaffWorkCommand(this, messages);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        repository = createRepository(settings);
        java.util.concurrent.CompletableFuture.runAsync(repository::initialize, databaseExecutor)
                .thenRunAsync(this::finishEnable, runnable -> scheduler.runGlobal(runnable))
                .exceptionally(error -> {
                    getLogger().log(Level.SEVERE, "TMC StaffWork не запущен: хранилище недоступно", error);
                    scheduler.runGlobal(() -> Bukkit.getPluginManager().disablePlugin(this));
                    return null;
                });
    }

    private void finishEnable() {
        staffService = new StaffService(repository, databaseExecutor, clock, settings.ranks());
        workService = new WorkSessionService(
                repository,
                databaseExecutor,
                clock,
                settings.serverId(),
                settings.countedStatuses(),
                settings.zoneId());
        placeholderCache = new PlaceholderCache(workService, clock);
        telegram = new TelegramService(
                settings.telegram(), getDataFolder().toPath(), integrationExecutor, clock, getLogger());
        telegram.start();
        Bukkit.getPluginManager().registerEvents(new StaffActivityListener(this, clock), this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderController = PlaceholderHook.register(this, placeholderCache, settings);
        }
        workService.recoverOpenSessions().thenAccept(count -> {
            if (count > 0) {
                getLogger().warning("После предыдущей остановки завершено открытых сессий: " + count);
            }
        });
        ready.set(true);
        getLogger()
                .info("TMC StaffWork v" + getDescription().getVersion() + " готов к работе; хранилище: "
                        + settings.database().type() + ", Folia: " + scheduler.isFolia());
    }

    private StaffRepository createRepository(PluginSettings current) {
        return current.database().type() == StorageType.YAML
                ? new YamlStaffRepository(getDataFolder().toPath())
                : new JdbcStaffRepository(current.database());
    }

    public boolean reloadSafeSettings() {
        StorageType oldType = settings.database().type();
        PluginSettings reloaded = configManager.load();
        settings = reloaded;
        messages.reload();
        staffService.updateAllowedRanks(reloaded.ranks());
        workService.updateSettings(reloaded.countedStatuses(), reloaded.zoneId());
        placeholderController.updateColors(reloaded.statusColors());
        telegram.close();
        telegram = new TelegramService(
                reloaded.telegram(), getDataFolder().toPath(), integrationExecutor, clock, getLogger());
        telegram.start();
        return oldType != reloaded.database().type();
    }

    @Override
    public void onDisable() {
        ready.set(false);
        if (telegram != null) {
            telegram.close();
        }
        if (workService != null) {
            try {
                workService.recoverOpenSessions().get(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Не все активные сессии удалось завершить при остановке", exception);
            }
        }
        if (repository != null) {
            repository.close();
        }
        if (messages != null) {
            messages.close();
        }
        shutdown(databaseExecutor);
        shutdown(integrationExecutor);
    }

    private static void shutdown(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public boolean isReady() {
        return ready.get();
    }

    public PlatformScheduler scheduler() {
        return scheduler;
    }

    public PluginSettings settings() {
        return settings;
    }

    public StaffService staffService() {
        return staffService;
    }

    public WorkSessionService workService() {
        return workService;
    }

    public PlaceholderCache placeholderCache() {
        return placeholderCache;
    }

    public TelegramService telegram() {
        return telegram;
    }
}
