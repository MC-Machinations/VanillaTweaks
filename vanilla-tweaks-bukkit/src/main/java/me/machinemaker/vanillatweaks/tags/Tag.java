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
package me.machinemaker.vanillatweaks.tags;

import org.bukkit.Keyed;

import java.util.function.Predicate;

public interface Tag<T extends Keyed> extends org.bukkit.Tag<T>, Predicate<T> {

    /**
     * Checks if the value is a member of this tag
     *
     * @param value the value to check
     * @return true if value is a member of this tag
     */
    @Override
    boolean test(T value);
}