<p align="center">
  <img src="src/main/resources/com/mrcdprm/airline/icon.png" alt="Airline Reservations icon" width="96">
</p>

<h1 align="center">Airline Reservations</h1>

<p align="center">
  <b>English</b> | <a href="README.tr.md">Türkçe</a>
</p>

<p align="center">
  A desktop flight booking app written in Java and JavaFX: passengers search flights, pick seats on a<br>
  <b>visual seat map</b>, check in online and manage their booking; staff manage flights and passenger lists.
</p>

<p align="center">
  <a href="https://github.com/MrcDprm/airline-reservation-system/releases/latest"><b>⬇️ Download for Windows</b></a>
</p>

<p align="center">
  <img src="docs/search.png" alt="Flight search with date strip and results" width="820">
</p>

> This is a portfolio project, not a real airline. It runs locally with one SQLite database on the same computer; there is no server. The airline (flight numbers `ZZ`), flights and fares are made up, no payment is taken, and every PDF it produces is marked as a sample.

## Features

**Passenger side**
- **Flight search** by origin, destination, date, number of adults and children, and cabin (Economy / Business)
- **Date strip** with the lowest fare for 3 days before and after the chosen date
- **Dynamic pricing:** the fare rises as the flight fills up (4 load steps) and in the last 72 hours; children pay 75%
- **Visual seat map** for three aircraft types (A320, 737-800, A321neo): 2-2 business and 3-3 economy layouts, taken seats, paid extra legroom exit rows, and "pick seats together" for groups
- Booking in four steps: passengers → seats → review with price breakdown and refund rules → **booking code (PNR)**
- **PDF e-ticket** with the flight, passengers, ticket numbers and payment
- **My booking** with booking code + last name: change seat, **online check-in** (opens 24 hours and closes 60 minutes before departure), **PDF boarding passes** with gate, boarding time, sequence number and a Code 39 barcode
- **Cancellation with a refund rule** shown before confirming: the fare refund depends on cabin and time left, taxes are always refunded, seat fees are not

**Staff panel**
- Sign-in with **Argon2id** password hashing and a lockout after 5 failed attempts; automatic sign-out after 10 minutes of inactivity
- **Overview:** flights and passengers today, check-ins, 7-day load factor, net sales of the last 14 days (chart), fullest routes and next departures
- **Flights:** date range, search and status filter, load and check-in columns
- **New flight** with distance-based fare and flight time suggestions, repeated daily for up to 60 days with the same flight number
- **Delay** (departure and arrival move together) and **flight cancellation** (all bookings are fully refunded)
- **Passenger list** with an occupancy map where checked-in seats are highlighted
- **Bookings:** search by code, name or email; details, e-ticket and cancellation

**Correctness and security**
- Prices are always calculated in the service layer; if the price changes while the passenger is on the review screen, the booking is stopped and the new total is shown
- A partial unique index (`UNIQUE (flight_id, seat) WHERE active = 1`) makes it impossible to sell the same seat twice, even when two bookings run at the same time
- Each booking is saved in a single database transaction: there is never a booking without tickets
- Booking codes and ticket numbers are random (`SecureRandom`), never sequential; codes avoid look-alike characters (0/O, 1/I/L)
- Booking lookup gives the same message for a wrong code or a wrong last name and is throttled after 5 failed attempts
- Every SQL query is parameterised; search input is escaped for `LIKE`
- Amounts are stored in kuruş as whole numbers, never as floating-point values
- Unexpected errors show a short message, never a stack trace

**Desktop app**
- Turkish and English, light and dark theme (AtlantaFX)
- First-start setup creates the staff account; sample data (19 routes, 4-week schedule, about 130,000 tickets, delays and cancellations) can be loaded with one tick
- Data is stored in the user folder (`%APPDATA%\MrcDprm\AirlineReservations`), never in the program folder
- Windows installer, icon, version and About window

## Screenshots

| Seat selection | Review and price breakdown |
|:---:|:---:|
| <img src="docs/seats.png" alt="Visual seat map with three passengers seated together" width="420"> | <img src="docs/review.png" alt="Booking review with price breakdown and refund rules" width="420"> |
| **My booking** | **Staff overview** |
| <img src="docs/manage.png" alt="Booking details with gate, boarding time and passengers" width="420"> | <img src="docs/dashboard.png" alt="Staff dashboard with sales chart and route load" width="420"> |
| **Flights** | **Passenger list** |
| <img src="docs/flights.png" alt="Flight list with load and check-in status" width="420"> | <img src="docs/manifest.png" alt="Passenger list with occupancy map" width="420"> |
| **Boarding pass (PDF)** | **Dark theme** |
| <img src="docs/boarding.png" alt="Boarding pass with barcode" width="420"> | <img src="docs/search-dark.png" alt="Flight search in dark theme" width="420"> |

## Installation

