package com.mrcdprm.airline.service;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Codes;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.core.Money;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.data.Records;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.data.Records.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Uçuş arama (yolcu tarafı) ve uçuş yönetimi (personel tarafı). */
public final class FlightService {

    /** Bir uçuşun satış ve doluluk bilgisiyle birlikte hâli. */
    public record FlightRow(Flight flight, int soldEconomy, int soldBusiness, int checkedIn) {

        public int sold() {
            return soldEconomy + soldBusiness;
        }

        public int sold(CabinClass cabin) {
            return cabin == CabinClass.BUSINESS ? soldBusiness : soldEconomy;
        }

        public int capacity() {
            return flight.aircraft().capacity();
        }

        public int seatsLeft(CabinClass cabin) {
            return flight.aircraft().capacity(cabin) - sold(cabin);
        }

        /** Doluluk oranı, 0-1. */
        public double load() {
            return capacity() == 0 ? 0 : (double) sold() / capacity();
        }
    }

    /** Arama sonucundaki bir uçuş: yetişkin/çocuk ücreti, kalan koltuk ve satın alınabilir mi. */
    public record Offer(FlightRow row, CabinClass cabin, long adultFare, long childFare, int seatsLeft, boolean bookable) {

        public Flight flight() {
            return row.flight();
        }
    }

    /** Personelin yeni uçuş formu. */
    public record NewFlight(Airport origin, Airport destination, LocalDate firstDate, LocalTime time,
            AircraftType aircraft, long economyFare, long businessFare, int days) {
    }

    /** Yolcu listesindeki bir satır. */
    public record ManifestRow(Ticket ticket, String pnr) {
    }

    public static final int MAX_REPEAT_DAYS = 60;
    public static final int MAX_DELAY_MINUTES = 24 * 60;
    private static final String GATE_LETTERS = "ABCDEF";

    private static final String ROW_SELECT = """
            SELECT f.*,
              (SELECT COUNT(*) FROM tickets t JOIN bookings b ON b.id = t.booking_id
                WHERE t.flight_id = f.id AND t.active = 1 AND b.cabin = 'ECONOMY') AS sold_economy,
              (SELECT COUNT(*) FROM tickets t JOIN bookings b ON b.id = t.booking_id
                WHERE t.flight_id = f.id AND t.active = 1 AND b.cabin = 'BUSINESS') AS sold_business,
              (SELECT COUNT(*) FROM tickets t
                WHERE t.flight_id = f.id AND t.active = 1 AND t.checked_in_at IS NOT NULL) AS checked_in
            FROM flights f
            """;

    private final Airline airline;

    FlightService(Airline airline) {
        this.airline = airline;
    }

    // ---------------------------------------------------------------- yolcu tarafı

    /**
     * Bir günün uçuşları. İptal edilen uçuşlar listelenmez; satışı kapanmış ya da yeterli koltuğu kalmamış
     * uçuşlar listede görünür ama satın alınamaz (kullanıcı neden seçemediğini görsün).
     */
    public List<Offer> search(Airport from, Airport to, LocalDate date, CabinClass cabin, int passengers) {
        if (from == null || to == null || date == null || cabin == null)
            throw new AirlineException("searchIncomplete");
        if (from == to)
            throw new AirlineException("sameAirport");
        if (passengers < 1 || passengers > Airline.MAX_PASSENGERS)
            throw new AirlineException("passengerCount", Airline.MAX_PASSENGERS);
        if (date.isBefore(airline.today()))
            throw new AirlineException("pastDate");
        final LocalDateTime now = airline.now();
        final List<FlightRow> rows = airline.db().read(c -> rows(c,
                "WHERE f.origin = ? AND f.destination = ? AND f.departure >= ? AND f.departure < ? AND f.status <> 'CANCELLED'"
                        + " ORDER BY f.departure",
                from.code(), to.code(), Records.ts(date.atStartOfDay()), Records.ts(date.plusDays(1).atStartOfDay())));
        final List<Offer> offers = new ArrayList<>();
        for (FlightRow row : rows) {
            final long adult = fare(row, cabin, now);
            final int left = row.seatsLeft(cabin);
            final boolean open = row.flight().departure().isAfter(now.plusMinutes(Airline.CLOSE_MINUTES));
            offers.add(new Offer(row, cabin, adult, Pricing.childFare(adult), left, open && left >= passengers));
        }
        return offers;
    }

