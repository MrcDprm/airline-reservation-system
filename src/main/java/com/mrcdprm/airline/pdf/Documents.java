package com.mrcdprm.airline.pdf;

import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.PassengerType;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.data.Records.Booking;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.service.BookingService;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import com.mrcdprm.airline.ui.I18n;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * E-bilet (seyahat belgesi) ve biniş kartı PDF'leri. Metinler seçili dilde yazılır.
 * Havayolu hayalidir; her sayfada "ÖRNEK" damgası vardır, belge gerçek bilet sanılmasın.
 */
public final class Documents {

    private static final Color LIGHT = new Color(0xf6, 0xf8, 0xfa);
    private static final Color AMBER = new Color(0xf5, 0xc7, 0x7e);
    private static final Color NAVY = new Color(0x16, 0x1e, 0x3a);

    private Documents() {
    }

    public static String eTicketFileName(BookingDetail d) {
        return I18n.t("pdf.eTicketFile") + "-" + d.booking().pnr() + ".pdf";
    }

    public static String boardingFileName(BookingDetail d) {
        return I18n.t("pdf.boardingFile") + "-" + d.booking().pnr() + ".pdf";
    }

    // ---------------------------------------------------------------- e-bilet

    public static void eTicket(BookingDetail d, LocalDateTime issued, Path file) throws IOException {
        final Booking b = d.booking();
        final Flight f = d.flight();
        try (PdfDocument pdf = new PdfDocument()) {
            pdf.newPage();
            final float left = PdfDocument.MARGIN;
            final float right = PdfDocument.WIDTH - left;
            header(pdf, I18n.t("pdf.eTicketTitle"), I18n.t("pdf.bookingCode", b.pnr()));

            pdf.y -= 6;
            row(pdf, I18n.t("pdf.status"), b.status() == com.mrcdprm.airline.core.BookingStatus.CANCELLED
                    ? I18n.t("booking.cancelledBy." + b.cancelledBy()) : I18n.t("booking.confirmed"));
            row(pdf, I18n.t("pdf.bookedAt"), I18n.time(b.createdAt()));
            row(pdf, I18n.t("pdf.issuedAt"), I18n.time(issued));
            row(pdf, I18n.t("pdf.contact"), b.email() + "  ·  " + b.phone());

            // uçuş kutusu
            pdf.y -= 14;
            final float boxTop = pdf.y;
            final float boxH = 104;
            pdf.box(left, boxTop - boxH, right - left, boxH, LIGHT);
            pdf.text(f.number() + "  ·  " + I18n.date(f.departure().toLocalDate()) + "  ·  " + I18n.of(b.cabin()),
                    left + 16, boxTop - 22, pdf.bold, 11, PdfDocument.TEXT);
            pdf.textRight(f.aircraft().displayName(), right - 16, boxTop - 22, pdf.regular, 10, PdfDocument.MUTED);
            pdf.text(f.origin().code(), left + 16, boxTop - 58, pdf.bold, 28, PdfDocument.ACCENT);
            pdf.textRight(f.destination().code(), right - 16, boxTop - 58, pdf.bold, 28, PdfDocument.ACCENT);
            pdf.text(I18n.clock(f.departure()) + "  " + f.origin().city(I18n.locale()), left + 16, boxTop - 76, pdf.bold, 11,
                    PdfDocument.TEXT);
            pdf.textRight(f.destination().city(I18n.locale()) + "  " + I18n.clock(f.arrival()), right - 16, boxTop - 76,
                    pdf.bold, 11, PdfDocument.TEXT);
            pdf.text(f.origin().airportName(I18n.locale()), left + 16, boxTop - 91, pdf.regular, 9, PdfDocument.MUTED);
            pdf.textRight(f.destination().airportName(I18n.locale()), right - 16, boxTop - 91, pdf.regular, 9, PdfDocument.MUTED);
            final String mid = I18n.duration(f.minutes());
            final float mw = pdf.width(mid, pdf.regular, 9);
            pdf.line(left + 110, boxTop - 50, right - 110, PdfDocument.LINE, 1);
            pdf.text(mid, PdfDocument.WIDTH / 2 - mw / 2, boxTop - 64, pdf.regular, 9, PdfDocument.MUTED);
            pdf.y = boxTop - boxH - 6;

            // yolcular
            section(pdf, I18n.t("wizard.passengers"));
            final float[] cols = {left, left + 190, left + 270, left + 320};
            tableHead(pdf, cols, right, I18n.t("wizard.col.passenger"), I18n.t("wizard.col.type"), I18n.t("wizard.col.seat"),
                    I18n.t("booking.ticketNo"), I18n.t("wizard.col.fare"));
            for (Ticket t : d.tickets()) {
                pdf.text(pdf.fit(t.ticketName(), pdf.bold, 9.5f, 180), cols[0], pdf.y - 13, pdf.bold, 9.5f, PdfDocument.TEXT);
                pdf.text(I18n.of(t.type()), cols[1], pdf.y - 13, pdf.regular, 9.5f, PdfDocument.TEXT);
                pdf.text(t.seat(), cols[2], pdf.y - 13, pdf.regular, 9.5f, PdfDocument.TEXT);
                pdf.text(ticketNo(t.ticketNo()), cols[3], pdf.y - 13, pdf.regular, 9.5f, PdfDocument.TEXT);
                pdf.textRight(I18n.money(t.fare()), right, pdf.y - 13, pdf.regular, 9.5f, PdfDocument.TEXT);
                pdf.y -= 18;
                pdf.line(left, pdf.y + 2, right, PdfDocument.LINE, 0.5f);
            }

            // ödeme
            section(pdf, I18n.t("booking.payment"));
            money(pdf, I18n.t("wizard.fares", d.tickets().size()), d.fares(), false);
            money(pdf, I18n.t("wizard.taxes"), d.taxes(), false);
            money(pdf, I18n.t("wizard.seatFeesShort"), d.seatFees(), false);
            money(pdf, I18n.t("booking.paid"), b.total(), true);
            if (b.refund() > 0)
                money(pdf, I18n.t("booking.refunded"), b.refund(), true);

            // kurallar
            section(pdf, I18n.t("pdf.rulesTitle"));
            final String rules = I18n.t(b.cabin() == CabinClass.BUSINESS ? "wizard.rulesBusiness" : "wizard.rulesEconomy",
                    I18n.money(Pricing.TAX_PER_PASSENGER));
            paragraph(pdf, I18n.t("pdf.checkInRule", I18n.time(BookingService.checkInOpens(f))) + " " + rules);

            pdf.watermark(I18n.t("pdf.watermark"));
            footer(pdf);
            pdf.save(file);
        }
    }

