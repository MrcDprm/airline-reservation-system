package com.mrcdprm.airline.core;

/**
 * Uçaktaki bir koltuk: sıra numarası, harf (A-F), kabin ve geniş bacak mesafesi (acil çıkış sırası).
 * Kod "12A" biçimindedir.
 */
public record Seat(int row, char letter, CabinClass cabin, boolean extraLegroom) {

    public String code() {
        return row + String.valueOf(letter);
    }

    /** Pencere kenarı: her iki düzende de A ve F. */
    public boolean window() {
        return letter == 'A' || letter == 'F';
    }

    /** Koridor kenarı: iki düzende de C ve D. */
    public boolean aisle() {
        return letter == 'C' || letter == 'D';
    }
}