    /** Seçili günün 3 gün öncesi ve sonrası için en düşük yetişkin ücreti (tarih şeridi). Uçuş yoksa değer yok. */
    public Map<LocalDate, Long> lowestFares(Airport from, Airport to, LocalDate center, CabinClass cabin, int passengers) {
        final Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (int d = -3; d <= 3; d++) {
            final LocalDate date = center.plusDays(d);
            if (date.isBefore(airline.today()))
                continue;
            search(from, to, date, cabin, passengers).stream()
                    .filter(Offer::bookable)
                    .mapToLong(Offer::adultFare)
                    .min()
                    .ifPresentOrElse(min -> result.put(date, min), () -> result.put(date, null));
        }
        return result;
    }

    public Optional<Flight> get(long flightId) {
        return airline.db().read(c -> findFlight(c, flightId));
    }

    public Optional<FlightRow> row(long flightId) {
        return airline.db().read(c -> rows(c, "WHERE f.id = ?", flightId).stream().findFirst());
    }

    /** Uçuştaki dolu koltuk kodları ("12A"...). */
    public Set<String> occupiedSeats(long flightId) {
        return airline.db().read(c -> occupied(c, flightId));
    }

    /** Şu anki yetişkin ücreti: doluluğa ve kalkışa kalan süreye göre. */
    long fare(FlightRow row, CabinClass cabin, LocalDateTime now) {
        final Flight f = row.flight();
        final long hours = Duration.between(now, f.departure()).toHours();
        return Pricing.adultFare(f.baseFare(cabin), row.sold(cabin), f.aircraft().capacity(cabin), hours);
    }

    // ---------------------------------------------------------------- personel tarafı

    /** Uçuş listesi: tarih aralığı, metin (uçuş no, havalimanı kodu ya da şehir) ve durum filtresiyle. */
    public List<FlightRow> list(Session session, LocalDate from, LocalDate to, String query, FlightStatus status) {
        Airline.requireStaff(session);
        if (from == null || to == null || to.isBefore(from))
            throw new AirlineException("invalidDateRange");
        final List<FlightRow> rows = airline.db().read(c -> rows(c,
                "WHERE f.departure >= ? AND f.departure < ?" + (status != null ? " AND f.status = ?" : "")
                        + " ORDER BY f.departure, f.number",
                status != null
                        ? new Object[] {Records.ts(from.atStartOfDay()), Records.ts(to.plusDays(1).atStartOfDay()), status.name()}
                        : new Object[] {Records.ts(from.atStartOfDay()), Records.ts(to.plusDays(1).atStartOfDay())}));
        final String q = query == null ? "" : com.mrcdprm.airline.core.Names.fold(query).replace(" ", "");
        if (q.isEmpty())
            return rows;
        return rows.stream().filter(r -> matches(r.flight(), q)).toList();
    }

    private static boolean matches(Flight f, String q) {
        final String number = f.number().replace(" ", "");
        return number.contains(q) || f.origin().code().contains(q) || f.destination().code().contains(q)
                || com.mrcdprm.airline.core.Names.fold(f.origin().city(java.util.Locale.ROOT)).contains(q)
                || com.mrcdprm.airline.core.Names.fold(f.destination().city(java.util.Locale.ROOT)).contains(q)
                || (f.origin().code() + "-" + f.destination().code()).contains(q);
    }

    /** Yeni uçuş için önerilen numara: aynı hat ve saatte uçuş varsa onun numarası, yoksa boş bir numara. */
    public String suggestNumber(Session session, Airport origin, Airport destination, LocalTime time) {
        Airline.requireStaff(session);
        return airline.db().read(c -> suggestNumber(c, origin, destination, time));
    }

