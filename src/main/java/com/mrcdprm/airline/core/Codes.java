package com.mrcdprm.airline.core;

import java.util.random.RandomGenerator;

/**
 * Rezervasyon kodu (PNR) ve bilet numarası üretimi. Kodlar sıralı değil rastgeledir: 1, 2, 3 gibi
 * tahmin edilebilir numaralarla başkasının rezervasyonu bulunamasın. Üretimde SecureRandom kullanılır.
 */
public final class Codes {

    /** Karışabilecek karakterler (0/O, 1/I/L) yok; kod telefonda okunurken yanlış anlaşılmasın. */
    public static final String PNR_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int PNR_LENGTH = 6;
    /** Hayali havayolunun uçuş numarası ön eki ve bilet numarası ön eki. */
    public static final String CARRIER = "ZZ";
    public static final String TICKET_PREFIX = "990";

    private Codes() {
    }

    /** 6 karakterlik rezervasyon kodu: 31^6 ≈ 887 milyon olasılık. */
    public static String pnr(RandomGenerator random) {
        final StringBuilder sb = new StringBuilder(PNR_LENGTH);
        for (int i = 0; i < PNR_LENGTH; i++)
            sb.append(PNR_ALPHABET.charAt(random.nextInt(PNR_ALPHABET.length())));
        return sb.toString();
    }

    /** 13 haneli bilet numarası: havayolu ön eki (990) + 10 rastgele rakam. */
    public static String ticketNumber(RandomGenerator random) {
        final StringBuilder sb = new StringBuilder(TICKET_PREFIX);
        for (int i = 0; i < 10; i++)
            sb.append(random.nextInt(10));
        return sb.toString();
    }

    /** Kullanıcının yazdığı kodu düzeltir (boşluk, küçük harf) ve biçimi geçerliyse döner; değilse null. */
    public static String normalizePnr(String text) {
        if (text == null)
            return null;
        final String s = text.strip().toUpperCase(java.util.Locale.ROOT);
        if (s.length() != PNR_LENGTH)
            return null;
        for (char c : s.toCharArray())
            if (PNR_ALPHABET.indexOf(c) < 0)
                return null;
        return s;
    }

    /** "ZZ" + 1-4 rakam: "ZZ 2104". */
    public static boolean validFlightNumber(String number) {
        return number != null && number.matches("ZZ [1-9][0-9]{0,3}");
    }
}
