package dev.voidreturn;

record WorldConfig(double voidThreshold, long cooldownMillis,
                   double fallbackX, double fallbackY, double fallbackZ,
                   float fallbackYaw, float fallbackPitch) {
}
