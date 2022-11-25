/*
 * GNU General Public License v3
 *
 * PaperTweaks, a performant replacement for the VanillaTweaks datapacks.
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
package me.machinemaker.vanillatweaks.cloud.cooldown;

import cloud.commandframework.keys.CloudKey;
import cloud.commandframework.keys.SimpleCloudKey;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
class CommandCooldownBuilderImpl<C> implements CommandCooldownBuilder<C> {

    private final CooldownDuration<C> cooldownDuration;
    private @Nullable CommandCooldownNotifier<C> notifier;
    protected @Nullable CloudKey<Void> cooldownKey;

    CommandCooldownBuilderImpl(final CooldownDuration<C> cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
    }

    @Override
    public CommandCooldownBuilder<C> notifier(final CommandCooldownNotifier<C> notifier) {
        this.notifier = notifier;
        return this;
    }

    @Override
    public CommandCooldownBuilder<C> key(final CloudKey<Void> cooldownKey) {
        this.cooldownKey = cooldownKey;
        return this;
    }

    protected CloudKey<Void> getOrCreateKey() {
        @Nullable CloudKey<Void> key = this.cooldownKey;
        if (key == null) {
            key = SimpleCloudKey.of(UUID.randomUUID().toString());
        }
        return key;
    }

    @Override
    public CommandCooldown<C> build() {
        return new CommandCooldownImpl<>(this.getOrCreateKey(), this.cooldownDuration, this.notifier);
    }
}
