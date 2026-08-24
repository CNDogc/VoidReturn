package dev.voidreturn;

import java.util.List;
import java.util.Locale;

enum MsgType { TITLE, SUBTITLE, ACTION_BAR, CHAT, BOSS_BAR }

record MessageSpec(MsgType type, String text) {
}

record WorldConfig(double voidThreshold, long cooldownMillis,
                   double fallbackX, double fallbackY, double fallbackZ,
                   float fallbackYaw, float fallbackPitch,
                   int delaySecs, List<MessageSpec> messages) {

    boolean hasCountdown() {
        return delaySecs > 0 && !messages.isEmpty();
    }

    static MsgType parseType(String s) {
        try {
            return MsgType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
