package com.mrcdprm.airline.service;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Codes;
import com.mrcdprm.airline.core.Names;
import com.mrcdprm.airline.core.PassengerType;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.core.RefundPolicy;
import com.mrcdprm.airline.core.Seat;
import com.mrcdprm.airline.data.Records;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * İlk açılışta isteğe bağlı örnek veri: 19 hatta gidiş-dönüş tarifeli seferler (1 hafta öncesinden 3 hafta sonrasına),
 * doluluğu kalkışa yaklaştıkça artan rezervasyonlar, check-in yapılmış yolcular, rötarlar ve iptaller.
 * Hızlı olsun diye servis yerine doğrudan toplu SQL ile ve tek işlemde yazılır.
 */
public final class SampleData {

    public static final String STAFF_USERNAME = "operasyon";
    public static final String STAFF_PASSWORD = "Ucus.Demo2026";
    public static final String DEMO_PNR = "DEMA26";
    public static final String DEMO_SURNAME = "Yılmaz";

    private record Route(Airport from, Airport to, AircraftType aircraft, String... times) {
    }

    private record SampleFlight(long id, AircraftType aircraft, LocalDateTime departure, long economy, long business) {
    }

    private static final List<Route> ROUTES = List.of(
            new Route(Airport.IST, Airport.ESB, AircraftType.A321, "07:00", "13:30", "19:45"),
            new Route(Airport.IST, Airport.ADB, AircraftType.A321, "08:15", "18:30"),
            new Route(Airport.IST, Airport.AYT, AircraftType.A321, "09:00", "16:40"),
            new Route(Airport.IST, Airport.TZX, AircraftType.B738, "10:20"),
            new Route(Airport.IST, Airport.DLM, AircraftType.A320, "11:10"),
            new Route(Airport.IST, Airport.BJV, AircraftType.A320, "12:05"),
            new Route(Airport.IST, Airport.GZT, AircraftType.B738, "14:15"),
            new Route(Airport.IST, Airport.DIY, AircraftType.B738, "06:40"),
            new Route(Airport.IST, Airport.ERZ, AircraftType.A320, "15:30"),
            new Route(Airport.IST, Airport.VAN, AircraftType.A320, "17:20"),
            new Route(Airport.SAW, Airport.ESB, AircraftType.A320, "08:50"),
            new Route(Airport.SAW, Airport.ADB, AircraftType.B738, "12:40"),
            new Route(Airport.SAW, Airport.AYT, AircraftType.B738, "07:30", "20:10"),
            new Route(Airport.SAW, Airport.TZX, AircraftType.A320, "09:40"),
            new Route(Airport.SAW, Airport.ASR, AircraftType.A320, "16:00"),
            new Route(Airport.SAW, Airport.KYA, AircraftType.A320, "18:05"),
            new Route(Airport.SAW, Airport.SZF, AircraftType.A320, "11:35"),
            new Route(Airport.ESB, Airport.ADB, AircraftType.A320, "10:05"),
            new Route(Airport.ESB, Airport.AYT, AircraftType.A320, "14:50"));

    private static final String[] MALE = {"Ahmet", "Mehmet", "Mustafa", "Ali", "Hüseyin", "Hasan", "İbrahim", "Murat",
        "Emre", "Burak", "Can", "Kerem", "Ozan", "Serkan", "Tolga", "Yusuf", "Ömer", "Barış", "Onur", "Kaan", "Eren",
        "Arda", "Cem", "Volkan", "Selim"};
    private static final String[] FEMALE = {"Ayşe", "Fatma", "Emine", "Zeynep", "Elif", "Merve", "Esra", "Büşra",
        "Selin", "Deniz", "Ece", "Gizem", "İrem", "Derya", "Seda", "Özge", "Nur", "Gül", "Yasemin", "Melis", "Ceren",
        "Damla", "Ebru", "Pınar", "Şule"};
    private static final String[] SURNAMES = {"Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız", "Yıldırım",
        "Öztürk", "Aydın", "Özdemir", "Arslan", "Doğan", "Kılıç", "Aslan", "Çetin", "Kara", "Koç", "Kurt", "Özkan",
        "Şimşek", "Polat", "Erdoğan", "Güneş", "Aksoy", "Tekin", "Ünal", "Korkmaz", "Bulut", "Keskin", "Acar"};

