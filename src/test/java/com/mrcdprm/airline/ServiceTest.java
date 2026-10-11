package com.mrcdprm.airline;

import static com.mrcdprm.airline.TestAirline.START;
import static com.mrcdprm.airline.TestAirline.adult;
import static com.mrcdprm.airline.TestAirline.child;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.BookingStatus;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Codes;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.service.Airline;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import com.mrcdprm.airline.service.BookingService.BookingKey;
import com.mrcdprm.airline.service.BookingService.BookingRequest;
import com.mrcdprm.airline.service.BookingService.CheckInState;
import com.mrcdprm.airline.service.BookingService.RefundQuote;
import com.mrcdprm.airline.service.FlightService.NewFlight;
import com.mrcdprm.airline.service.FlightService.Offer;
import com.mrcdprm.airline.service.ReportService.Dashboard;
import com.mrcdprm.airline.service.SampleData;
import com.mrcdprm.airline.service.Session;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Servis katmanı: giriş, uçuş yönetimi, satın alma, koltuk, check-in, iptal ve iadeler. */
class ServiceTest {

    private final TestAirline t = new TestAirline();
    private final Airline airline = t.airline;
    /** Varsayılan uçuş: 10 gün sonra 10:00. */
    private final Flight flight = t.flight(START.plusDays(10).withHour(10));

    @AfterEach
    void close() {
        t.close();
    }

    private static String code(Runnable action) {
        return assertThrows(AirlineException.class, action::run).code();
    }

    // ---------------------------------------------------------------- giriş

    @Test
    void loginAndLockout() {
        final Session s = airline.auth().login("YONETICI ", "Guclu.Sifre2026");
        assertEquals("Selin Arslan", s.fullName());
        assertEquals("invalidLogin", code(() -> airline.auth().login("kimse", "Guclu.Sifre2026")));
        for (int i = 0; i < 4; i++)
            assertEquals("invalidLogin", code(() -> airline.auth().login("yonetici", "yanlis")));
        assertEquals("accountLocked", code(() -> airline.auth().login("yonetici", "yanlis")));
        // kilitliyken doğru şifre de kabul edilmez
        assertEquals("accountLocked", code(() -> airline.auth().login("yonetici", "Guclu.Sifre2026")));
        t.clock.advance(Duration.ofMinutes(16));
        assertNotNull(airline.auth().login("yonetici", "Guclu.Sifre2026"));
    }

    @Test
    void setupOnlyOnceAndPasswordRules() {
        assertEquals("setupDone", code(() -> airline.auth().createFirstAdmin("ikinci", "Ali Veli", "Guclu.Sifre2026")));
        assertEquals("wrongPassword", code(() -> airline.auth().changePassword(t.admin, "yanlis", "Yeni.Sifre2027")));
        assertEquals("weakPassword", code(() -> airline.auth().changePassword(t.admin, "Guclu.Sifre2026", "kisa")));
        airline.auth().changePassword(t.admin, "Guclu.Sifre2026", "Yeni.Sifre2027");
        assertNotNull(airline.auth().login("yonetici", "Yeni.Sifre2027"));
    }

    @Test
    void staffOperationsNeedSession() {
        assertEquals("notAuthorized", code(() -> airline.flights().cancel(null, flight.id())));
        assertEquals("notAuthorized", code(() -> airline.bookings().search(null, "")));
        assertEquals("notAuthorized", code(() -> airline.reports().dashboard(null)));
        assertEquals("notAuthorized", code(() -> airline.flights().manifest(null, flight.id())));
    }

    // ---------------------------------------------------------------- uçuşlar

