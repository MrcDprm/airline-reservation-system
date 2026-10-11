package com.mrcdprm.airline.core;

import java.util.Locale;
import java.util.Optional;

/**
 * Uçuş ağındaki havalimanları (yurt içi; hepsi aynı saat diliminde olduğu için saatler yerel tutulur).
 * Koordinatlar uçuş süresini ve önerilen ücreti mesafeden hesaplamak için kullanılır.
 */
public enum Airport {
    IST("İstanbul", "Istanbul", "İstanbul Havalimanı", "Istanbul Airport", 41.275, 28.752),
    SAW("İstanbul", "Istanbul", "Sabiha Gökçen", "Sabiha Gökçen", 40.898, 29.309),
    ESB("Ankara", "Ankara", "Esenboğa", "Esenboğa", 40.128, 32.995),
    ADB("İzmir", "Izmir", "Adnan Menderes", "Adnan Menderes", 38.292, 27.157),
    AYT("Antalya", "Antalya", "Antalya Havalimanı", "Antalya Airport", 36.899, 30.800),
    DLM("Dalaman", "Dalaman", "Dalaman Havalimanı", "Dalaman Airport", 36.713, 28.792),
    BJV("Bodrum", "Bodrum", "Milas-Bodrum", "Milas-Bodrum", 37.250, 27.664),
    TZX("Trabzon", "Trabzon", "Trabzon Havalimanı", "Trabzon Airport", 40.995, 39.790),
    SZF("Samsun", "Samsun", "Çarşamba", "Çarşamba", 41.254, 36.567),
    ASR("Kayseri", "Kayseri", "Erkilet", "Erkilet", 38.770, 35.495),
    KYA("Konya", "Konya", "Konya Havalimanı", "Konya Airport", 37.979, 32.562),
    GZT("Gaziantep", "Gaziantep", "Gaziantep Havalimanı", "Gaziantep Airport", 36.947, 37.479),
    DIY("Diyarbakır", "Diyarbakır", "Diyarbakır Havalimanı", "Diyarbakır Airport", 37.894, 40.201),
    ERZ("Erzurum", "Erzurum", "Erzurum Havalimanı", "Erzurum Airport", 39.957, 41.170),
    VAN("Van", "Van", "Ferit Melen", "Ferit Melen", 38.468, 43.332);

    private static final double EARTH_RADIUS_KM = 6371;
    /** Yolcu jetinin ortalama seyir hızı ve kalkış/iniş için eklenen süre. */
    private static final double CRUISE_KMH = 780;
    private static final int TAXI_MINUTES = 25;

    private final String cityTr;
    private final String cityEn;
    private final String nameTr;
    private final String nameEn;
    private final double lat;
    private final double lon;

    Airport(String cityTr, String cityEn, String nameTr, String nameEn, double lat, double lon) {
        this.cityTr = cityTr;
        this.cityEn = cityEn;
        this.nameTr = nameTr;
        this.nameEn = nameEn;
        this.lat = lat;
        this.lon = lon;
    }

    public String city(Locale locale) {
        return "en".equals(locale.getLanguage()) ? cityEn : cityTr;
    }

    public String airportName(Locale locale) {
        return "en".equals(locale.getLanguage()) ? nameEn : nameTr;
    }

    /** IATA kodu (IST, ESB...). */
    public String code() {
        return name();
    }

    /** Veritabanından okunan koda güvenilmez: bilinmeyen kod boş döner. */
    public static Optional<Airport> of(String code) {
        if (code == null)
            return Optional.empty();
        for (Airport a : values())
            if (a.name().equalsIgnoreCase(code.strip()))
                return Optional.of(a);
        return Optional.empty();
    }

    /** İki havalimanı arasındaki büyük daire (kuş uçuşu) mesafesi, km. Haversine formülü. */
    public static double distanceKm(Airport a, Airport b) {
        final double dLat = Math.toRadians(b.lat - a.lat);
        final double dLon = Math.toRadians(b.lon - a.lon);
        final double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    /** Tahmini blok süresi (dakika), 5 dakikaya yuvarlanmış. */
    public static int flightMinutes(Airport a, Airport b) {
        final double minutes = distanceKm(a, b) / CRUISE_KMH * 60 + TAXI_MINUTES;
        return (int) Math.round(minutes / 5) * 5;
    }
}