    private SampleData() {
    }

    public static void load(Airline airline) {
        airline.auth().addStaff(STAFF_USERNAME, "Deniz Kaya", STAFF_PASSWORD);
        final Random random = new Random(2026);
        final LocalDateTime now = airline.now();
        final LocalDate today = airline.today();
        airline.db().transaction(c -> {
            final List<SampleFlight> flights = flights(c, random, now, today);
            final Set<String> pnrs = new HashSet<>(Set.of(DEMO_PNR));
            final Set<String> ticketNos = new HashSet<>();
            try (PreparedStatement booking = c.prepareStatement("""
                    INSERT INTO bookings(pnr, flight_id, cabin, contact_email, contact_phone, status, total, refund,
                      created_at, cancelled_at, cancelled_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement ticket = c.prepareStatement("""
                    INSERT INTO tickets(booking_id, flight_id, ticket_no, first_name, last_name, type, seat, fare, tax,
                      seat_fee, checked_in_at, boarding_no, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                for (SampleFlight f : flights)
                    bookings(f, random, now, booking, ticket, pnrs, ticketNos);
                ticket.executeBatch();
            }
            cancelSomeFlights(c, random, now, flights);
            demoBooking(c, now, flights, ticketNos, random);
            return null;
        });
    }

    // ---------------------------------------------------------------- uçuşlar

    private static List<SampleFlight> flights(Connection c, Random random, LocalDateTime now, LocalDate today)
            throws SQLException {
        final List<SampleFlight> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO flights(number, origin, destination, departure, arrival, scheduled_departure, aircraft,
                  economy_fare, business_fare, status, gate, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                Statement.RETURN_GENERATED_KEYS)) {
            for (int day = -6; day <= 20; day++) {
                final LocalDate date = today.plusDays(day);
                int number = 100;
                for (Route route : ROUTES) {
                    for (String time : route.times()) {
                        final LocalDateTime out = date.atTime(LocalTime.parse(time));
                        list.add(insert(ps, random, now, number, route.from(), route.to(), route.aircraft(), out));
                        final LocalDateTime back = out.plusMinutes(Airport.flightMinutes(route.from(), route.to()) + 50);
                        list.add(insert(ps, random, now, number + 1, route.to(), route.from(), route.aircraft(), back));
                        number += 2;
                    }
                }
            }
        }
        return list;
    }

    private static SampleFlight insert(PreparedStatement ps, Random random, LocalDateTime now, int number, Airport from,
            Airport to, AircraftType aircraft, LocalDateTime scheduled) throws SQLException {
        final double km = Airport.distanceKm(from, to);
        final long economy = Pricing.suggestedEconomyBase(km);
        final long business = Pricing.suggestedBusinessBase(km);
        // yakın tarihli seferlerin bir kısmı rötarlı
        final boolean near = scheduled.isAfter(now.minusDays(6)) && scheduled.isBefore(now.plusDays(2));
        final int delay = near && random.nextInt(100) < 6 ? 15 + 5 * random.nextInt(20) : 0;
        final LocalDateTime departure = scheduled.plusMinutes(delay);
        ps.setString(1, Codes.CARRIER + " " + number);
        ps.setString(2, from.code());
        ps.setString(3, to.code());
        ps.setString(4, Records.ts(departure));
        ps.setString(5, Records.ts(departure.plusMinutes(Airport.flightMinutes(from, to))));
        ps.setString(6, Records.ts(scheduled));
        ps.setString(7, aircraft.name());
        ps.setLong(8, economy);
        ps.setLong(9, business);
        ps.setString(10, delay > 0 ? "DELAYED" : "SCHEDULED");
        ps.setString(11, "ABCDEF".charAt(random.nextInt(6)) + String.valueOf(1 + random.nextInt(20)));
        ps.setString(12, Records.ts(now.minusDays(30)));
        ps.executeUpdate();
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return new SampleFlight(keys.getLong(1), aircraft, departure, economy, business);
        }
    }