    /**
     * Yeni uçuş ekler; "days" kadar gün her gün aynı saatte tekrarlanır. Hepsi tek işlemde kaydedilir:
     * biri geçersizse (ör. aynı numara aynı saatte zaten varsa) hiçbiri eklenmez.
     */
    public List<Flight> create(Session session, NewFlight form) {
        Airline.requireStaff(session);
        if (form.origin() == null || form.destination() == null || form.firstDate() == null || form.time() == null
                || form.aircraft() == null)
            throw new AirlineException("flightIncomplete");
        if (form.origin() == form.destination())
            throw new AirlineException("sameAirport");
        if (form.days() < 1 || form.days() > MAX_REPEAT_DAYS)
            throw new AirlineException("repeatDays", MAX_REPEAT_DAYS);
        if (form.economyFare() <= 0 || form.businessFare() <= 0 || form.economyFare() > Money.MAX_FARE
                || form.businessFare() > Money.MAX_FARE)
            throw new AirlineException("invalidFare");
        if (form.businessFare() < form.economyFare())
            throw new AirlineException("businessCheaper");
        final LocalDateTime first = form.firstDate().atTime(form.time());
        if (!first.isAfter(airline.now().plusHours(2)))
            throw new AirlineException("departureTooSoon");
        final int minutes = Airport.flightMinutes(form.origin(), form.destination());
        return airline.db().transaction(c -> {
            final String number = suggestNumber(c, form.origin(), form.destination(), form.time());
            final List<Flight> created = new ArrayList<>();
            for (int day = 0; day < form.days(); day++) {
                final LocalDateTime departure = first.plusDays(day);
                try (PreparedStatement exists = c.prepareStatement(
                        "SELECT 1 FROM flights WHERE number = ? AND scheduled_departure = ?")) {
                    exists.setString(1, number);
                    exists.setString(2, Records.ts(departure));
                    if (exists.executeQuery().next())
                        throw new AirlineException("flightExists", number, departure);
                }
                final long id = insertFlight(c, number, form.origin(), form.destination(), departure,
                        departure.plusMinutes(minutes), form.aircraft(), form.economyFare(), form.businessFare(),
                        gate(), airline.now());
                created.add(findFlight(c, id).orElseThrow());
            }
            return created;
        });
    }

