package com.mrcdprm.airline.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Filodaki uçak tipleri ve koltuk düzenleri. Business 2-2 (A C - D F), ekonomi 3-3 (A B C - D E F).
 * Acil çıkış sıraları geniş bacak mesafelidir ve ek ücretlidir.
 */
public enum AircraftType {
    A320("Airbus A320", 3, 29, Set.of(11, 12)),
    B738("Boeing 737-800", 4, 31, Set.of(15, 16)),
    A321("Airbus A321neo", 5, 36, Set.of(12, 25));

    private final String displayName;
    private final int businessRows;
    private final int lastRow;
    private final List<Seat> seats;

    AircraftType(String displayName, int businessRows, int lastRow, Set<Integer> exitRows) {
        this.displayName = displayName;
        this.businessRows = businessRows;
        this.lastRow = lastRow;
        final List<Seat> list = new ArrayList<>();
        for (int row = 1; row <= lastRow; row++) {
            final boolean business = row <= businessRows;
            for (char letter : letters(business ? CabinClass.BUSINESS : CabinClass.ECONOMY))
                list.add(new Seat(row, letter, business ? CabinClass.BUSINESS : CabinClass.ECONOMY,
                        !business && exitRows.contains(row)));
        }
        this.seats = Collections.unmodifiableList(list);
    }

    /** Kabindeki koltuk harfleri: business 2-2 (A C D F), ekonomi 3-3 (A-F). */
    public static List<Character> letters(CabinClass cabin) {
        return cabin == CabinClass.BUSINESS ? List.of('A', 'C', 'D', 'F') : List.of('A', 'B', 'C', 'D', 'E', 'F');
    }

    public String displayName() {
        return displayName;
    }

    public int lastRow() {
        return lastRow;
    }

    public int businessRows() {
        return businessRows;
    }

    /** Bütün koltuklar, önden arkaya ve soldan sağa sıralı. */
    public List<Seat> seats() {
        return seats;
    }

    public List<Seat> seats(CabinClass cabin) {
        return seats.stream().filter(s -> s.cabin() == cabin).toList();
    }

    public int capacity(CabinClass cabin) {
        return (int) seats.stream().filter(s -> s.cabin() == cabin).count();
    }

    public int capacity() {
        return seats.size();
    }

    /** "12A" kodundaki koltuk; uçakta yoksa boş döner (kullanıcı ya da dosya verisine güvenilmez). */
    public Optional<Seat> seat(String code) {
        if (code == null)
            return Optional.empty();
        final String c = code.strip().toUpperCase(Locale.ROOT);
        return seats.stream().filter(s -> s.code().equals(c)).findFirst();
    }

    public static Optional<AircraftType> of(String name) {
        for (AircraftType t : values())
            if (t.name().equals(name))
                return Optional.of(t);
        return Optional.empty();
    }
}