    // ---------------------------------------------------------------- rezervasyonlar

    private static void bookings(SampleFlight f, Random random, LocalDateTime now, PreparedStatement booking,
            PreparedStatement ticket, Set<String> pnrs, Set<String> ticketNos) throws SQLException {
        final boolean departed = !f.departure().isAfter(now);
        final double daysAhead = Duration.between(now, f.departure()).toHours() / 24.0;
        for (CabinClass cabin : CabinClass.values()) {
            final double target = departed ? 0.72 + random.nextDouble() * 0.25
                    : Math.max(0.03, Math.min(0.96, 0.9 - daysAhead * 0.04 + (random.nextDouble() - 0.5) * 0.3));
            final List<Seat> seats = seatOrder(f.aircraft(), cabin, random);
            final int capacity = seats.size();
            final int count = (int) Math.round(capacity * target * (cabin == CabinClass.BUSINESS ? 0.8 : 1));
            int used = 0;
            int boarding = 0;
            while (used < count) {
                final int size = Math.min(count - used, groupSize(random));
                final List<Seat> group = seats.subList(used, used + size);
                // Satış zamanı: geçmiş uçuşta kalkıştan 1-30 gün önce, gelecektekinde son 20 gün içinde
                final LocalDateTime created = departed
                        ? f.departure().minusMinutes(60L * 24 + random.nextInt(29 * 24 * 60))
                        : now.minusMinutes(10 + random.nextInt(20 * 24 * 60));
                final long hours = Duration.between(created, f.departure()).toHours();
                final long adult = Pricing.adultFare(cabin == CabinClass.BUSINESS ? f.business() : f.economy(), used,
                        capacity, hours);
                final boolean cancelled = random.nextInt(100) < 4;
                final String surname = pick(random, SURNAMES);
                long total = 0;
                long fares = 0;
                final List<Object[]> rows = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    final boolean female = random.nextBoolean();
                    final boolean child = size >= 3 && i == size - 1 && random.nextBoolean();
                    final String last = i == 0 || random.nextInt(100) < 70 ? surname : pick(random, SURNAMES);
                    final PassengerType type = child ? PassengerType.CHILD : PassengerType.ADULT;
                    final long fare = Pricing.fare(adult, type);
                    final long seatFee = Pricing.seatFee(group.get(i));
                    total += fare + Pricing.TAX_PER_PASSENGER + seatFee;
                    fares += fare;
                    rows.add(new Object[] {pick(random, female ? FEMALE : MALE), last, type, group.get(i), fare, seatFee});
                }
                final LocalDateTime cancelledAt = cancelled
                        ? created.plusMinutes(random.nextInt((int) Math.max(1, Duration.between(created, departed ? f.departure() : now).toMinutes())))
                        : null;
                final long refund = cancelled ? RefundPolicy.refund(cabin,
                        Duration.between(cancelledAt, f.departure()).toMinutes(), fares, Pricing.TAX_PER_PASSENGER * size) : 0;
                final Object[] lead = rows.get(0);
                booking.setString(1, unique(pnrs, () -> Codes.pnr(random)));
                booking.setLong(2, f.id());
                booking.setString(3, cabin.name());
                booking.setString(4, email((String) lead[0], (String) lead[1]));
                booking.setString(5, "+905" + (300000000 + random.nextInt(600000000)));
                booking.setString(6, cancelled ? "CANCELLED" : "CONFIRMED");
                booking.setLong(7, total);
                booking.setLong(8, refund);
                booking.setString(9, Records.ts(created));
                booking.setString(10, Records.ts(cancelledAt));
                booking.setString(11, cancelled ? "PASSENGER" : null);
                booking.executeUpdate();
                final long bookingId;
                try (ResultSet keys = booking.getGeneratedKeys()) {
                    keys.next();
                    bookingId = keys.getLong(1);
                }
                // check-in: kalkmış uçuşlarda neredeyse herkes, son 24 saattekilerde yaklaşık yarısı
                final boolean checkInOpen = !departed && daysAhead < 1;
                final boolean checkedIn = !cancelled && (departed ? random.nextInt(100) < 97 : checkInOpen && random.nextBoolean());
                for (Object[] r : rows) {
                    ticket.setLong(1, bookingId);
                    ticket.setLong(2, f.id());
                    ticket.setString(3, unique(ticketNos, () -> Codes.ticketNumber(random)));
                    ticket.setString(4, (String) r[0]);
                    ticket.setString(5, (String) r[1]);
                    ticket.setString(6, ((PassengerType) r[2]).name());
                    ticket.setString(7, ((Seat) r[3]).code());
                    ticket.setLong(8, (Long) r[4]);
                    ticket.setLong(9, Pricing.TAX_PER_PASSENGER);
                    ticket.setLong(10, (Long) r[5]);
                    ticket.setString(11, checkedIn ? Records.ts(min(now, f.departure().minusMinutes(70 + random.nextInt(20 * 60)))) : null);
                    if (checkedIn)
                        ticket.setInt(12, ++boarding);
                    else
                        ticket.setNull(12, java.sql.Types.INTEGER);
                    ticket.setInt(13, cancelled ? 0 : 1);
                    ticket.addBatch();
                }
                // iptal edilen rezervasyonun koltukları boşalır; sonraki rezervasyon aynı koltukları alabilir
                if (!cancelled)
                    used += size;
            }
        }
    }

    /** Koltuk sırası: sıralar karışık, sıra içinde soldan sağa; böylece gruplar çoğunlukla yan yana oturur. */
    private static List<Seat> seatOrder(AircraftType aircraft, CabinClass cabin, Random random) {
        final List<Integer> rows = new ArrayList<>(aircraft.seats(cabin).stream().map(Seat::row).distinct().toList());
        Collections.shuffle(rows, random);
        final List<Seat> order = new ArrayList<>();
        for (int row : rows)
            aircraft.seats(cabin).stream().filter(s -> s.row() == row).forEach(order::add);
        return order;
    }

    private static int groupSize(Random random) {
        final int r = random.nextInt(100);
        return r < 45 ? 1 : r < 80 ? 2 : r < 92 ? 3 : 4;
    }

    /** İki sefer havayolu tarafından iptal edilmiş: yolcularına tam iade. */
    private static void cancelSomeFlights(Connection c, Random random, LocalDateTime now, List<SampleFlight> flights)
            throws SQLException {
        final List<SampleFlight> soon = flights.stream()
                .filter(f -> f.departure().isAfter(now.plusHours(6)) && f.departure().isBefore(now.plusDays(4))).toList();
        for (int i = 0; i < 2 && !soon.isEmpty(); i++) {
            final SampleFlight f = soon.get(random.nextInt(soon.size()));
            final String at = Records.ts(now.minusHours(1 + random.nextInt(12)));
            try (PreparedStatement ps = c.prepareStatement("UPDATE flights SET status = 'CANCELLED', departure = scheduled_departure WHERE id = ?")) {
                ps.setLong(1, f.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE bookings SET status = 'CANCELLED', refund = total, cancelled_at = ?, cancelled_by = 'AIRLINE'
                    WHERE flight_id = ? AND status = 'CONFIRMED'""")) {
                ps.setString(1, at);
                ps.setLong(2, f.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET active = 0 WHERE flight_id = ?")) {
                ps.setLong(1, f.id());
                ps.executeUpdate();
            }
        }
    }

    /**
     * README'deki deneme rezervasyonu (DEMA26 / Yılmaz): 4-22 saat içinde kalkan bir uçuşta 2 yetişkin + 1 çocuk,
     * yan yana koltuklar, check-in yapılmamış. Böylece check-in ve biniş kartı hemen denenebilir.
     */
    private static void demoBooking(Connection c, LocalDateTime now, List<SampleFlight> flights, Set<String> ticketNos,
            Random random) throws SQLException {
        final List<FlightService.FlightRow> candidates = FlightService.rows(c,
                "WHERE f.departure > ? AND f.departure < ? AND f.status = 'SCHEDULED' ORDER BY f.departure",
                Records.ts(now.plusHours(4)), Records.ts(now.plusHours(22)));
        final FlightService.FlightRow row = candidates.isEmpty()
                ? FlightService.rows(c, "WHERE f.departure > ? AND f.status = 'SCHEDULED' ORDER BY f.departure LIMIT 1",
                        Records.ts(now.plusHours(4))).get(0)
                : candidates.get(0);
        final AircraftType aircraft = row.flight().aircraft();
        final Set<String> taken = FlightService.occupied(c, row.flight().id());
        // arka sıralardan başlayarak üç koltuğu (ABC ya da DEF) boş olan ilk sıra
        List<Seat> chosen = null;
        for (int r = aircraft.lastRow(); r > aircraft.businessRows() && chosen == null; r--) {
            for (String side : new String[] {"ABC", "DEF"}) {
                final int rowNo = r;
                final List<Seat> seats = side.chars().mapToObj(ch -> aircraft.seat(rowNo + String.valueOf((char) ch)).orElseThrow()).toList();
                if (seats.stream().noneMatch(s -> taken.contains(s.code()) || s.extraLegroom())) {
                    chosen = seats;
                    break;
                }
            }
        }
        if (chosen == null)
            return;
        final long hours = Duration.between(now.minusDays(3), row.flight().departure()).toHours();
        final long adult = Pricing.adultFare(row.flight().economyFare(), row.soldEconomy(), aircraft.capacity(CabinClass.ECONOMY), hours);
        final String[][] people = {{"Ahmet", "ADULT"}, {"Elif", "ADULT"}, {"Can", "CHILD"}};
        long total = 0;
        for (String[] p : people)
            total += Pricing.fare(adult, PassengerType.valueOf(p[1])) + Pricing.TAX_PER_PASSENGER;
        final long bookingId;
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO bookings(pnr, flight_id, cabin, contact_email, contact_phone, total, created_at)
                VALUES (?, ?, 'ECONOMY', ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, DEMO_PNR);
            ps.setLong(2, row.flight().id());
            ps.setString(3, "ahmet.yilmaz@example.com");
            ps.setString(4, "+905321234567");
            ps.setLong(5, total);
            ps.setString(6, Records.ts(now.minusDays(3)));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                bookingId = keys.getLong(1);
            }
        }
        for (int i = 0; i < people.length; i++) {
            final PassengerType type = PassengerType.valueOf(people[i][1]);
            BookingService.insertTicket(c, bookingId, row.flight().id(), unique(ticketNos, () -> Codes.ticketNumber(random)),
                    people[i][0], DEMO_SURNAME, type, chosen.get(i).code(), Pricing.fare(adult, type),
                    Pricing.TAX_PER_PASSENGER, 0);
        }
    }

    // ---------------------------------------------------------------- yardımcılar

    private static String pick(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static String unique(Set<String> used, java.util.function.Supplier<String> generator) {
        String code;
        do {
            code = generator.get();
        } while (!used.add(code));
        return code;
    }

    private static String email(String first, String last) {
        return (Names.fold(first) + "." + Names.fold(last)).toLowerCase(Locale.ROOT).replace(" ", "") + "@example.com";
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
