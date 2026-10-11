package com.mrcdprm.airline.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite bağlantısı ve şema. Masaüstü uygulaması tek kullanıcılı olduğu için tek bağlantı paylaşılır;
 * aynı anda iki iş parçacığı yazmasın diye bütün işlemler bu nesne üzerinden sırayla (synchronized) yapılır.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final int SCHEMA_VERSION = 1;

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS meta (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS staff (
            id              INTEGER PRIMARY KEY,
            username        TEXT NOT NULL UNIQUE COLLATE NOCASE,
            full_name       TEXT NOT NULL,
            password_hash   TEXT NOT NULL,
            failed_attempts INTEGER NOT NULL DEFAULT 0,
            locked_until    TEXT,
            created_at      TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS flights (
            id                  INTEGER PRIMARY KEY,
            number              TEXT NOT NULL,
            origin              TEXT NOT NULL,
            destination         TEXT NOT NULL CHECK (destination <> origin),
            departure           TEXT NOT NULL,
            arrival             TEXT NOT NULL CHECK (arrival > departure),
            scheduled_departure TEXT NOT NULL,
            aircraft            TEXT NOT NULL,
            economy_fare        INTEGER NOT NULL CHECK (economy_fare > 0),
            business_fare       INTEGER NOT NULL CHECK (business_fare > 0),
            status              TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'DELAYED', 'CANCELLED')),
            gate                TEXT NOT NULL,
            created_at          TEXT NOT NULL,
            UNIQUE (number, scheduled_departure)
        )""",
        "CREATE INDEX IF NOT EXISTS flights_route_date ON flights(origin, destination, departure)",
        "CREATE INDEX IF NOT EXISTS flights_departure ON flights(departure)",
        """
        CREATE TABLE IF NOT EXISTS bookings (
            id            INTEGER PRIMARY KEY,
            pnr           TEXT NOT NULL UNIQUE,
            flight_id     INTEGER NOT NULL REFERENCES flights(id),
            cabin         TEXT NOT NULL CHECK (cabin IN ('ECONOMY', 'BUSINESS')),
            contact_email TEXT NOT NULL,
            contact_phone TEXT NOT NULL,
            status        TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
            total         INTEGER NOT NULL CHECK (total >= 0),
            refund        INTEGER NOT NULL DEFAULT 0 CHECK (refund >= 0),
            created_at    TEXT NOT NULL,
            cancelled_at  TEXT,
            cancelled_by  TEXT CHECK (cancelled_by IN ('PASSENGER', 'STAFF', 'AIRLINE'))
        )""",
        "CREATE INDEX IF NOT EXISTS bookings_flight ON bookings(flight_id)",
        """
        CREATE TABLE IF NOT EXISTS tickets (
            id            INTEGER PRIMARY KEY,
            booking_id    INTEGER NOT NULL REFERENCES bookings(id),
            flight_id     INTEGER NOT NULL REFERENCES flights(id),
            ticket_no     TEXT NOT NULL UNIQUE,
            first_name    TEXT NOT NULL,
            last_name     TEXT NOT NULL,
            type          TEXT NOT NULL CHECK (type IN ('ADULT', 'CHILD')),
            seat          TEXT NOT NULL,
            fare          INTEGER NOT NULL CHECK (fare >= 0),
            tax           INTEGER NOT NULL CHECK (tax >= 0),
            seat_fee      INTEGER NOT NULL DEFAULT 0 CHECK (seat_fee >= 0),
            checked_in_at TEXT,
            boarding_no   INTEGER,
            active        INTEGER NOT NULL DEFAULT 1
        )""",
        "CREATE INDEX IF NOT EXISTS tickets_booking ON tickets(booking_id)",
        "CREATE INDEX IF NOT EXISTS tickets_flight ON tickets(flight_id, active)",
        // Aynı uçuşta bir koltuk sadece bir geçerli bilette olabilir: çift satışı veritabanı da engeller.
        // İptal edilen biletler (active = 0) indekse girmez, koltuk yeniden satılabilir.
        "CREATE UNIQUE INDEX IF NOT EXISTS tickets_seat ON tickets(flight_id, seat) WHERE active = 1",
    };

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Dosyadaki veritabanını açar; klasör ve tablolar yoksa oluşturur. */
    public static Database open(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return connect("jdbc:sqlite:" + file.toAbsolutePath());
    }

    /** Testler için bellekte geçici veritabanı. */
    public static Database inMemory() {
        return connect("jdbc:sqlite::memory:");
    }

    private static Database connect(String url) {
        try {
            final Connection connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
                for (String sql : SCHEMA)
                    st.execute(sql);
                st.execute("INSERT OR IGNORE INTO meta(key, value) VALUES ('schema_version', '" + SCHEMA_VERSION + "')");
            }
            return new Database(connection);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /** Okuma: otomatik commit açık. */
    public synchronized <T> T read(Work<T> work) {
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /**
     * Yazma işlemi: içindeki bütün ifadeler ya hep birlikte kaydedilir ya da hiçbiri (atomik).
     * Arada bir istisna olursa geri alınır; örneğin rezervasyon kaydı oluşup biletler yazılamazsa
     * yarım rezervasyon kalmaz.
     */
    public synchronized <T> T transaction(Work<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                final T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // kapanırken hata önemli değil
        }
    }
}