    // ---------------------------------------------------------------- biniş kartı

    /** Check-in yapılmış her yolcu için bir biniş kartı; sayfada üç kart. */
    public static void boardingPasses(BookingDetail d, Path file) throws IOException {
        final List<Ticket> checked = d.tickets().stream().filter(Ticket::checkedIn).toList();
        if (checked.isEmpty())
            throw new IllegalStateException("Check-in yapılmamış");
        final Flight f = d.flight();
        try (PdfDocument pdf = new PdfDocument()) {
            for (int i = 0; i < checked.size(); i++) {
                if (i % 3 == 0) {
                    if (i > 0) {
                        pdf.watermark(I18n.t("pdf.watermark"));
                        footer(pdf);
                    }
                    pdf.newPage();
                }
                pass(pdf, d, f, checked.get(i), PdfDocument.HEIGHT - PdfDocument.MARGIN - (i % 3) * 245);
            }
            pdf.watermark(I18n.t("pdf.watermark"));
            footer(pdf);
            pdf.save(file);
        }
    }

    private static void pass(PdfDocument pdf, BookingDetail d, Flight f, Ticket t, float top) throws IOException {
        final float left = PdfDocument.MARGIN;
        final float right = PdfDocument.WIDTH - left;
        final float h = 225;
        pdf.box(left, top - h, right - left, h, LIGHT);
        pdf.box(left, top - 34, right - left, 34, NAVY);
        pdf.text(I18n.t("app.name"), left + 14, top - 22, pdf.bold, 12, Color.WHITE);
        pdf.textRight(I18n.t("pdf.boardingTitle") + "  ·  " + f.number(), right - 14, top - 22, pdf.bold, 11, AMBER);

        final float y = top - 34;
        label(pdf, I18n.t("pdf.passenger"), left + 14, y - 20);
        pdf.text(pdf.fit(t.ticketName() + (t.type() == PassengerType.CHILD ? "  (" + I18n.of(t.type()) + ")" : ""),
                pdf.bold, 14, 330), left + 14, y - 38, pdf.bold, 14, PdfDocument.TEXT);
        label(pdf, I18n.t("booking.code"), right - 120, y - 20);
        pdf.text(d.booking().pnr(), right - 120, y - 38, pdf.bold, 14, PdfDocument.TEXT);

        pdf.text(f.origin().code(), left + 14, y - 82, pdf.bold, 30, PdfDocument.ACCENT);
        pdf.text(f.origin().city(I18n.locale()), left + 14, y - 97, pdf.regular, 9, PdfDocument.MUTED);
        pdf.text("→", left + 108, y - 78, pdf.bold, 20, PdfDocument.MUTED);
        pdf.text(f.destination().code(), left + 140, y - 82, pdf.bold, 30, PdfDocument.ACCENT);
        pdf.text(f.destination().city(I18n.locale()), left + 140, y - 97, pdf.regular, 9, PdfDocument.MUTED);

        final String[][] facts = {
            {I18n.t("pdf.date"), I18n.shortDate(f.departure().toLocalDate())},
            {I18n.t("pdf.departure"), I18n.clock(f.departure())},
            {I18n.t("booking.boarding"), I18n.clock(f.boardingTime())},
            {I18n.t("booking.gate"), f.gate()},
            {I18n.t("wizard.col.seat"), t.seat()},
            {I18n.t("pdf.sequence"), String.format(Locale.ROOT, "%03d", t.boardingNo())},
        };
        float x = left + 14;
        for (String[] fact : facts) {
            label(pdf, fact[0], x, y - 125);
            pdf.text(fact[1], x, y - 143, pdf.bold, 13, PdfDocument.TEXT);
            x += 82;
        }
        pdf.text(I18n.of(d.booking().cabin()) + "  ·  " + I18n.t("booking.ticketNo") + " " + ticketNo(t.ticketNo()),
                left + 14, y - 168, pdf.regular, 9, PdfDocument.MUTED);
        pdf.text(I18n.t("pdf.gateCloses", I18n.clock(f.departure().minusMinutes(15))), left + 14, y - 182, pdf.regular, 9,
                PdfDocument.MUTED);
        barcode(pdf, d.booking().pnr() + t.seat(), right - 210, y - 100, 196, 42);
    }

