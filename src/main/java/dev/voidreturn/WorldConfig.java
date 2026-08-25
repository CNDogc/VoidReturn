/*
 * VoidReturn - Source-memory void teleport plugin for Paper
 * Copyright (C) 2026 狗晨Yz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.voidreturn;

import java.util.List;
import java.util.Locale;

enum MsgType { TITLE, SUBTITLE, ACTION_BAR, CHAT, BOSS_BAR }

record MessageSpec(MsgType type, String text) {
}

record WorldConfig(double voidThreshold, long cooldownMillis,
                   double fallbackX, double fallbackY, double fallbackZ,
                   float fallbackYaw, float fallbackPitch,
                   int delaySecs, List<MessageSpec> beforeMessages, List<MessageSpec> afterMessages) {

    boolean hasCountdown() {
        return delaySecs > 0 && !beforeMessages.isEmpty();
    }

    static MsgType parseType(String s) {
        try {
            return MsgType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
