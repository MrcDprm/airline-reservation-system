package com.mrcdprm.airline.core;

/**
 * Yolcu iptalinde iade kuralı. Bilet ücretinin iade oranı kabine ve kalkışa kalan süreye bağlıdır;
 * vergi ve harçlar her zaman iade edilir, koltuk ücretleri edilmez. Havayolu uçuşu iptal ederse her şey iade edilir.
 */
public final class RefundPolicy {

    private RefundPolicy() {
    }

    /**
     * Bilet ücretinin iade yüzdesi.
     * Ekonomi: 7 günden fazla %100, 3-7 gün %75, 1-3 gün %50, son 24 saat %0.
     * Business: 24 saatten fazla %100, son 24 saat %50.
     */
    public static int farePercent(CabinClass cabin, long minutesToDeparture) {
        final long hours = minutesToDeparture / 60;
        if (cabin == CabinClass.BUSINESS)
            return hours >= 24 ? 100 : 50;
        if (hours >= 7 * 24)
            return 100;
        if (hours >= 72)
            return 75;
        if (hours >= 24)
            return 50;
        return 0;
    }

    /** İade tutarı: bilet ücretlerinin yüzdesi + vergilerin tamamı. */
    public static long refund(CabinClass cabin, long minutesToDeparture, long fares, long taxes) {
        return fares * farePercent(cabin, minutesToDeparture) / 100 + taxes;
    }
}
