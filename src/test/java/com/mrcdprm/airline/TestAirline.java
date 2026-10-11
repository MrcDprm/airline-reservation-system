package com.mrcdprm.airline;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.PassengerType;
import com.mrcdprm.airline.data.Database;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.service.Airline;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import com.mrcdprm.airline.service.BookingService.BookingRequest;
import com.mrcdprm.airline.service.BookingService.PassengerInput;
import com.mrcdprm.airline.service.FlightService.NewFlight;
import com.mrcdprm.airline.service.Session;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Testler için bellek içi veritabanı, ileri alınabilir saat ve hazır yönetici oturumu. */
final class TestAirline implements AutoCloseable {

    /** Testin "şimdi"si: 1 Mart 2027 09:00. */
    static final LocalDateTime START = LocalDateTime.of(2027, 3, 1, 9, 0);

    static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneId.of("Europe/Istanbul");

        MutableClock(LocalDateTime start) {
            instant = start.atZone(zone).toInstant();
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        void set(LocalDateTime time) {
            instant = time.atZone(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    final Database db = Database.inMemory();
    final MutableClock clock = new MutableClock(START);
    final Airline airline = new Airline(db, clock);
    final Session admin = airline.auth().createFirstAdmin("yonetici", "Selin Arslan", "Guclu.Sifre2026");

    /** IST-ESB, A320, belirtilen kalkış saatinde tek uçuş. */
    Flight flight(LocalDateTime departure) {
        return airline.flights().create(admin, new NewFlight(Airport.IST, Airport.ESB, departure.toLocalDate(),
                departure.toLocalTime(), AircraftType.A320, 100_000, 260_000, 1)).get(0);
    }

    static PassengerInput adult(String first, String last, String seat) {
        return new PassengerInput(first, last, PassengerType.ADULT, seat);
    }

    static PassengerInput child(String first, String last, String seat) {
        return new PassengerInput(first, last, PassengerType.CHILD, seat);
    }

    BookingRequest request(Flight f, CabinClass cabin, PassengerInput... passengers) {
        return new BookingRequest(f.id(), cabin, List.of(passengers), "ayse@example.com", "0532 123 45 67");
    }

    /** Teklifteki fiyatla rezervasyon yapar (kullanıcının onay ekranındaki akış). */
    BookingDetail book(Flight f, CabinClass cabin, PassengerInput... passengers) {
        final BookingRequest request = request(f, cabin, passengers);
        final long total = airline.bookings().quote(f.id(), cabin, request.passengers()).total();
        return airline.bookings().book(request, total);
    }

    @Override
    public void close() {
        db.close();
    }
}
