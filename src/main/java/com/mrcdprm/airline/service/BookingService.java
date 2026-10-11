package com.mrcdprm.airline.service;

import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.core.BookingStatus;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Codes;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.core.Names;
import com.mrcdprm.airline.core.PassengerType;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.core.RefundPolicy;
import com.mrcdprm.airline.core.Seat;
import com.mrcdprm.airline.core.Validation;
import com.mrcdprm.airline.data.DataException;
import com.mrcdprm.airline.data.Records;
import com.mrcdprm.airline.data.Records.Booking;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.service.FlightService.FlightRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rezervasyon: fiyat teklifi, satın alma, rezervasyon kodu + soyadı ile bulma, koltuk değiştirme,
 * online check-in ve iptal. Yolcu tarafında oturum yoktur; her işlemde kod ve soyad yeniden doğrulanır.
 */
public final class BookingService {

    public record PassengerInput(String firstName, String lastName, PassengerType type, String seat) {
    }

    public record BookingRequest(long flightId, CabinClass cabin, List<PassengerInput> passengers, String email,
            String phone) {
    }

    /** Fiyat dökümü. Ücretler sunucu tarafında (burada) hesaplanır; arayüzün gönderdiği fiyata güvenilmez. */
    public record Quote(long adultFare, long childFare, int adults, int children, long fares, long taxes,
            long seatFees, long total) {
    }

    /** Yolcunun rezervasyona erişim anahtarı: kod + soyad (havayollarındaki "Rezervasyonumu yönet"). */
    public record BookingKey(String pnr, String lastName) {
    }

    public record BookingDetail(Booking booking, Flight flight, List<Ticket> tickets) {

        public boolean cancelled() {
            return booking.status() == BookingStatus.CANCELLED;
        }

        public long fares() {
            return tickets.stream().mapToLong(Ticket::fare).sum();
        }

        public long taxes() {
            return tickets.stream().mapToLong(Ticket::tax).sum();
        }

        public long seatFees() {
            return tickets.stream().mapToLong(Ticket::seatFee).sum();
        }

        public boolean allCheckedIn() {
            return !tickets.isEmpty() && tickets.stream().allMatch(Ticket::checkedIn);
        }
    }

    /** İptal öncesi kullanıcıya gösterilen iade bilgisi. */
    public record RefundQuote(int farePercent, long refund, long paid) {
    }

    /** Check-in penceresinin durumu. */
    public enum CheckInState {
        NOT_OPEN, OPEN, CLOSED
    }

    /** Personelin rezervasyon listesindeki satır. */
    public record BookingRow(Booking booking, Flight flight, String leadName, int passengers) {
    }

    /** Arka arkaya hatalı aramada kısa süre bekletme: rezervasyon kodu deneme yanılmayla bulunamasın. */
    static final int MAX_FAILED_LOOKUPS = 5;
    static final int LOOKUP_WINDOW_MINUTES = 10;
    static final int LOOKUP_BLOCK_MINUTES = 1;

    private final Airline airline;
    private final Deque<LocalDateTime> failedLookups = new ArrayDeque<>();
    private LocalDateTime blockedUntil;

    BookingService(Airline airline) {
        this.airline = airline;
    }

    // ---------------------------------------------------------------- teklif ve satın alma

    public Quote quote(long flightId, CabinClass cabin, List<PassengerInput> passengers) {
        final FlightRow row = airline.flights().row(flightId).orElseThrow(() -> new AirlineException("flightNotFound"));
        return quote(row, cabin, passengers, airline.now());
    }

    private Quote quote(FlightRow row, CabinClass cabin, List<PassengerInput> passengers, LocalDateTime now) {
        final long adult = airline.flights().fare(row, cabin, now);
        final long child = Pricing.childFare(adult);
        int adults = 0;
        int children = 0;
        long fares = 0;
        long seatFees = 0;
        for (PassengerInput p : passengers) {
            if (p.type() == PassengerType.CHILD) {
                children++;
                fares += child;
            } else {
                adults++;
                fares += adult;
            }
            if (p.seat() != null)
                seatFees += row.flight().aircraft().seat(p.seat()).map(Pricing::seatFee).orElse(0L);
        }
        final long taxes = Pricing.TAX_PER_PASSENGER * passengers.size();
        return new Quote(adult, child, adults, children, fares, taxes, seatFees, fares + taxes + seatFees);
    }