    /**
     * Code 39 barkodu: her karakter 5 çubuk ve 4 boşluktan oluşur, 3'ü geniştir.
     * Başta ve sonda "*" başlangıç/bitiş karakteri bulunur.
     */
    static void barcode(PdfDocument pdf, String text, float x, float y, float width, float height) throws IOException {
        final String data = "*" + text.toUpperCase(Locale.ROOT) + "*";
        final StringBuilder pattern = new StringBuilder();
        for (char c : data.toCharArray()) {
            final String p = CODE39.get(c);
            if (p == null)
                continue;
            pattern.append(p).append('n'); // karakterler arası dar boşluk
        }
        final int units = pattern.chars().map(c -> c == 'w' ? 3 : 1).sum();
        final float unit = width / units;
        float cx = x;
        for (int i = 0; i < pattern.length(); i++) {
            final float w = (pattern.charAt(i) == 'w' ? 3 : 1) * unit;
            if (i % 2 == 0 && i < pattern.length())
                pdf.box(cx, y, w, height, PdfDocument.TEXT);
            cx += w;
        }
    }

    private static final Map<Character, String> CODE39 = Map.ofEntries(
            Map.entry('0', "nnnwwnwnn"), Map.entry('1', "wnnwnnnnw"), Map.entry('2', "nnwwnnnnw"),
            Map.entry('3', "wnwwnnnnn"), Map.entry('4', "nnnwwnnnw"), Map.entry('5', "wnnwwnnnn"),
            Map.entry('6', "nnwwwnnnn"), Map.entry('7', "nnnwnnwnw"), Map.entry('8', "wnnwnnwnn"),
            Map.entry('9', "nnwwnnwnn"), Map.entry('A', "wnnnnwnnw"), Map.entry('B', "nnwnnwnnw"),
            Map.entry('C', "wnwnnwnnn"), Map.entry('D', "nnnnwwnnw"), Map.entry('E', "wnnnwwnnn"),
            Map.entry('F', "nnwnwwnnn"), Map.entry('G', "nnnnnwwnw"), Map.entry('H', "wnnnnwwnn"),
            Map.entry('I', "nnwnnwwnn"), Map.entry('J', "nnnnwwwnn"), Map.entry('K', "wnnnnnnww"),
            Map.entry('L', "nnwnnnnww"), Map.entry('M', "wnwnnnnwn"), Map.entry('N', "nnnnwnnww"),
            Map.entry('O', "wnnnwnnwn"), Map.entry('P', "nnwnwnnwn"), Map.entry('Q', "nnnnnnwww"),
            Map.entry('R', "wnnnnnwwn"), Map.entry('S', "nnwnnnwwn"), Map.entry('T', "nnnnwnwwn"),
            Map.entry('U', "wwnnnnnnw"), Map.entry('V', "nwwnnnnnw"), Map.entry('W', "wwwnnnnnn"),
            Map.entry('X', "nwnnwnnnw"), Map.entry('Y', "wwnnwnnnn"), Map.entry('Z', "nwwnwnnnn"),
            Map.entry('*', "nwnnwnwnn"));

