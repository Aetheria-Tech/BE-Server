package com.serverbe.infrastructure.crypto;

public class EncryptionContext {
    private static final ThreadLocal<Boolean> migrationRequired = ThreadLocal.withInitial(() -> false);

    public static void setMigrationRequired(boolean required) {
        migrationRequired.set(required);
    }

    public static boolean isMigrationRequired() {
        return migrationRequired.get();
    }

    public static void clear() {
        migrationRequired.remove();
    }
}