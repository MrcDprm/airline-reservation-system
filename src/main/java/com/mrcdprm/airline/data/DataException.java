package com.mrcdprm.airline.data;

import java.sql.SQLException;

/** Veritabanı hatası. Ayrıntısı kullanıcıya gösterilmez, sadece günlüğe yazılır. */
public final class DataException extends RuntimeException {

    public DataException(SQLException cause) {
        super(cause);
    }

    public DataException(String message) {
        super(message);
    }

    /** Hatanın nedeni bir UNIQUE kısıtı mı? (aynı koltuk iki kez satılmak istendiğinde) */
    public boolean isUniqueViolation() {
        return getCause() instanceof SQLException e && e.getMessage() != null && e.getMessage().contains("UNIQUE");
    }
}