    /**
     * Rezervasyonu kaydeder ve rezervasyon kodunu döner. Fiyat kullanıcı onay ekranındayken değiştiyse
     * (ör. bu arada koltuk satıldı) kayıt yapılmaz, yeni fiyat bildirilir. Bütün kontroller ve kayıt tek işlemdedir.
     */
    public BookingDetail book(BookingRequest request, long expectedTotal) {
        validate(request);
        final String email = request.email().strip();
        final String phone = Validation.phone(request.phone());
        try {
            final long bookingId = airline.db().transaction(c -> {
                final LocalDateTime now = airline.now();
                final FlightRow row = FlightService.rows(c, "WHERE f.id = ?", request.flightId()).stream().findFirst()
                        .orElseThrow(() -> new AirlineException("flightNotFound"));
                final Flight flight = row.flight();
                requireOpen(flight, now);
                if (row.seatsLeft(request.cabin()) < request.passengers().size())
                    throw new AirlineException("notEnoughSeats");
                final Set<String> taken = FlightService.occupied(c, flight.id());
                final Set<String> chosen = new HashSet<>();
                for (PassengerInput p : request.passengers()) {
                    final Seat seat = flight.aircraft().seat(p.seat())
                            .filter(s -> s.cabin() == request.cabin())
                            .orElseThrow(() -> new AirlineException("invalidSeat", p.seat()));
                    if (!chosen.add(seat.code()))
                        throw new AirlineException("duplicateSeat", seat.code());
                    if (taken.contains(seat.code()))
                        throw new AirlineException("seatTaken", seat.code());
                }
                final Quote quote = quote(row, request.cabin(), request.passengers(), now);
                if (quote.total() != expectedTotal)
                    throw new AirlineException("priceChanged", quote.total());

                final String pnr = uniqueCode(c, "SELECT 1 FROM bookings WHERE pnr = ?", true);
                final long id;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO bookings(pnr, flight_id, cabin, contact_email, contact_phone, total, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, pnr);
                    ps.setLong(2, flight.id());
                    ps.setString(3, request.cabin().name());
                    ps.setString(4, email);
                    ps.setString(5, phone);
                    ps.setLong(6, quote.total());
                    ps.setString(7, Records.ts(now));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                for (PassengerInput p : request.passengers()) {
                    final Seat seat = flight.aircraft().seat(p.seat()).orElseThrow();
                    insertTicket(c, id, flight.id(), uniqueCode(c, "SELECT 1 FROM tickets WHERE ticket_no = ?", false),
                            Names.clean(p.firstName()), Names.clean(p.lastName()), p.type(), seat.code(),
                            Pricing.fare(quote.adultFare(), p.type()), Pricing.TAX_PER_PASSENGER, Pricing.seatFee(seat));
                }
                return id;
            });
            return airline.db().read(c -> detail(c, bookingId));
        } catch (DataException e) {
            // Aynı koltuğu aynı anda başka bir işlem almışsa veritabanının UNIQUE indeksi engeller
            if (e.isUniqueViolation())
                throw new AirlineException("seatJustTaken");
            throw e;
        }
    }

    private void validate(BookingRequest request) {
        if (request == null || request.cabin() == null || request.passengers() == null)
            throw new AirlineException("bookingIncomplete");
        final List<PassengerInput> list = request.passengers();
        if (list.isEmpty() || list.size() > Airline.MAX_PASSENGERS)
            throw new AirlineException("passengerCount", Airline.MAX_PASSENGERS);
        if (list.stream().noneMatch(p -> p.type() == PassengerType.ADULT))
            throw new AirlineException("adultRequired");
        for (int i = 0; i < list.size(); i++) {
            final PassengerInput p = list.get(i);
            if (p.type() == null || !Names.valid(p.firstName()) || !Names.valid(p.lastName()))
                throw new AirlineException("invalidName", i + 1);
            if (p.seat() == null || p.seat().isBlank())
                throw new AirlineException("seatMissing", i + 1);
        }
        if (!Validation.email(request.email()))
            throw new AirlineException("invalidEmail");
        if (Validation.phone(request.phone()) == null)
            throw new AirlineException("invalidPhone");
    }