    /** Rötar: kalkış ve varış aynı süre kadar ileri alınır. Birden fazla rötar üst üste eklenebilir. */
    public Flight delay(Session session, long flightId, int minutes) {
        Airline.requireStaff(session);
        if (minutes < 5 || minutes > MAX_DELAY_MINUTES)
            throw new AirlineException("invalidDelay");
        return airline.db().transaction(c -> {
            final Flight f = findFlight(c, flightId).orElseThrow(() -> new AirlineException("flightNotFound"));
            requireChangeable(f);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE flights SET departure = ?, arrival = ?, status = 'DELAYED' WHERE id = ?")) {
                ps.setString(1, Records.ts(f.departure().plusMinutes(minutes)));
                ps.setString(2, Records.ts(f.arrival().plusMinutes(minutes)));
                ps.setLong(3, f.id());
                ps.executeUpdate();
            }
            return findFlight(c, flightId).orElseThrow();
        });
    }

    /**
     * Uçuş iptali: bütün geçerli rezervasyonlar havayolu iptali olarak kapanır ve ödenen tutarın tamamı iade edilir.
     * İptal edilen rezervasyon sayısını döner.
     */
    public int cancel(Session session, long flightId) {
        Airline.requireStaff(session);
        return airline.db().transaction(c -> {
            final Flight f = findFlight(c, flightId).orElseThrow(() -> new AirlineException("flightNotFound"));
            requireChangeable(f);
            final String now = Records.ts(airline.now());
            try (PreparedStatement ps = c.prepareStatement("UPDATE flights SET status = 'CANCELLED' WHERE id = ?")) {
                ps.setLong(1, f.id());
                ps.executeUpdate();
            }
            final int bookings;
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE bookings SET status = 'CANCELLED', refund = total, cancelled_at = ?, cancelled_by = 'AIRLINE'
                    WHERE flight_id = ? AND status = 'CONFIRMED'""")) {
                ps.setString(1, now);
                ps.setLong(2, f.id());
                bookings = ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET active = 0 WHERE flight_id = ?")) {
                ps.setLong(1, f.id());
                ps.executeUpdate();
            }
            return bookings;
        });
    }

    /** Yolcu listesi: geçerli biletler koltuk sırasına göre. */
    public List<ManifestRow> manifest(Session session, long flightId) {
        Airline.requireStaff(session);
        final List<ManifestRow> rows = airline.db().read(c -> {
            final List<ManifestRow> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT t.*, b.pnr FROM tickets t JOIN bookings b ON b.id = t.booking_id
                    WHERE t.flight_id = ? AND t.active = 1""")) {
                ps.setLong(1, flightId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        list.add(new ManifestRow(Records.ticket(rs), rs.getString("pnr")));
                }
            }
            return list;
        });
        return rows.stream().sorted(Comparator.comparingInt((ManifestRow r) -> seatRow(r.ticket().seat()))
                .thenComparing(r -> r.ticket().seat())).toList();
    }

    // ---------------------------------------------------------------- yardımcılar

    private void requireChangeable(Flight f) {
        if (f.status() == FlightStatus.CANCELLED)
            throw new AirlineException("flightCancelled");
        if (f.departed(airline.now()))
            throw new AirlineException("flightDeparted");
    }

    static int seatRow(String seat) {
        try {
            return Integer.parseInt(seat.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private String gate() {
        return GATE_LETTERS.charAt(airline.random().nextInt(GATE_LETTERS.length())) + String.valueOf(1 + airline.random().nextInt(20));
    }

    static Optional<Flight> findFlight(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM flights WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Records.flight(rs)) : Optional.empty();
            }
        }
    }

    static Set<String> occupied(Connection c, long flightId) throws SQLException {
        final Set<String> seats = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT seat FROM tickets WHERE flight_id = ? AND active = 1")) {
            ps.setLong(1, flightId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    seats.add(rs.getString(1));
            }
        }
        return seats;
    }

    static List<FlightRow> rows(Connection c, String where, Object... params) throws SQLException {
        final List<FlightRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(ROW_SELECT + where)) {
            for (int i = 0; i < params.length; i++)
                ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    rows.add(new FlightRow(Records.flight(rs), rs.getInt("sold_economy"), rs.getInt("sold_business"),
                            rs.getInt("checked_in")));
            }
        }
        return rows;
    }

    static long insertFlight(Connection c, String number, Airport origin, Airport destination, LocalDateTime departure,
            LocalDateTime arrival, AircraftType aircraft, long economyFare, long businessFare, String gate,
            LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO flights(number, origin, destination, departure, arrival, scheduled_departure, aircraft,
                  economy_fare, business_fare, gate, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, number);
            ps.setString(2, origin.code());
            ps.setString(3, destination.code());
            ps.setString(4, Records.ts(departure));
            ps.setString(5, Records.ts(arrival));
            ps.setString(6, Records.ts(departure));
            ps.setString(7, aircraft.name());
            ps.setLong(8, economyFare);
            ps.setLong(9, businessFare);
            ps.setString(10, gate);
            ps.setString(11, Records.ts(createdAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * Aynı hatta aynı saatte uçan bir uçuş varsa onun numarası (gerçek havayollarında tarifeli sefer her gün
     * aynı numarayla uçar). Yoksa 100'den başlayıp kullanılmayan ilk çift numara.
     */
    private static String suggestNumber(Connection c, Airport origin, Airport destination, LocalTime time) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT number FROM flights WHERE origin = ? AND destination = ? AND substr(scheduled_departure, 12, 5) = ?
                ORDER BY id DESC LIMIT 1""")) {
            ps.setString(1, origin.code());
            ps.setString(2, destination.code());
            ps.setString(3, String.format("%02d:%02d", time.getHour(), time.getMinute()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getString(1);
            }
        }
        final Set<String> used = new HashSet<>();
        try (ResultSet rs = c.createStatement().executeQuery("SELECT DISTINCT number FROM flights")) {
            while (rs.next())
                used.add(rs.getString(1));
        }
        for (int n = 100; n < 10_000; n += 2) {
            final String number = Codes.CARRIER + " " + n;
            if (!used.contains(number))
                return number;
        }
        throw new AirlineException("noFlightNumber");
    }
}