    @Test
    void createRepeatingFlightsAndSearch() {
        final List<Flight> created = airline.flights().create(t.admin, new NewFlight(Airport.SAW, Airport.AYT,
                START.toLocalDate().plusDays(2), LocalTime.of(7, 30), AircraftType.B738, 90_000, 230_000, 5));
        assertEquals(5, created.size());
        assertTrue(created.stream().allMatch(f -> f.number().equals(created.get(0).number())), "tarifeli sefer aynı numara");
        assertTrue(Codes.validFlightNumber(created.get(0).number()));
        assertEquals(created.get(0).departure().plusMinutes(Airport.flightMinutes(Airport.SAW, Airport.AYT)), created.get(0).arrival());
        // aynı numara aynı saatte iki kez eklenemez; hiçbiri eklenmez (tek işlem)
        assertEquals("flightExists", code(() -> airline.flights().create(t.admin, new NewFlight(Airport.SAW, Airport.AYT,
                START.toLocalDate().plusDays(1), LocalTime.of(7, 30), AircraftType.B738, 90_000, 230_000, 3))));
        assertEquals(5, airline.flights().list(t.admin, START.toLocalDate(), START.toLocalDate().plusDays(30), "saw", null).size());

        // 5 gün sonrası: son dakika zammı yok, uçak boş -> taban ücret
        final List<Offer> offers = airline.flights().search(Airport.SAW, Airport.AYT, START.toLocalDate().plusDays(5),
                CabinClass.ECONOMY, 2);
        assertEquals(1, offers.size());
        assertEquals(90_000, offers.get(0).adultFare());
        assertEquals(162, offers.get(0).seatsLeft());
        assertTrue(offers.get(0).bookable());
        assertTrue(airline.flights().search(Airport.AYT, Airport.SAW, START.toLocalDate().plusDays(5), CabinClass.ECONOMY, 1).isEmpty());
    }

    @Test
    void invalidFlightsAndSearches() {
        assertEquals("sameAirport", code(() -> airline.flights().search(Airport.IST, Airport.IST, START.toLocalDate(), CabinClass.ECONOMY, 1)));
        assertEquals("pastDate", code(() -> airline.flights().search(Airport.IST, Airport.ESB, START.toLocalDate().minusDays(1), CabinClass.ECONOMY, 1)));
        assertEquals("passengerCount", code(() -> airline.flights().search(Airport.IST, Airport.ESB, START.toLocalDate(), CabinClass.ECONOMY, 7)));
        assertEquals("departureTooSoon", code(() -> t.flight(START.plusMinutes(30))));
        assertEquals("businessCheaper", code(() -> airline.flights().create(t.admin, new NewFlight(Airport.IST, Airport.ADB,
                START.toLocalDate().plusDays(3), LocalTime.NOON, AircraftType.A320, 200_000, 100_000, 1))));
        assertEquals("repeatDays", code(() -> airline.flights().create(t.admin, new NewFlight(Airport.IST, Airport.ADB,
                START.toLocalDate().plusDays(3), LocalTime.NOON, AircraftType.A320, 100_000, 200_000, 61))));
    }

    @Test
    void priceRisesAsFlightFills() {
        final long before = airline.flights().search(Airport.IST, Airport.ESB, flight.departure().toLocalDate(),
                CabinClass.BUSINESS, 1).get(0).adultFare();
        // business 12 koltuk: 6 satılınca doluluk %50
        t.book(flight, CabinClass.BUSINESS, adult("Ali", "Kaya", "1A"), adult("Can", "Kaya", "1C"), adult("Ece", "Kaya", "1D"));
        t.book(flight, CabinClass.BUSINESS, adult("Ali", "Demir", "2A"), adult("Can", "Demir", "2C"), adult("Ece", "Demir", "2D"));
        final long after = airline.flights().search(Airport.IST, Airport.ESB, flight.departure().toLocalDate(),
                CabinClass.BUSINESS, 1).get(0).adultFare();
        assertEquals(260_000, before);
        assertEquals(299_000, after);
    }

    // ---------------------------------------------------------------- satın alma

