package com.github.cinnaio.relations.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class SchedulerUtils {

    private static final boolean IS_FOLIA;

    static {
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
        IS_FOLIA = isFolia;
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            // Folia async scheduler uses time units, assume 50ms per tick
            Bukkit.getAsyncScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), delayTicks * 50, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    public static void runTask(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, (scheduledTask) -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            entity.getScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), null, delayTicks * 50);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    public static java.util.concurrent.CompletableFuture<Boolean> teleport(Entity entity, org.bukkit.Location location) {
        if (IS_FOLIA) {
            return entity.teleportAsync(location);
        } else {
            boolean result = entity.teleport(location);
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        }
    }
}
