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

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import io.papermc.lib.PaperLib;
import me.machinemaker.lectern.BaseConfig;
import me.machinemaker.vanillatweaks.adventure.MiniMessageComponentRenderer;
import me.machinemaker.vanillatweaks.cloud.CloudModule;
import me.machinemaker.vanillatweaks.db.DatabaseModule;
import me.machinemaker.vanillatweaks.db.DatabaseType;
import me.machinemaker.vanillatweaks.integrations.Integrations;
import me.machinemaker.vanillatweaks.modules.ModuleManager;
import me.machinemaker.vanillatweaks.modules.ModuleRegistry;
import me.machinemaker.vanillatweaks.modules.teleportation.homes.Homes;
import me.machinemaker.vanillatweaks.utils.PlayerMapFactory;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static me.machinemaker.vanillatweaks.adventure.Components.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class VanillaTweaks extends JavaPlugin {

    public static boolean RAN_CONFIG_MIGRATIONS = false;

    public static final Component PLUGIN_PREFIX = text().append(text("[", DARK_GRAY)).append(text("VanillaTweaks", BLUE)).append(text("] ", DARK_GRAY)).build();
    public static final Logger LOGGER = LoggerFactory.getLogger();

    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(0);

    static final Set<Locale> SUPPORTED_LOCALES = Set.of(
            Locale.ENGLISH
    );

    @Inject private ModuleManager moduleManager;
    @Inject private BukkitAudiences audiences;
    private final Path dataPath = this.getDataFolder().toPath();
    private final Path modulesPath = dataPath.resolve("modules");
    private final Path i18nPath = dataPath.resolve("i18n");
    private final VanillaTweaksConfig config = BaseConfig.create(VanillaTweaksConfig.class, this.dataPath);
    private final Jdbi jdbi = DatabaseType.installPlugins(this.config.database.type.createJdbiInstance(this.dataPath, this.config));

    @Override
    public void onEnable() {
        getLogger().info("Thank you for using PaperTweaks/VanillaTweaks!");
        getLogger().info("If you have any issues, please visit one of the following links for support:");
        getLogger().info("  - https://discord.gg/invite/Np6Pcb78rr");
        getLogger().info("  - https://github.com/MC-Machinations/VanillaTweaks/issues");
        PaperLib.suggestPaper(this);
        Integrations.load();
        try (Handle handle = this.jdbi.open()) {
            handle.execute(this.config.database.type.readSchema(this.getClassLoader()));
            LOGGER.info("You are using the " + this.config.database.type.name() + " database type.");
        } catch (IOException exception) {
            LOGGER.error("Unable to create/load the database of type " + this.config.database.type.name());
            LOGGER.error("You could try select a different database type by changing the type in the config.yml");
            this.getPluginLoader().disablePlugin(this);
            throw new RuntimeException("Could not create database and load schema", exception);
        }

        tryBackupOldModulesYml();
        try {
            migrateModuleConfigs();
        } catch (IOException e) {
            e.printStackTrace();
            this.getPluginLoader().disablePlugin(this);
            return;
        }
        I18n.create(this.i18nPath, this.getClassLoader()).setupI18n();

        BukkitAudiences bukkitAudiences = BukkitAudiences.builder(this).componentRenderer(ptr -> ptr.getOrDefault(Identity.LOCALE, Locale.US), new MiniMessageComponentRenderer()).build();
        PlayerMapFactory mapFactory = new PlayerMapFactory();
        Injector pluginInjector;
        try {
            pluginInjector = Guice.createInjector(new DatabaseModule(this.jdbi), new AbstractModule() {
                @Override
                protected void configure() {
                    bind(VanillaTweaksConfig.class).toInstance(VanillaTweaks.this.config);
                    bind(VanillaTweaks.class).toInstance(VanillaTweaks.this);
                    bind(JavaPlugin.class).toInstance(VanillaTweaks.this);
                    bind(Plugin.class).toInstance(VanillaTweaks.this);
                    bind(PlayerMapFactory.class).toInstance(mapFactory);
                    bind(BukkitAudiences.class).toInstance(bukkitAudiences);
                    bind(Audience.class).annotatedWith(Names.named("server")).toInstance(bukkitAudiences.all());
                    bind(Audience.class).annotatedWith(Names.named("players")).toInstance(bukkitAudiences.players());
                    bind(Path.class).annotatedWith(Names.named("data")).toInstance(VanillaTweaks.this.dataPath);
                    bind(Path.class).annotatedWith(Names.named("modules")).toInstance(VanillaTweaks.this.modulesPath);
                    bind(Path.class).annotatedWith(Names.named("i18n")).toInstance(VanillaTweaks.this.i18nPath);
                    bind(ClassLoader.class).annotatedWith(Names.named("plugin")).toInstance(VanillaTweaks.this.getClassLoader());
                }
            }, new ModuleRegistry(this), new CloudModule(this, EXECUTOR_SERVICE));
            pluginInjector.injectMembers(this);
        } catch (CreationException e) {
            throw new RuntimeException("Could not create injector!", e);
        }

        audiences.console().sendMessage(join(PLUGIN_PREFIX, translatable("plugin-lifecycle.on-enable.loaded-modules", GOLD, text(moduleManager.loadModules(), GRAY))));
        audiences.console().sendMessage(join(PLUGIN_PREFIX, translatable("plugin-lifecycle.on-enable.enabled-modules", GREEN, text(moduleManager.enableModules(), GRAY))));

        pluginInjector.getInstance(RootCommand.class).registerCommands();
        this.getServer().getPluginManager().registerEvents(pluginInjector.getInstance(GlobalListener.class), this);
        this.getServer().getPluginManager().registerEvents(mapFactory, this);
    }

    @Override
    public void onDisable() {
        String disabled = "N/A";
        if (this.moduleManager != null) {
            disabled = String.valueOf(moduleManager.disableModules(true));
        }
        EXECUTOR_SERVICE.shutdownNow();
        if (this.audiences != null) {
            audiences.console().sendMessage(join(PLUGIN_PREFIX, translatable("plugin-lifecycle.on-disable.disabled-modules", YELLOW, text(disabled, GRAY))));
            audiences.close();
        }
    }

    private void tryBackupOldModulesYml() {
        File oldFile = new File(this.getDataFolder(), "modules.yml");
        if (oldFile.exists()) {
            FileConfiguration oldModulesConfig = YamlConfiguration.loadConfiguration(oldFile);
            if (oldModulesConfig.contains("item-tools")) { // is old
                if (oldFile.renameTo(new File(this.getDataFolder(), "OLD_modules.yml"))) {
                    LOGGER.warn("OLD modules.yml detected. It has been backed up to OLD_modules.yml");
                    LOGGER.warn("You can re-configure your enabled packs with the new modules.yml");
                } else {
                    LOGGER.error("Could not rename modules.yml to back it up! Please rename VanillaTweaks/modules.yml to something else so the plugin can create a new one");
                    this.getPluginLoader().disablePlugin(this);
                    return;
                }

            }
        }
    }

    private void migrateModuleConfigs() throws IOException {
        if (Files.notExists(this.modulesPath)) {
            RAN_CONFIG_MIGRATIONS = true;
            Files.createDirectories(this.modulesPath);
            LOGGER.info("Moving module configurations to their new location");
            try (Stream<Path> files = Files.list(this.dataPath)) {
                for (Path path : files.toList()) {
                    if (Files.isDirectory(path) && !Files.isSameFile(path, this.modulesPath)) {
                        Files.move(path, this.modulesPath.resolve(path.getFileName()));
                    }
                }
            }
        }

        String current = "none";
        try {
            current = "playergraves";
            if (Files.exists(this.modulesPath.resolve(current)) && !Files.exists(this.modulesPath.resolve("graves"))) {
                Files.move(this.modulesPath.resolve(current), this.modulesPath.resolve("graves"));
                LOGGER.info("Moved '{}' config to 'graves' folder", current);
            }
            current = "wrench";
            if (Files.exists(this.modulesPath.resolve(current)) && !Files.exists(this.modulesPath.resolve("wrenches"))) {
                Files.move(this.modulesPath.resolve(current), this.modulesPath.resolve("wrenches"));
                LOGGER.info("Moved '{}' config to 'wrenches' folder", current);
            }
            current = "sethome";
            if (Files.exists(this.modulesPath.resolve(current)) && !Files.exists(this.modulesPath.resolve("homes"))) {
                Files.move(this.modulesPath.resolve(current), this.modulesPath.resolve("homes"));
                LOGGER.info("Moved '{}' config to 'homes' folder", current);
            }
            current = "homes.yml";
            if (Files.exists(this.modulesPath.resolve("homes").resolve("homes.yml"))) {
                Homes.migrateHomesYmlConfig(this.jdbi, this.modulesPath.resolve("homes").resolve("homes.yml"));
                Files.deleteIfExists(this.modulesPath.resolve("homes").resolve("homes.yml"));
                LOGGER.info("Migrated '{}' config to h2 database", "homes/" + current);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to migrate {} configuration!", current, e);
        }
    }
}