    private void requireOpen(Flight flight, LocalDateTime now) {
        if (flight.status() == FlightStatus.CANCELLED)
            throw new AirlineException("flightCancelled");
        if (!flight.departure().isAfter(now.plusMinutes(Airline.CLOSE_MINUTES)))
            throw new AirlineException("salesClosed");
    }

    // ---------------------------------------------------------------- rezervasyonu bulma

    /** Kod + soyad ile rezervasyon. İkisinden biri yanlışsa aynı mesaj verilir (hangisinin yanlış olduğu söylenmez). */
    public BookingDetail find(BookingKey key) {
        return airline.db().read(c -> resolve(c, key));
    }

    private synchronized BookingDetail resolve(Connection c, BookingKey key) throws SQLException {
        final LocalDateTime now = airline.now();
        if (blockedUntil != null && blockedUntil.isAfter(now))
            throw new AirlineException("tooManyLookups", Math.max(1, Duration.between(now, blockedUntil).toSeconds()));
        final String pnr = key == null ? null : Codes.normalizePnr(key.pnr());
        Optional<BookingDetail> found = Optional.empty();
        if (pnr != null && key.lastName() != null) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM bookings WHERE pnr = ?")) {
                ps.setString(1, pnr);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        found = Optional.of(detail(c, rs.getLong(1)));
                }
            }
            found = found.filter(d -> d.tickets().stream().anyMatch(t -> Names.sameSurname(t.lastName(), key.lastName())));
        }
        if (found.isEmpty()) {
            failedLookups.addLast(now);
            while (!failedLookups.isEmpty() && failedLookups.peekFirst().isBefore(now.minusMinutes(LOOKUP_WINDOW_MINUTES)))
                failedLookups.removeFirst();
            if (failedLookups.size() >= MAX_FAILED_LOOKUPS) {
                failedLookups.clear();
                blockedUntil = now.plusMinutes(LOOKUP_BLOCK_MINUTES);
            }
            throw new AirlineException("bookingNotFound");
        }
        return found.get();
    }

    // ---------------------------------------------------------------- koltuk, check-in, iptal

    /**
     * Bir yolcunun koltuğunu değiştirir (check-in yapılmadıysa). Geniş bacak mesafeli koltuğa geçişte fark alınır;
     * ödenmiş koltuk ücreti iade edilmez. Alınan ek ücreti döner.
     */
    public long changeSeat(BookingKey key, long ticketId, String newSeat) {
        return airline.db().transaction(c -> {
            final BookingDetail d = resolve(c, key);
            final LocalDateTime now = airline.now();
            requireActive(d, now);
            if (!d.flight().departure().isAfter(now.plusMinutes(Airline.CLOSE_MINUTES)))
                throw new AirlineException("changeClosed");
            final Ticket ticket = d.tickets().stream().filter(t -> t.id() == ticketId).findFirst()
                    .orElseThrow(() -> new AirlineException("ticketNotFound"));
            if (ticket.checkedIn())
                throw new AirlineException("alreadyCheckedIn");
            final Seat seat = d.flight().aircraft().seat(newSeat).filter(s -> s.cabin() == d.booking().cabin())
                    .orElseThrow(() -> new AirlineException("invalidSeat", newSeat));
            if (seat.code().equals(ticket.seat()))
                return 0L;
            if (FlightService.occupied(c, d.flight().id()).contains(seat.code()))
                throw new AirlineException("seatTaken", seat.code());
            final long fee = Math.max(ticket.seatFee(), Pricing.seatFee(seat));
            final long extra = fee - ticket.seatFee();
            try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET seat = ?, seat_fee = ? WHERE id = ?")) {
                ps.setString(1, seat.code());
                ps.setLong(2, fee);
                ps.setLong(3, ticket.id());
                ps.executeUpdate();
            }
            if (extra > 0) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET total = total + ? WHERE id = ?")) {
                    ps.setLong(1, extra);
                    ps.setLong(2, d.booking().id());
                    ps.executeUpdate();
                }
            }
            return extra;
        });
    }

    public CheckInState checkInState(Flight flight) {
        final LocalDateTime now = airline.now();
        if (!now.isBefore(flight.departure().minusMinutes(Airline.CLOSE_MINUTES)))
            return CheckInState.CLOSED;
        if (now.isBefore(checkInOpens(flight)))
            return CheckInState.NOT_OPEN;
        return CheckInState.OPEN;
    }

    public static LocalDateTime checkInOpens(Flight flight) {
        return flight.departure().minusHours(Airline.CHECK_IN_OPEN_HOURS);
    }

    /**
     * Online check-in: rezervasyondaki bütün yolcular için yapılır. Her yolcu uçuştaki sıradaki biniş numarasını alır.
     */
    public BookingDetail checkIn(BookingKey key) {
        return airline.db().transaction(c -> {
            final BookingDetail d = resolve(c, key);
            final LocalDateTime now = airline.now();
            requireActive(d, now);
            switch (checkInState(d.flight())) {
                case NOT_OPEN -> throw new AirlineException("checkInNotOpen", checkInOpens(d.flight()));
                case CLOSED -> throw new AirlineException("checkInClosed");
                default -> {
                }
            }
            int next;
            try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(MAX(boarding_no), 0) FROM tickets WHERE flight_id = ?")) {
                ps.setLong(1, d.flight().id());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    next = rs.getInt(1);
                }
            }
            for (Ticket t : d.tickets()) {
                if (t.checkedIn())
                    continue;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE tickets SET checked_in_at = ?, boarding_no = ? WHERE id = ?")) {
                    ps.setString(1, Records.ts(now));
                    ps.setInt(2, ++next);
                    ps.setLong(3, t.id());
                    ps.executeUpdate();
                }
            }
            return detail(c, d.booking().id());
        });
    }

    public RefundQuote refundQuote(BookingKey key) {
        final BookingDetail d = find(key);
        requireActive(d, airline.now());
        return refundQuote(d);
    }

    private RefundQuote refundQuote(BookingDetail d) {
        final long minutes = Duration.between(airline.now(), d.flight().departure()).toMinutes();
        final int percent = RefundPolicy.farePercent(d.booking().cabin(), minutes);
        final long refund = RefundPolicy.refund(d.booking().cabin(), minutes, d.fares(), d.taxes());
        return new RefundQuote(percent, refund, d.booking().total());
    }

    /** Yolcu iptali: iade kurala göre hesaplanır, koltuklar boşa çıkar. */
    public RefundQuote cancel(BookingKey key) {
        return airline.db().transaction(c -> cancel(c, resolve(c, key), "PASSENGER"));
    }

    private RefundQuote cancel(Connection c, BookingDetail d, String by) throws SQLException {
        requireActive(d, airline.now());
        final RefundQuote quote = refundQuote(d);
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE bookings SET status = 'CANCELLED', refund = ?, cancelled_at = ?, cancelled_by = ?
                WHERE id = ? AND status = 'CONFIRMED'""")) {
            ps.setLong(1, quote.refund());
            ps.setString(2, Records.ts(airline.now()));
            ps.setString(3, by);
            ps.setLong(4, d.booking().id());
            if (ps.executeUpdate() != 1)
                throw new AirlineException("alreadyCancelled");
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET active = 0 WHERE booking_id = ?")) {
            ps.setLong(1, d.booking().id());
            ps.executeUpdate();
        }
        return quote;
    }

    /** Rezervasyon iptal edilmemiş, uçuş iptal edilmemiş ve kalkmamış olmalı. */
    private static void requireActive(BookingDetail d, LocalDateTime now) {
        if (d.cancelled())
            throw new AirlineException("alreadyCancelled");
        if (d.flight().status() == FlightStatus.CANCELLED)
            throw new AirlineException("flightCancelled");
        if (d.flight().departed(now))
            throw new AirlineException("flightDeparted");
    }

    // ---------------------------------------------------------------- personel tarafı

    /** Kod, ad, soyad ya da e-postaya göre arama; boş aramada en yeni 200 rezervasyon. */
    public List<BookingRow> search(Session session, String query) {
        Airline.requireStaff(session);
        final String q = query == null ? "" : query.strip();
        return airline.db().read(c -> {
            final String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            final String sql = """
                    SELECT b.*, (SELECT first_name || ' ' || last_name FROM tickets WHERE booking_id = b.id ORDER BY id LIMIT 1) AS lead,
                           (SELECT COUNT(*) FROM tickets WHERE booking_id = b.id) AS pax
                    FROM bookings b
                    """ + (q.isEmpty() ? "" : """
                    WHERE b.pnr = ? OR b.contact_email LIKE ? ESCAPE '\\'
                       OR EXISTS (SELECT 1 FROM tickets t WHERE t.booking_id = b.id
                                  AND (t.last_name LIKE ? ESCAPE '\\' OR t.first_name || ' ' || t.last_name LIKE ? ESCAPE '\\'))
                    """) + " ORDER BY b.id DESC LIMIT 200";
            final List<BookingRow> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (!q.isEmpty()) {
                    ps.setString(1, q.toUpperCase(java.util.Locale.ROOT));
                    ps.setString(2, like);
                    ps.setString(3, like);
                    ps.setString(4, like);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final Booking b = Records.booking(rs);
                        rows.add(new BookingRow(b, null, rs.getString("lead"), rs.getInt("pax")));
                    }
                }
            }
            final List<BookingRow> withFlights = new ArrayList<>(rows.size());
            for (BookingRow r : rows)
                withFlights.add(new BookingRow(r.booking(), FlightService.findFlight(c, r.booking().flightId()).orElseThrow(),
                        r.leadName(), r.passengers()));
            return withFlights;
        });
    }

    public BookingDetail detail(Session session, long bookingId) {
        Airline.requireStaff(session);
        return airline.db().read(c -> detail(c, bookingId));
    }

    public RefundQuote refundQuote(Session session, long bookingId) {
        final BookingDetail d = detail(session, bookingId);
        requireActive(d, airline.now());
        return refundQuote(d);
    }

    /** Personel iptali (yolcu telefonla/gişeden istediğinde): aynı iade kuralı uygulanır. */
    public RefundQuote cancel(Session session, long bookingId) {
        Airline.requireStaff(session);
        return airline.db().transaction(c -> cancel(c, detail(c, bookingId), "STAFF"));
    }

    // ---------------------------------------------------------------- yardımcılar

    static BookingDetail detail(Connection c, long bookingId) throws SQLException {
        final Booking booking;
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM bookings WHERE id = ?")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new AirlineException("bookingNotFound");
                booking = Records.booking(rs);
            }
        }
        final Flight flight = FlightService.findFlight(c, booking.flightId()).orElseThrow();
        final List<Ticket> tickets = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM tickets WHERE booking_id = ? ORDER BY id")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    tickets.add(Records.ticket(rs));
            }
        }
        tickets.sort(Comparator.comparingLong(Ticket::id));
        return new BookingDetail(booking, flight, List.copyOf(tickets));
    }

    /** Rastgele PNR ya da bilet numarası; çok düşük ihtimalle var olan bir kod çıkarsa yenisi üretilir. */
    private String uniqueCode(Connection c, String existsSql, boolean pnr) throws SQLException {
        for (int attempt = 0; attempt < 20; attempt++) {
            final String code = pnr ? Codes.pnr(airline.random()) : Codes.ticketNumber(airline.random());
            try (PreparedStatement ps = c.prepareStatement(existsSql)) {
                ps.setString(1, code);
                if (!ps.executeQuery().next())
                    return code;
            }
        }
        throw new IllegalStateException("Benzersiz kod üretilemedi");
    }

    static void insertTicket(Connection c, long bookingId, long flightId, String ticketNo, String firstName,
            String lastName, PassengerType type, String seat, long fare, long tax, long seatFee) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tickets(booking_id, flight_id, ticket_no, first_name, last_name, type, seat, fare, tax, seat_fee)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setLong(1, bookingId);
            ps.setLong(2, flightId);
            ps.setString(3, ticketNo);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setString(6, type.name());
            ps.setString(7, seat);
            ps.setLong(8, fare);
            ps.setLong(9, tax);
            ps.setLong(10, seatFee);
            ps.executeUpdate();
        }
    }
}
