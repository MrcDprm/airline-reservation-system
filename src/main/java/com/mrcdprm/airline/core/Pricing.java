package com.mrcdprm.airline.core;

/**
 * Ücret kuralları. Havayolları gibi "dinamik fiyat": uçak doldukça ve kalkış yaklaştıkça bilet pahalanır.
 * Bütün tutarlar kuruş; satış fiyatları 10 TL'ye yukarı yuvarlanır.
 */
public final class Pricing {

    /** Yolcu başına vergi ve havalimanı harcı (iptalde her zaman iade edilir). */
    public static final long TAX_PER_PASSENGER = 12_500;
    /** Acil çıkış sırasındaki geniş bacak mesafeli koltuk ücreti. İade edilmez. */
    public static final long EXTRA_LEGROOM_FEE = 35_000;
    /** Çocuk (2-11 yaş) yetişkin ücretinin yüzde kaçını öder. */
    public static final int CHILD_PERCENT = 75;
    /** Kalkıştan bu kadar saat öncesinden sonra "son dakika" zammı uygulanır. */
    public static final int LATE_BOOKING_HOURS = 72;

    private static final long ROUND_TO = 1_000; // 10 TL

    private Pricing() {
    }

    /**
     * Yetişkin bilet ücreti. Doluluk oranına göre kademeli artar (%50'ye kadar taban fiyat, sonra %15, %35, %60),
     * kalkışa 72 saatten az kaldıysa ayrıca %15 eklenir.
     */
    public static long adultFare(long baseFare, int sold, int capacity, long hoursToDeparture) {
        final double load = capacity <= 0 ? 1 : (double) sold / capacity;
        int percent = load < 0.5 ? 100 : load < 0.7 ? 115 : load < 0.85 ? 135 : 160;
        if (hoursToDeparture < LATE_BOOKING_HOURS)
            percent += 15;
        return roundUp(baseFare * percent / 100);
    }

    public static long childFare(long adultFare) {
        return roundUp(adultFare * CHILD_PERCENT / 100);
    }

    public static long fare(long adultFare, PassengerType type) {
        return type == PassengerType.CHILD ? childFare(adultFare) : adultFare;
    }

    public static long seatFee(Seat seat) {
        return seat.extraLegroom() ? EXTRA_LEGROOM_FEE : 0;
    }

    /** Personelin yeni uçuş eklerken gördüğü önerilen ekonomi taban ücreti: 600 TL + km başına 1,1 TL. */
    public static long suggestedEconomyBase(double distanceKm) {
        return roundUp(60_000 + Math.round(distanceKm * 110));
    }

    /** Önerilen business taban ücreti: ekonominin 2,6 katı. */
    public static long suggestedBusinessBase(double distanceKm) {
        return roundUp(suggestedEconomyBase(distanceKm) * 26 / 10);
    }

    private static long roundUp(long minor) {
        return Math.ceilDiv(minor, ROUND_TO) * ROUND_TO;
    }
}
