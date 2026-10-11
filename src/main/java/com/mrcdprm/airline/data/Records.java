package com.mrcdprm.airline.data;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.BookingStatus;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.core.Names;
import com.mrcdprm.airline.core.PassengerType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Tablo satırlarının Java karşılıkları (record) ve ResultSet'ten okuma. Veritabanı dosyası elle
 * değiştirilmiş olabilir; bilinmeyen havalimanı, uçak tipi ya da durum değeri okunurken hata verilir.
 */
public final class Records {

    /** Zaman sütunları hep aynı biçimde yazılır; böylece metin olarak sıralama tarih sıralamasıyla aynıdır. */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Records() {
    }

    public static String ts(LocalDateTime time) {
        return time == null ? null : time.withNano(0).format(TS);
    }

    public static LocalDateTime time(String text) {
        if (text == null)
            return null;
        try {
            return LocalDateTime.parse(text, TS);
        } catch (DateTimeParseException e) {
            throw new DataException("Geçersiz tarih: " + text);
        }
    }

    public record Staff(long id, String username, String fullName, String passwordHash, int failedAttempts,
            LocalDateTime lockedUntil) {
    }

    public record Flight(long id, String number, Airport origin, Airport destination, LocalDateTime departure,
            LocalDateTime arrival, LocalDateTime scheduledDeparture, AircraftType aircraft, long economyFare,
            long businessFare, FlightStatus status, String gate) {

        public long baseFare(CabinClass cabin) {
            return cabin == CabinClass.BUSINESS ? businessFare : economyFare;
        }

        public int minutes() {
            return (int) Duration.between(departure, arrival).toMinutes();
        }

        /** Rötar süresi (dakika); rötar yoksa 0. */
        public long delayMinutes() {
            return Math.max(0, Duration.between(scheduledDeparture, departure).toMinutes());
        }

        public boolean departed(LocalDateTime now) {
            return status != FlightStatus.CANCELLED && !departure.isAfter(now);
        }

        /** Kapıya gelme (biniş) saati: kalkıştan 30 dakika önce. */
        public LocalDateTime boardingTime() {
            return departure.minusMinutes(30);
        }
    }

    public record Booking(long id, String pnr, long flightId, CabinClass cabin, String email, String phone,
            BookingStatus status, long total, long refund, LocalDateTime createdAt, LocalDateTime cancelledAt,
            String cancelledBy) {
    }

    public record Ticket(long id, long bookingId, long flightId, String ticketNo, String firstName, String lastName,
            PassengerType type, String seat, long fare, long tax, long seatFee, LocalDateTime checkedInAt,
            Integer boardingNo, boolean active) {

        public String ticketName() {
            return Names.ticketName(firstName, lastName);
        }

        public String fullName() {
            return firstName + " " + lastName;
        }

        public boolean checkedIn() {
            return checkedInAt != null;
        }

        public long total() {
            return fare + tax + seatFee;
        }
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataException("Geçersiz değer: " + value);
        }
    }

    public static Staff staff(ResultSet rs) throws SQLException {
        return new Staff(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"),
                rs.getString("password_hash"), rs.getInt("failed_attempts"), time(rs.getString("locked_until")));
    }

    public static Flight flight(ResultSet rs) throws SQLException {
        return new Flight(rs.getLong("id"), rs.getString("number"),
                Airport.of(rs.getString("origin")).orElseThrow(() -> new DataException("Bilinmeyen havalimanı")),
                Airport.of(rs.getString("destination")).orElseThrow(() -> new DataException("Bilinmeyen havalimanı")),
                time(rs.getString("departure")), time(rs.getString("arrival")),
                time(rs.getString("scheduled_departure")),
                AircraftType.of(rs.getString("aircraft")).orElseThrow(() -> new DataException("Bilinmeyen uçak")),
                rs.getLong("economy_fare"), rs.getLong("business_fare"),
                enumValue(FlightStatus.class, rs.getString("status")), rs.getString("gate"));
    }

    public static Booking booking(ResultSet rs) throws SQLException {
        return new Booking(rs.getLong("id"), rs.getString("pnr"), rs.getLong("flight_id"),
                enumValue(CabinClass.class, rs.getString("cabin")), rs.getString("contact_email"),
                rs.getString("contact_phone"), enumValue(BookingStatus.class, rs.getString("status")),
                rs.getLong("total"), rs.getLong("refund"), time(rs.getString("created_at")),
                time(rs.getString("cancelled_at")), rs.getString("cancelled_by"));
    }

    public static Ticket ticket(ResultSet rs) throws SQLException {
        final int boardingValue = rs.getInt("boarding_no");
        final Integer boarding = rs.wasNull() ? null : boardingValue;
        return new Ticket(rs.getLong("id"), rs.getLong("booking_id"), rs.getLong("flight_id"), rs.getString("ticket_no"),
                rs.getString("first_name"), rs.getString("last_name"),
                enumValue(PassengerType.class, rs.getString("type")), rs.getString("seat"), rs.getLong("fare"),
                rs.getLong("tax"), rs.getLong("seat_fee"), time(rs.getString("checked_in_at")),
                boarding, rs.getInt("active") == 1);
    }
}
