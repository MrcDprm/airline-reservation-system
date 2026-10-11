package com.mrcdprm.airline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Codes;
import com.mrcdprm.airline.core.Money;
import com.mrcdprm.airline.core.Names;
import com.mrcdprm.airline.core.PasswordHasher;
import com.mrcdprm.airline.core.PasswordPolicy;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.core.RefundPolicy;
import com.mrcdprm.airline.core.Validation;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Çekirdek kurallar: mesafe, koltuk düzeni, fiyat, iade, kodlar, adlar, doğrulama, şifre. */
class CoreTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    @Test
    void distanceAndFlightTime() {
        final double km = Airport.distanceKm(Airport.IST, Airport.ESB);
        assertTrue(km > 360 && km < 400, "IST-ESB yaklaşık 380 km: " + km);
        assertEquals(Airport.distanceKm(Airport.ESB, Airport.IST), km, 1e-9);
        assertEquals(55, Airport.flightMinutes(Airport.IST, Airport.ESB));
        assertTrue(Airport.flightMinutes(Airport.IST, Airport.VAN) > 120);
        assertEquals(0, Airport.flightMinutes(Airport.IST, Airport.IST) % 5);
    }

    @Test
    void airportLookupDoesNotTrustInput() {
        assertEquals(Airport.SAW, Airport.of(" saw ").orElseThrow());
        assertTrue(Airport.of("XXX").isEmpty());
        assertTrue(Airport.of(null).isEmpty());
        assertEquals("Izmir", Airport.ADB.city(Locale.ENGLISH));
        assertEquals("İzmir", Airport.ADB.city(TR));
    }

    @Test
    void seatMaps() {
        assertEquals(168, AircraftType.A320.capacity());
        assertEquals(12, AircraftType.A320.capacity(CabinClass.BUSINESS));
        assertEquals(178, AircraftType.B738.capacity());
        assertEquals(206, AircraftType.A321.capacity());
        // business 2-2: B ve E koltuğu yok
        assertTrue(AircraftType.A320.seat("1B").isEmpty());
        assertEquals(CabinClass.BUSINESS, AircraftType.A320.seat("3d").orElseThrow().cabin());
        assertEquals(CabinClass.ECONOMY, AircraftType.A320.seat("4B").orElseThrow().cabin());
        assertTrue(AircraftType.A320.seat("11C").orElseThrow().extraLegroom());
        assertFalse(AircraftType.A320.seat("13C").orElseThrow().extraLegroom());
        assertTrue(AircraftType.A320.seat("30A").isEmpty());
        assertTrue(AircraftType.A320.seat("A1").isEmpty());
        assertTrue(AircraftType.A320.seat(null).isEmpty());
        assertTrue(AircraftType.A320.seat("4A").orElseThrow().window());
        assertTrue(AircraftType.A320.seat("4D").orElseThrow().aisle());
    }

    @Test
    void dynamicPricing() {
        final long base = 100_000; // 1.000 TL
        assertEquals(100_000, Pricing.adultFare(base, 0, 100, 500));
        assertEquals(100_000, Pricing.adultFare(base, 49, 100, 500));
        assertEquals(115_000, Pricing.adultFare(base, 50, 100, 500));
        assertEquals(135_000, Pricing.adultFare(base, 70, 100, 500));
        assertEquals(160_000, Pricing.adultFare(base, 85, 100, 500));
        // son 72 saat: +%15
        assertEquals(115_000, Pricing.adultFare(base, 0, 100, 71));
        assertEquals(175_000, Pricing.adultFare(base, 99, 100, 10));
        // 10 TL'ye yukarı yuvarlama
        assertEquals(101_000, Pricing.adultFare(100_010, 0, 100, 500));
        assertEquals(75_000, Pricing.childFare(100_000));
        assertEquals(76_000, Pricing.childFare(101_000)); // 757,50 -> 760
        assertEquals(Pricing.EXTRA_LEGROOM_FEE, Pricing.seatFee(AircraftType.B738.seat("15A").orElseThrow()));
        assertEquals(0, Pricing.seatFee(AircraftType.B738.seat("14A").orElseThrow()));
        assertTrue(Pricing.suggestedBusinessBase(500) > Pricing.suggestedEconomyBase(500));
        assertEquals(0, Pricing.suggestedEconomyBase(347) % 1_000);
    }

    @Test
    void refundRules() {
        final long day = 24 * 60;
        assertEquals(100, RefundPolicy.farePercent(CabinClass.ECONOMY, 8 * day));
        assertEquals(75, RefundPolicy.farePercent(CabinClass.ECONOMY, 7 * day - 1));
        assertEquals(75, RefundPolicy.farePercent(CabinClass.ECONOMY, 3 * day));
        assertEquals(50, RefundPolicy.farePercent(CabinClass.ECONOMY, 3 * day - 1));
        assertEquals(50, RefundPolicy.farePercent(CabinClass.ECONOMY, day));
        assertEquals(0, RefundPolicy.farePercent(CabinClass.ECONOMY, day - 1));
        assertEquals(100, RefundPolicy.farePercent(CabinClass.BUSINESS, day));
        assertEquals(50, RefundPolicy.farePercent(CabinClass.BUSINESS, 90));
        // vergiler her zaman iade
        assertEquals(25_000, RefundPolicy.refund(CabinClass.ECONOMY, 60, 200_000, 25_000));
        assertEquals(125_000, RefundPolicy.refund(CabinClass.ECONOMY, 2 * day, 200_000, 25_000));
    }

    @Test
    void codesAreRandomAndUnambiguous() {
        final SecureRandom random = new SecureRandom();
        final Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            final String pnr = Codes.pnr(random);
            assertEquals(6, pnr.length());
            assertTrue(pnr.chars().allMatch(c -> Codes.PNR_ALPHABET.indexOf(c) >= 0));
            assertFalse(pnr.matches(".*[01OIL].*"));
            seen.add(pnr);
        }
        assertTrue(seen.size() > 1_990, "kodlar tekrar etmemeli");
        assertTrue(Codes.ticketNumber(random).matches("990\\d{10}"));
        assertEquals("DEMA26", Codes.normalizePnr("  dema26 "));
        assertNull(Codes.normalizePnr("DEMO26")); // O harfi alfabede yok
        assertNull(Codes.normalizePnr("ABC"));
        assertNull(Codes.normalizePnr("' OR 1"));
        assertTrue(Codes.validFlightNumber("ZZ 104"));
        assertFalse(Codes.validFlightNumber("ZZ 0104"));
    }

    @Test
    void namesOnTickets() {
        assertEquals("YILMAZ/AYSE GUL", Names.ticketName(" Ayşe  Gül ", "Yılmaz"));
        assertEquals("OZTURK/IREM", Names.ticketName("irem", "öztürk"));
        assertTrue(Names.sameSurname("Yılmaz", "yilmaz"));
        assertTrue(Names.sameSurname("Yılmaz", "YILMAZ"));
        assertTrue(Names.sameSurname("Şahin-Kaya", "sahin kaya"));
        assertFalse(Names.sameSurname("Kaya", "Kara"));
        assertFalse(Names.sameSurname("", ""));
        assertTrue(Names.valid("Ayşe Gül"));
        assertTrue(Names.valid("O'Neil"));
        assertFalse(Names.valid("A"));
        assertFalse(Names.valid("Ali1"));
        assertFalse(Names.valid("<script>"));
        assertFalse(Names.valid("x".repeat(41)));
    }

    @Test
    void contactValidation() {
        assertEquals("+905321234567", Validation.phone("0532 123 45 67"));
        assertEquals("+905321234567", Validation.phone("(532) 123-45-67"));
        assertEquals("+905321234567", Validation.phone("+90 532 123 45 67"));
        assertEquals("+4915112345678", Validation.phone("+49 151 12345678"));
        assertNull(Validation.phone("12345"));
        assertNull(Validation.phone("0212 123 45 67 89"));
        assertTrue(Validation.email("ayse.yilmaz@example.com"));
        assertFalse(Validation.email("ayse@"));
        assertFalse(Validation.email("ayse yilmaz@example.com"));
        assertTrue(Validation.username("operasyon"));
        assertFalse(Validation.username("op"));
        assertFalse(Validation.username("op erasyon"));
    }

    @Test
    void moneyFormatAndParse() {
        assertEquals("1.250 ₺", Money.format(125_000, TR));
        assertEquals("1,250 ₺", Money.format(125_000, Locale.ENGLISH));
        assertEquals("1.249,50 ₺", Money.format(124_950, TR));
        assertEquals(OptionalLong.of(125_000), Money.parse("1.250", TR));
        assertEquals(OptionalLong.of(125_050), Money.parse("1250,50", TR));
        assertEquals(OptionalLong.of(125_050), Money.parse("1,250.50", Locale.ENGLISH));
        assertEquals(OptionalLong.of(125_000), Money.parse("1250 ₺", TR));
        assertTrue(Money.parse("1.2.3", TR).isEmpty());
        assertTrue(Money.parse("0", TR).isEmpty());
        assertTrue(Money.parse("-5", TR).isEmpty());
        assertTrue(Money.parse("12,345", TR).isEmpty());
        assertTrue(Money.parse("abc", TR).isEmpty());
        assertTrue(Money.parse("99999999", TR).isEmpty());
    }

    @Test
    void passwords() {
        assertEquals(List.of(), PasswordPolicy.violations("Guclu.Sifre2026", "operasyon"));
        assertTrue(PasswordPolicy.violations("kisa", null).contains("length"));
        assertTrue(PasswordPolicy.violations("operasyon2026A", "operasyon").contains("containsLogin"));
        final String hash = PasswordHasher.hash("Guclu.Sifre2026");
        assertTrue(hash.startsWith("$argon2id$v=19$"));
        assertTrue(PasswordHasher.verify("Guclu.Sifre2026", hash));
        assertFalse(PasswordHasher.verify("guclu.sifre2026", hash));
        assertFalse(PasswordHasher.verify("Guclu.Sifre2026", hash.replace("m=19456", "m=9999999")));
        assertFalse(PasswordHasher.verify("x", "bozuk"));
    }
}
