package com.mrcdprm.airline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.core.PassengerType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** İki dil dosyası aynı anahtarlara sahip mi ve kodda geçen her metin anahtarı tanımlı mı? */
class I18nTest {

    /** Anahtar biçiminde olup metin anahtarı olmayan değerler (dosya adları, sistem özellikleri). */
    private static final Set<String> NOT_KEYS = Set.of("airline.db", "settings.properties", "airline.dataDir",
            "user.home", "com.mrcdprm.airline.i18n.messages");

    private static Properties load(String lang) throws IOException {
        final Properties p = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream("/com/mrcdprm/airline/i18n/messages_" + lang + ".properties")) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(new TreeSet<>(load("tr").stringPropertyNames()), new TreeSet<>(load("en").stringPropertyNames()));
    }

    @Test
    void everyKeyUsedInCodeExists() throws IOException {
        final Properties tr = load("tr");
        final Set<String> used = new TreeSet<>();
        // ui ve pdf paketlerindeki "a.b" biçimli bütün metinler (I18n.t, tablo sütunları, adım adları...)
        final Pattern literal = Pattern.compile("\"([a-z]+\\.[a-zA-Z0-9.]+)\"");
        final Pattern error = Pattern.compile("AirlineException\\(\"([a-zA-Z]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                final String code = Files.readString(file);
                final boolean ui = file.toString().contains("ui") || file.toString().contains("pdf");
                if (ui) {
                    final Matcher m = literal.matcher(code);
                    while (m.find())
                        used.add(m.group(1));
                }
                final Matcher e = error.matcher(code);
                while (e.find())
                    used.add("error." + e.group(1));
            }
        }
        for (Enum<?>[] values : List.<Enum<?>[]>of(CabinClass.values(), PassengerType.values(), FlightStatus.values()))
            for (Enum<?> v : values)
                used.add("enum." + v.getDeclaringClass().getSimpleName() + "." + v.name());
        for (String rule : List.of("length", "tooLong", "upper", "lower", "digit", "containsLogin"))
            used.add("password.rule." + rule);
        for (String by : List.of("PASSENGER", "STAFF", "AIRLINE"))
            used.add("booking.cancelledBy." + by);
        for (String nav : List.of("dashboard", "flights", "bookings", "account"))
            used.add("nav." + nav);
        used.removeAll(NOT_KEYS);

        final List<String> missing = new ArrayList<>();
        for (String key : used)
            if (!tr.containsKey(key) && !key.endsWith("."))
                missing.add(key);
        assertTrue(missing.isEmpty(), "Eksik anahtarlar: " + missing);
    }
}
