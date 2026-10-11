package com.mrcdprm.airline.service;

import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.data.Database;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.random.RandomGenerator;

/**
 * Servislerin bir arada tutulduğu nesne. Saat (Clock) dışarıdan verilir: testlerde zamanı ileri alıp
 * check-in penceresi, satış kapanışı ve iade oranları gibi zamana bağlı kurallar denenebilir.
 */
public final class Airline {

    /** Kalkışa bu kadar dakika kala satış, koltuk değişikliği ve check-in kapanır. */
    public static final int CLOSE_MINUTES = 60;
    /** Online check-in kalkıştan bu kadar saat önce açılır. */
    public static final int CHECK_IN_OPEN_HOURS = 24;
    /** Bir rezervasyonda en fazla yolcu sayısı. */
    public static final int MAX_PASSENGERS = 6;

    private final Database db;
    private final Clock clock;
    private final RandomGenerator random;
    private final AuthService auth;
    private final FlightService flights;
    private final BookingService bookings;
    private final ReportService reports;

    public Airline(Database db, Clock clock) {
        this(db, clock, new SecureRandom());
    }

    public Airline(Database db, Clock clock, RandomGenerator random) {
        this.db = db;
        this.clock = clock;
        this.random = random;
        this.auth = new AuthService(this);
        this.flights = new FlightService(this);
        this.bookings = new BookingService(this);
        this.reports = new ReportService(this);
    }

    public AuthService auth() {
        return auth;
    }

    public FlightService flights() {
        return flights;
    }

    public BookingService bookings() {
        return bookings;
    }

    public ReportService reports() {
        return reports;
    }

    Database db() {
        return db;
    }

    RandomGenerator random() {
        return random;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Personel işlemleri için oturum şart; yetki kontrolü arayüzde değil burada yapılır. */
    static void requireStaff(Session session) {
        if (session == null)
            throw new AirlineException("notAuthorized");
    }
}
