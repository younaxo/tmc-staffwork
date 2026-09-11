package su.twomc.staffwork.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class PlatformScheduler {
    private final Plugin plugin;
    private final boolean folia;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public void runGlobal(Runnable action) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, action);
            return;
        }
        invokeGlobal("execute", new Class<?>[] {Plugin.class, Runnable.class}, plugin, action);
    }

    public void runGlobalDelayed(Runnable action, long ticks) {
        if (!folia) {
            Bukkit.getScheduler().runTaskLater(plugin, action, ticks);
            return;
        }
        invokeGlobal("runDelayed", findGlobalDelayedSignature(), plugin, consumer(action), ticks);
    }

    public void runEntity(Entity entity, Runnable action) {
        runEntityDelayed(entity, action, 1L);
    }

    public void runEntityDelayed(Entity entity, Runnable action, long ticks) {
        if (!folia) {
            Bukkit.getScheduler().runTaskLater(plugin, action, Math.max(1L, ticks));
            return;
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method execute =
                    scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            execute.invoke(scheduler, plugin, action, null, Math.max(1L, ticks));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Не удалось выполнить задачу в планировщике сущности Folia", exception);
        }
    }

    public void runRegion(Location location, Runnable action) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, action);
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            scheduler
                    .getClass()
                    .getMethod("execute", Plugin.class, Location.class, Runnable.class)
                    .invoke(scheduler, plugin, location, action);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось выполнить региональную задачу Folia", unwrap(exception));
        }
    }

    public void runAsync(Runnable action) {
        if (!folia) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, action);
            return;
        }
        invokeAsync("runNow", 2, plugin, consumer(action));
    }

    public void runAsyncDelayed(Runnable action, Duration delay) {
        if (!folia) {
            long ticks = Math.max(1L, delay.toMillis() / 50L);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, action, ticks);
            return;
        }
        invokeAsync("runDelayed", 4, plugin, consumer(action), Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS);
    }

    private void invokeGlobal(String methodName, Class<?>[] signature, Object... arguments) {
        try {
            Object scheduler =
                    Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod(methodName, signature).invoke(scheduler, arguments);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось выполнить глобальную задачу Folia", unwrap(exception));
        }
    }

    private Class<?>[] findGlobalDelayedSignature() {
        try {
            Object scheduler =
                    Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            for (Method method : scheduler.getClass().getMethods()) {
                if (method.getName().equals("runDelayed") && method.getParameterCount() == 3) {
                    return method.getParameterTypes();
                }
            }
            throw new IllegalStateException("Метод runDelayed не найден");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Планировщик Folia недоступен", exception);
        }
    }

    private java.util.function.Consumer<Object> consumer(Runnable action) {
        return ignored -> action.run();
    }

    private void invokeAsync(String name, int parameterCount, Object... arguments) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Method selected = null;
            for (Method method : scheduler.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    selected = method;
                    break;
                }
            }
            if (selected == null) {
                throw new NoSuchMethodException(name);
            }
            selected.invoke(scheduler, arguments);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось выполнить асинхронную задачу Folia", unwrap(exception));
        }
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : exception;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