    @Test
    void bookingCreatesPnrTicketsAndOccupiesSeats() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"),
                child("Can", "Yılmaz", "14B"), adult("Mert", "Yılmaz", "11A"));
        assertNotNull(Codes.normalizePnr(d.booking().pnr()));
        assertEquals(3, d.tickets().size());
        assertEquals(100_000, d.tickets().get(0).fare());
        assertEquals(75_000, d.tickets().get(1).fare());
        assertEquals(Pricing.EXTRA_LEGROOM_FEE, d.tickets().get(2).seatFee());
        assertEquals(100_000 + 75_000 + 100_000 + 3 * Pricing.TAX_PER_PASSENGER + Pricing.EXTRA_LEGROOM_FEE, d.booking().total());
        assertEquals("+905321234567", d.booking().phone());
        assertTrue(airline.flights().occupiedSeats(flight.id()).containsAll(List.of("14A", "14B", "11A")));
        assertTrue(d.tickets().stream().allMatch(tk -> tk.ticketNo().matches("990\\d{10}")));
    }

    @Test
    void bookingValidation() {
        t.book(flight, CabinClass.ECONOMY, adult("Ali", "Kaya", "20A"));
        assertEquals("seatTaken", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ece", "Demir", "20A"))));
        assertEquals("duplicateSeat", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ece", "Demir", "21A"), adult("Can", "Demir", "21A"))));
        assertEquals("adultRequired", code(() -> t.book(flight, CabinClass.ECONOMY, child("Can", "Demir", "21A"))));
        assertEquals("invalidName", code(() -> t.book(flight, CabinClass.ECONOMY, adult("C4n", "Demir", "21A"))));
        // ekonomi biletiyle business koltuğu ve uçakta olmayan koltuk seçilemez
        assertEquals("invalidSeat", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ece", "Demir", "1A"))));
        assertEquals("invalidSeat", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ece", "Demir", "40A"))));
        assertEquals("seatMissing", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ece", "Demir", null))));
        final BookingRequest bad = new BookingRequest(flight.id(), CabinClass.ECONOMY, List.of(adult("Ece", "Demir", "21A")),
                "ece@", "0532 123 45 67");
        assertEquals("invalidEmail", code(() -> airline.bookings().book(bad, 0)));
        final List<com.mrcdprm.airline.service.BookingService.PassengerInput> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++)
            seven.add(adult("Ece", "Demir", (22 + i) + "A"));
        assertEquals("passengerCount", code(() -> airline.bookings().book(
                new BookingRequest(flight.id(), CabinClass.ECONOMY, seven, "e@example.com", "05321234567"), 0)));
    }

    @Test
    void priceChangeIsDetected() {
        final BookingRequest request = t.request(flight, CabinClass.ECONOMY, adult("Ali", "Kaya", "20A"));
        final long quoted = airline.bookings().quote(flight.id(), CabinClass.ECONOMY, request.passengers()).total();
        // kullanıcı onay ekranındayken kalkışa 72 saatten az kaldı: fiyat değişti
        t.clock.set(flight.departure().minusHours(70));
        final AirlineException e = assertThrows(AirlineException.class, () -> airline.bookings().book(request, quoted));
        assertEquals("priceChanged", e.code());
        assertTrue((Long) e.args()[0] > quoted);
    }

    @Test
    void salesCloseOneHourBeforeDeparture() {
        t.clock.set(flight.departure().minusMinutes(59));
        assertEquals("salesClosed", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ali", "Kaya", "20A"))));
        assertFalse(airline.flights().search(Airport.IST, Airport.ESB, flight.departure().toLocalDate(), CabinClass.ECONOMY, 1)
                .get(0).bookable());
    }

    @Test
    void sameSeatSoldOnlyOnceUnderConcurrency() throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<Boolean>> results = new ArrayList<>();
        final long total = airline.bookings().quote(flight.id(), CabinClass.ECONOMY, List.of(adult("A", "B", "17C"))).total();
        for (int i = 0; i < 16; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    airline.bookings().book(t.request(flight, CabinClass.ECONOMY, adult("Yolcu", "No" + "abcdefghijklmnop".charAt(n), "17C")), total);
                    return true;
                } catch (AirlineException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results)
            if (f.get())
                ok++;
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, ok);
    }

    // ---------------------------------------------------------------- rezervasyonu bulma

    @Test
    void findByCodeAndSurname() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"), adult("Ali", "Demir", "14B"));
        final String pnr = d.booking().pnr();
        assertEquals(d.booking().id(), airline.bookings().find(new BookingKey(pnr.toLowerCase(), "yilmaz")).booking().id());
        // rezervasyondaki herhangi bir yolcunun soyadı yeterli
        assertEquals(d.booking().id(), airline.bookings().find(new BookingKey(pnr, "DEMİR")).booking().id());
        assertEquals("bookingNotFound", code(() -> airline.bookings().find(new BookingKey(pnr, "Kaya"))));
        assertEquals("bookingNotFound", code(() -> airline.bookings().find(new BookingKey("XXXXXX", "Yılmaz"))));
        assertEquals("bookingNotFound", code(() -> airline.bookings().find(new BookingKey("' OR 1=1 --", "Yılmaz"))));
    }

    @Test
    void repeatedFailedLookupsAreThrottled() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"));
        for (int i = 0; i < 5; i++)
            assertEquals("bookingNotFound", code(() -> airline.bookings().find(new BookingKey("ABCDEF", "Kaya"))));
        // doğru bilgiyle bile kısa süre beklenir
        final AirlineException blocked = assertThrows(AirlineException.class,
                () -> airline.bookings().find(new BookingKey(d.booking().pnr(), "Yılmaz")));
        assertEquals("tooManyLookups", blocked.code());
        // saniye int olmalı: long argümanlar hata metninde para tutarı olarak biçimlenir
        assertEquals(60, blocked.args()[0]);
        t.clock.advance(Duration.ofSeconds(61));
        assertNotNull(airline.bookings().find(new BookingKey(d.booking().pnr(), "Yılmaz")));
    }

    // ---------------------------------------------------------------- koltuk ve check-in

    @Test
    void changeSeatChargesExtraLegroomOnce() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"));
        final BookingKey key = new BookingKey(d.booking().pnr(), "Yılmaz");
        final long ticketId = d.tickets().get(0).id();
        assertEquals(Pricing.EXTRA_LEGROOM_FEE, airline.bookings().changeSeat(key, ticketId, "12F"));
        // tekrar standart koltuğa geçince iade yok, tekrar acil çıkışa geçince ikinci kez ücret yok
        assertEquals(0, airline.bookings().changeSeat(key, ticketId, "20C"));
        assertEquals(0, airline.bookings().changeSeat(key, ticketId, "11A"));
        final BookingDetail after = airline.bookings().find(key);
        assertEquals("11A", after.tickets().get(0).seat());
        assertEquals(d.booking().total() + Pricing.EXTRA_LEGROOM_FEE, after.booking().total());
        assertFalse(airline.flights().occupiedSeats(flight.id()).contains("14A"));
        assertEquals("invalidSeat", code(() -> airline.bookings().changeSeat(key, ticketId, "2A")));
        t.book(flight, CabinClass.ECONOMY, adult("Ali", "Kaya", "25A"));
        assertEquals("seatTaken", code(() -> airline.bookings().changeSeat(key, ticketId, "25A")));
    }

    @Test
    void checkInWindowAndBoardingNumbers() {
        final BookingDetail first = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"), child("Can", "Yılmaz", "14B"));
        final BookingDetail second = t.book(flight, CabinClass.ECONOMY, adult("Ali", "Kaya", "20A"));
        final BookingKey key = new BookingKey(first.booking().pnr(), "Yılmaz");
        assertEquals(CheckInState.NOT_OPEN, airline.bookings().checkInState(flight));
        assertEquals("checkInNotOpen", code(() -> airline.bookings().checkIn(key)));

        t.clock.set(flight.departure().minusHours(23));
        assertEquals(CheckInState.OPEN, airline.bookings().checkInState(flight));
        final BookingDetail checked = airline.bookings().checkIn(key);
        assertTrue(checked.allCheckedIn());
        assertEquals(List.of(1, 2), checked.tickets().stream().map(Ticket::boardingNo).toList());
        final BookingDetail other = airline.bookings().checkIn(new BookingKey(second.booking().pnr(), "kaya"));
        assertEquals(3, other.tickets().get(0).boardingNo());
        // check-in sonrası koltuk değişmez
        assertEquals("alreadyCheckedIn", code(() -> airline.bookings().changeSeat(key, checked.tickets().get(0).id(), "30A".replace("30", "22"))));

        t.clock.set(flight.departure().minusMinutes(45));
        assertEquals(CheckInState.CLOSED, airline.bookings().checkInState(flight));
        assertEquals("checkInClosed", code(() -> airline.bookings().checkIn(new BookingKey(second.booking().pnr(), "Kaya"))));
    }

    // ---------------------------------------------------------------- iptal ve iade

    @Test
    void passengerCancellationRefundsByRule() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"), adult("Ali", "Yılmaz", "11B"));
        final BookingKey key = new BookingKey(d.booking().pnr(), "Yılmaz");
        // 10 gün kala: bilet ücretinin tamamı + vergiler; koltuk ücreti iade edilmez
        t.clock.set(flight.departure().minusDays(5));
        final RefundQuote quote = airline.bookings().refundQuote(key);
        assertEquals(75, quote.farePercent());
        assertEquals(200_000 * 75 / 100 + 2 * Pricing.TAX_PER_PASSENGER, quote.refund());
        final RefundQuote done = airline.bookings().cancel(key);
        assertEquals(quote.refund(), done.refund());
        final BookingDetail after = airline.bookings().find(key);
        assertEquals(BookingStatus.CANCELLED, after.booking().status());
        assertEquals(quote.refund(), after.booking().refund());
        assertTrue(airline.flights().occupiedSeats(flight.id()).isEmpty(), "koltuklar boşa çıkar");
        assertEquals("alreadyCancelled", code(() -> airline.bookings().cancel(key)));
        // boşalan koltuk yeniden satılabilir
        t.book(flight, CabinClass.ECONOMY, adult("Ece", "Kaya", "14A"));
    }

    @Test
    void lastDayEconomyRefundsOnlyTaxes() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"));
        t.clock.set(flight.departure().minusHours(5));
        final RefundQuote q = airline.bookings().cancel(new BookingKey(d.booking().pnr(), "Yılmaz"));
        assertEquals(0, q.farePercent());
        assertEquals(Pricing.TAX_PER_PASSENGER, q.refund());
    }

    @Test
    void airlineCancellationRefundsEverything() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "11A"));
        t.book(flight, CabinClass.BUSINESS, adult("Ali", "Kaya", "1A"));
        assertEquals(2, airline.flights().cancel(t.admin, flight.id()));
        final BookingDetail after = airline.bookings().find(new BookingKey(d.booking().pnr(), "Yılmaz"));
        assertEquals(BookingStatus.CANCELLED, after.booking().status());
        assertEquals("AIRLINE", after.booking().cancelledBy());
        assertEquals(after.booking().total(), after.booking().refund());
        assertTrue(airline.flights().search(Airport.IST, Airport.ESB, flight.departure().toLocalDate(), CabinClass.ECONOMY, 1).isEmpty());
        assertEquals("flightCancelled", code(() -> t.book(flight, CabinClass.ECONOMY, adult("Ece", "Kaya", "20A"))));
        assertEquals("flightCancelled", code(() -> airline.flights().delay(t.admin, flight.id(), 30)));
    }

    @Test
    void delayMovesTimesAndKeepsSchedule() {
        final Flight delayed = airline.flights().delay(t.admin, flight.id(), 45);
        assertEquals(FlightStatus.DELAYED, delayed.status());
        assertEquals(flight.departure().plusMinutes(45), delayed.departure());
        assertEquals(flight.arrival().plusMinutes(45), delayed.arrival());
        assertEquals(45, delayed.delayMinutes());
        assertEquals("invalidDelay", code(() -> airline.flights().delay(t.admin, flight.id(), 2)));
        t.clock.set(delayed.departure().plusMinutes(1));
        assertEquals("flightDeparted", code(() -> airline.flights().delay(t.admin, flight.id(), 30)));
    }

    @Test
    void staffSearchAndCancel() {
        final BookingDetail d = t.book(flight, CabinClass.ECONOMY, adult("Ayşe", "Yılmaz", "14A"));
        t.book(flight, CabinClass.ECONOMY, adult("Ali", "Kaya", "15A"));
        assertEquals(2, airline.bookings().search(t.admin, "").size());
        assertEquals(1, airline.bookings().search(t.admin, "yılmaz").size());
        assertEquals(1, airline.bookings().search(t.admin, d.booking().pnr().toLowerCase()).size());
        assertEquals(0, airline.bookings().search(t.admin, "%").size(), "LIKE joker karakteri kaçışlanır");
        assertEquals(2, airline.flights().manifest(t.admin, flight.id()).size());
        airline.bookings().cancel(t.admin, d.booking().id());
        assertEquals("STAFF", airline.bookings().detail(t.admin, d.booking().id()).booking().cancelledBy());
        assertEquals(1, airline.flights().manifest(t.admin, flight.id()).size());
    }

    // ---------------------------------------------------------------- örnek veri ve özet

    @Test
    void sampleDataAndDashboard() {
        SampleData.load(airline);
        assertNotNull(airline.auth().login(SampleData.STAFF_USERNAME, SampleData.STAFF_PASSWORD));
        final BookingDetail demo = airline.bookings().find(new BookingKey(SampleData.DEMO_PNR, SampleData.DEMO_SURNAME));
        assertEquals(3, demo.tickets().size());
        assertEquals(CheckInState.OPEN, airline.bookings().checkInState(demo.flight()));
        final Dashboard dash = airline.reports().dashboard(t.admin);
        assertTrue(dash.flightsToday() > 30);
        assertTrue(dash.passengersToday() > 1000);
        assertEquals(14, dash.last14Days().size());
        assertTrue(dash.loadNext7Days() > 0.2 && dash.loadNext7Days() < 1);
        assertTrue(airline.flights().search(Airport.IST, Airport.ESB, START.toLocalDate().plusDays(1), CabinClass.ECONOMY, 2)
                .stream().anyMatch(Offer::bookable));
        // örnek veride hiçbir koltuk iki kez satılmamış olmalı (UNIQUE indeksi zaten engeller)
        final LocalDateTime now = airline.now();
        assertTrue(now.isEqual(START));
    }
}
