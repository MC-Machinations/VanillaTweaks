/*
 * GNU General Public License v3
 *
 * VanillaTweaks, a performant replacement for the VanillaTweaks datapacks.
 *
 * Copyright (C) 2021 Machine_Maker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package me.machinemaker.vanillatweaks;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

@Singleton
public class VanillaTweaksMetrics {

    private static final int PLUGIN_ID = 8141;

    private final VanillaTweaksConfig config;
    private Metrics metrics;

    @Inject
    VanillaTweaksMetrics(VanillaTweaksConfig config, JavaPlugin plugin) {
        this.config = config;
        if (this.config.metricsEnabled) {
            this.metrics = new Metrics(plugin, PLUGIN_ID);
        }
    }

    public boolean isRunning() {
        return this.metrics != null;
    }

}