    // ---------------------------------------------------------------- ortak parçalar

    private static void header(PdfDocument pdf, String title, String subtitle) throws IOException {
        final float left = PdfDocument.MARGIN;
        pdf.text(I18n.t("app.name"), left, pdf.y - 6, pdf.bold, 13, PdfDocument.ACCENT);
        pdf.text(I18n.t("pdf.airlineLine"), left, pdf.y - 20, pdf.regular, 8.5f, PdfDocument.MUTED);
        pdf.textRight(title, PdfDocument.WIDTH - left, pdf.y - 6, pdf.bold, 15, PdfDocument.TEXT);
        pdf.textRight(subtitle, PdfDocument.WIDTH - left, pdf.y - 21, pdf.regular, 9, PdfDocument.MUTED);
        pdf.y -= 32;
        pdf.line(left, pdf.y, PdfDocument.WIDTH - left, PdfDocument.ACCENT, 1.5f);
        pdf.y -= 8;
    }

    private static void footer(PdfDocument pdf) throws IOException {
        pdf.text(I18n.t("pdf.disclaimer"), PdfDocument.MARGIN, 28, pdf.regular, 8, PdfDocument.MUTED);
    }

    private static void section(PdfDocument pdf, String title) throws IOException {
        pdf.y -= 10;
        pdf.text(title.toUpperCase(I18n.locale()), PdfDocument.MARGIN, pdf.y - 12, pdf.bold, 9, PdfDocument.ACCENT);
        pdf.y -= 20;
    }

    private static void row(PdfDocument pdf, String label, String value) throws IOException {
        pdf.text(label, PdfDocument.MARGIN, pdf.y - 13, pdf.regular, 10, PdfDocument.MUTED);
        pdf.text(pdf.fit(value, pdf.regular, 10, 330), PdfDocument.MARGIN + 150, pdf.y - 13, pdf.regular, 10, PdfDocument.TEXT);
        pdf.y -= 18;
    }

    private static void tableHead(PdfDocument pdf, float[] cols, float right, String... titles) throws IOException {
        for (int i = 0; i < cols.length; i++)
            pdf.text(titles[i], cols[i], pdf.y - 10, pdf.bold, 8.5f, PdfDocument.MUTED);
        pdf.textRight(titles[titles.length - 1], right, pdf.y - 10, pdf.bold, 8.5f, PdfDocument.MUTED);
        pdf.y -= 16;
        pdf.line(PdfDocument.MARGIN, pdf.y + 2, right, PdfDocument.LINE, 0.8f);
    }

    private static void money(PdfDocument pdf, String label, long amount, boolean bold) throws IOException {
        final float right = PdfDocument.WIDTH - PdfDocument.MARGIN;
        pdf.text(label, PdfDocument.WIDTH / 2, pdf.y - 13, bold ? pdf.bold : pdf.regular, 10, PdfDocument.TEXT);
        pdf.textRight(I18n.money(amount), right, pdf.y - 13, bold ? pdf.bold : pdf.regular, 10, PdfDocument.TEXT);
        pdf.y -= 18;
    }

    /** Kelimeleri satıra sığdırarak yazar. */
    private static void paragraph(PdfDocument pdf, String text) throws IOException {
        final float max = PdfDocument.WIDTH - 2 * PdfDocument.MARGIN;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            final String candidate = line.isEmpty() ? word : line + " " + word;
            if (pdf.width(candidate, pdf.regular, 9) > max) {
                pdf.text(line.toString(), PdfDocument.MARGIN, pdf.y - 11, pdf.regular, 9, PdfDocument.MUTED);
                pdf.y -= 14;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            pdf.text(line.toString(), PdfDocument.MARGIN, pdf.y - 11, pdf.regular, 9, PdfDocument.MUTED);
            pdf.y -= 14;
        }
    }

    private static void label(PdfDocument pdf, String text, float x, float y) throws IOException {
        pdf.text(text.toUpperCase(I18n.locale()), x, y, pdf.regular, 7.5f, PdfDocument.MUTED);
    }

    private static String ticketNo(String no) {
        return no.length() == 13 ? no.substring(0, 3) + "-" + no.substring(3) : no;
    }
}