1. Download `AirlineReservations-1.0.0-Setup.exe` from the [Releases](https://github.com/MrcDprm/airline-reservation-system/releases/latest) page and run it. Java does not need to be installed; it is bundled.
2. On first start, create the staff account. Leave **Load sample data** ticked to try the app right away.
3. With sample data loaded you can try:

| What | How |
|---|---|
| Sample booking (check-in is open) | **My booking** → code `DEMA26`, last name `Yılmaz` |
| Staff panel | **Staff sign-in** → `operasyon` / `Ucus.Demo2026` (or the account you created) |

The sample schedule starts one week before and ends three weeks after the setup day; the sample booking departs within a day of setup. New flights can be added from the staff panel at any time.

Your data stays in `%APPDATA%\MrcDprm\AirlineReservations`. Uninstall from the Windows "Apps" settings; the data folder is kept and can be deleted by hand.

## Tech Stack

- **Java**, **Maven**
- **JavaFX**: user interface and charts
- **AtlantaFX** (theme), **Ikonli + Feather** (icons)
- **SQLite** (sqlite-jdbc): local database
- **Bouncy Castle**: Argon2id password hashing
- **Apache PDFBox** + **Noto Sans**: e-tickets and boarding passes with Turkish characters
- **JUnit**: tests
- **jpackage** + **Inno Setup**: Windows installer

## Project Structure

```
src/main/java/com/mrcdprm/airline/
├── core/      Airports, aircraft seat maps, pricing, refund rules, codes, names, validation, passwords
├── data/      SQLite connection, schema, transactions and row records
├── service/   Sign-in, flight search and management, bookings, check-in, reports, sample data
├── pdf/       E-ticket and boarding pass PDFs (with Code 39 barcode)
├── ui/        Passenger screens, booking steps, seat map, staff panel, dialogs, theme, translations
├── AirlineApp.java
└── Launcher.java
src/main/resources/   Styles, icon, fonts, Turkish and English texts
src/test/java/        JUnit tests
installer/            Build script, Inno Setup script and icon
```

## Building from Source

Requires a JDK (version in `pom.xml`) and Maven.

```
mvn test          # 33 tests
mvn javafx:run    # run the app
```

### Building the installer

Requires [Inno Setup](https://jrsoftware.org/isinfo.php).

```
powershell -ExecutionPolicy Bypass -File installer\build.ps1
ISCC installer\AirlineReservations.iss
```

`build.ps1` runs the tests, finds the JDK modules the app needs with `jdeps` and uses `jpackage` to create `dist\AirlineReservations` with a trimmed Java runtime (only Turkish and English locale data). The installer is created in `installer\Output\`.

**When releasing a new version:** update the version in `pom.xml`, `AirlineApp.VERSION`, `installer/build.ps1` and `installer/AirlineReservations.iss`, run both commands and upload the installer to a new GitHub Release.

## What I Learned

- **Letting the database guard the rules.** Checking "is this seat free?" in Java and then inserting leaves a gap where two bookings can take the same seat. I added a partial unique index on `(flight_id, seat)` for active tickets only, so cancelled tickets free the seat but two active tickets can never share it. A test starts 16 threads that all try to buy seat 17C at the same moment and expects exactly one to succeed.
- **Never trusting the price from the screen.** The review screen shows a quote, but the booking service calculates the price again inside the same transaction and refuses the booking if it changed. Moving the test clock into the last 72 hours is enough to see the "price changed" path.
- **Modelling a seat map.** Each aircraft type generates its seats from a few numbers (business rows, last row, exit rows). The same list drives the visual map, seat validation, extra legroom fees and the "seats together" search, so the UI and the rules cannot disagree.
- **Business rules that depend on time.** Sales and check-in windows, late booking surcharges and refund rates all depend on "now", so the services take a `Clock`. Tests move the clock to 23 hours, 59 minutes or 5 days before departure and check each rule.
- **Access without accounts.** Passengers have no login; the booking code plus last name works like a key, as on airline websites. The code is random, the error message never says which part was wrong, repeated failures are throttled, and the last name comparison folds Turkish letters so `yilmaz`, `Yılmaz` and `YILMAZ` match.
- **Generating realistic sample data fast.** About 130,000 tickets are written with batched prepared statements in one transaction. Seats are filled row by row in a shuffled row order, so groups mostly sit together, and fares are calculated with the same pricing code the app uses.
- **Drawing documents.** The e-ticket and boarding passes are drawn with PDFBox; I implemented the Code 39 barcode myself (each character is 5 bars and 4 spaces, 3 of them wide).
- **JavaFX compared to Windows Forms.** Layout panes instead of absolute positions, CSS instead of property grids, `Task` to keep slow work off the UI thread, and a `TableView` with custom cells for progress bars and status badges.

## License

[MIT](LICENSE) © 2026 Miraç Deprem
