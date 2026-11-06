/* StickLab Test C Stufe 3: eingebauten Controller abgreifen und 1:1 weitergeben.
 * Begonnen als Wegwerf-Testcode (Phase 1). Seit Phase 6b (27.10.2026) rechnet nur der Modus curve
 * die Sticks um, alle anderen Modi geben weiter 1:1 weiter.
 *
 * Modi (feste Liste, alles andere -> Abbruch):
 *   check   nur /dev/input/event8 oeffnen, pruefen, anzeigen.
 *           Kein virtuelles Geraet, kein Abgreifen.
 *   mirror  virtuelles Geraet "StickLab Controller" anlegen (gleiche Tasten/Achsen
 *           wie event8), event8 abgreifen, alles 1:1 weitergeben.
 *           Ende spaetestens nach 20 s.
 *   mirror_hide  Stufe 4.5b: umgekehrte Reihenfolge. Zuerst event8 abgreifen, dann warten,
 *           bis die Datei /dev/input/event8 verschwunden ist (das loescht ein festes
 *           root-Skript ueber PServerBinder, Fall hide_event8_mirror), hoechstens 8 s.
 *           Erst dann das virtuelle Geraet anlegen, damit Android ihm Spieler 1 gibt
 *           (Nummer wird beim Erscheinen vergeben, nie neu verteilt). Verschwindet die
 *           Datei nicht -> freigeben, Ende, nichts angelegt. Weitergeben hoechstens 15 s.
 *           Das Skript legt die Datei wieder an, sobald dieses Programm endet.
 *   mirror_hide_id  Stufe 4.5d: wie mirror_hide, aber das virtuelle Geraet uebernimmt
 *           bus/vendor/product/version von event8 (heute 0003/2020/0112/0000), damit Android
 *           ihm dieselbe Kennung und Tastenbelegung gibt. Name bleibt "StickLab Controller"
 *           (Schwarze Liste). Je 300 ms Pause vor dem Anlegen und nach dem Entfernen, damit
 *           Android nie zwei Geraete mit derselben Kennung gleichzeitig offen hat (R20).
 *   serve   Stufe 5: wie mirror_hide_id, aber ohne 15-s-Ende und seit Z-1 (15.10.2026) ganz ohne
 *           Zeitlimit. Laeuft, bis ein Signal kommt (Stopp aus der App: pkill -x libslmirror.so),
 *           bis der Waechter die App nicht mehr findet (2 x hintereinander, je 1 s Abstand),
 *           bei R9, wenn event8 verschwindet, oder bei einem Fehler. Haengt es, beendet es der
 *           "harte Stopp" der App (pkill -KILL -x libslmirror.so, der Kernel gibt event8 frei).
 *           Gestartet als root ueber PServerBinder (Skript s5.sh), Programm kommt als
 *           libslmirror.so mit der App und wird vor dem Start per SHA-256 geprueft (App + Skript).
 *           Schreibt keine Sekundenzeilen "t=..." (Protokoll wuerde sonst ohne Ende wachsen).
 *   curve   Phase 6b (Entscheidungen B1-B4 vom 27.10.2026): wie serve, dazu Kurven fuer die Sticks aus der
 *           festen Datei CURVE_PATH (privater Ordner der App, die App schreibt sie). Gueltige Tabelle ->
 *           beide Sticks radial umgerechnet, Trigger mit Grenze "voll ab" (Z11); Datei fehlt oder ungueltig -> alles
 *           1:1 wie serve (B3). Tasten und Steuerkreuz immer 1:1. Schaut einmal pro Sekunde, ob die Datei neu ist
 *           (B1), das Geraet bleibt.
 *   curve_check  nur die Datei lesen, pruefen und den Selbsttest ausgeben. Oeffnet weder event8 noch
 *           /dev/uinput, braucht kein root (Test T1 per run-as).
 *
 * Schutz (PROJEKT_DOKU Abschnitt 11):
 *   R2  nur /dev/input/event8, Name + Bus + Vendor + Product muessen passen
 *   R1  Zeitlimit 20 s (mirror_hide: 8 s warten + 15 s) im Programm, dazu alarm(23 bzw. 26)
 *       (beendet hart), aussen timeout
 *   R3  Abgreifen gescheitert oder event8 verschwindet (Geraet weg, nicht nur die Datei) -> Ende
 *   R9  mehr als 20000 Ereignisse pro Sekunde auf event8 -> Ende
 *   R30 serve: Waechter "App lebt" (nur /proc/<n>/cmdline lesen). Kein Zeitlimit, kein alarm
 *       (Z-1); Notbremsen: Stopp, harter Stopp (KILL), App beenden (Waechter), Neustart
 *   R136 curve: Tabelle wird nur gelesen, nie selbst gerechnet (K6-2), jeder Wert geprueft wie
 *       CurveTable.isValid (steigt nie ab), Faktor nie negativ (Stick kehrt sich nie um), alles auf +-32767 begrenzt
 *   R142 curve: die Datei schreibt die App, gelesen wird als root -> keine Links (O_NOFOLLOW), nie auf eine
 *       Roehre warten (O_NONBLOCK), nur normale Datei mit genau CURVE_FILE_SIZE Bytes, fester Puffer
 *   R143 curve: Kennung, Version und CRC32 gegen halb geschriebene oder fremde Dateien
 *   Bei jedem Fehler und bei Strg+C / kill: Abgreifen loesen, Geraet entfernen.
 *   Schreibt keine Dateien, startet keine Programme, fuehrt keine Befehle aus. Liest nur event8, /proc (Waechter)
 *   und im Modus curve die Tabellen-Datei.
 *   mirror_hide loescht nichts selbst, es schaut nur nach, ob die Datei noch da ist.
 */
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <math.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define SRC_PATH         "/dev/input/event8"
#define UINPUT_PATH      "/dev/uinput"
#define VIRT_NAME        "StickLab Controller"  /* steht auf der Schwarzen Liste */
#define LIMIT_MS         20000                /* Not-Aus 1 */
#define HARD_LIMIT_S     23                  /* alarm: harter Abbruch durch den Kernel */
#define SETTLE_MS        1500                 /* Android Zeit geben, das Geraet zu erkennen */
#define MAX_EVENTS_PER_S 20000                /* R9 Rueckfluss-Waechter */
#define HIDE_WAIT_MS     8000                 /* mirror_hide: so lange auf das Loeschen warten */
#define HIDE_LIMIT_MS    15000                /* mirror_hide: Weitergeben hoechstens 15 s */
#define HIDE_HARD_LIMIT_S 26                  /* mirror_hide: 8 + 15 + Reserve */
#define HIDE_SETTLE_MS   1000                 /* mirror_hide: Android Zeit geben, das Geraet zu oeffnen */
#define ID_GAP_MS        300                  /* mirror_hide_id: Pause vor dem Anlegen und nach dem Entfernen */
#define POLL_MS          200                  /* hoechstens so lange auf event8 warten, dann Stopp/Waechter/Frist pruefen */
/* Paket der App, zu der das Programm gehoert (Phase 6e): Debug-Fassung ohne Angabe, echte App mit -DSTICKLAB_PACKAGE. Fest im
 * Programm, nie aus dem Befehl (Regel 7) */
#ifndef STICKLAB_PACKAGE
#define STICKLAB_PACKAGE "com.sticklab.app.debug"
#endif
#define APP_NAME         STICKLAB_PACKAGE  /* serve: Waechter sucht diesen Prozess */
#define APP_MISSES       2                    /* serve: so oft hintereinander nicht gefunden -> Ende */

/* Kurven-Datei (Phase 6b, B4). Aufbau genau wie CurveTableFile.kt in der App, alle Zahlen little endian:
 * "SLCT" | Version (2 Bytes) | Punkte (2) | 256 x links (je 2) | 256 x rechts (je 2) | CRC32 ueber alles davor (4) */
#define CURVE_PATH        "/data/user/0/" STICKLAB_PACKAGE "/files/curve_table.bin"  /* fest, nie aus dem Befehl (Regel 7) */
#define CURVE_MAGIC       "SLCT"
#define CURVE_VERSION     2        /* 2 seit Z11: zwei Trigger-Grenzen hinter den Tabellen */
#define CURVE_POINTS      256      /* Stuetzpunkte je Stick (CurveTable.SIZE) */
#define CURVE_MAX         32767    /* groesster Achsenwert von event8 = letzter Tabellenwert (CurveTable.MAX_OUTPUT) */
#define CURVE_HEADER_SIZE 8                                            /* Kennung + Version + Punkte */
#define CURVE_TRIGGERS_OFFSET (CURVE_HEADER_SIZE + 2 * CURVE_POINTS * 2) /* 1032: Grenze L2, Grenze R2, je 2 Bytes */
#define CURVE_CRC_OFFSET  (CURVE_TRIGGERS_OFFSET + 2 * 2)              /* 1036 */
#define CURVE_FILE_SIZE   (CURVE_CRC_OFFSET + 4)                       /* 1040 Bytes */
#define TRIGGER_LIMIT_MIN 3277     /* 10 % von 32767, wie SettingLimits.TRIGGER_FULL_OUTPUT in der App */

#define LBITS (sizeof(long) * 8)
#define NLONGS(x) (((x) + LBITS - 1) / LBITS)
#define TEST_BIT(bit, arr) (((arr)[(bit) / LBITS] >> ((bit) % LBITS)) & 1UL)

static volatile sig_atomic_t stop_flag = 0;
static void on_signal(int sig) { (void)sig; stop_flag = 1; }

static unsigned long evbits[NLONGS(EV_MAX + 1)];
static unsigned long keybits[NLONGS(KEY_MAX + 1)];
static unsigned long absbits[NLONGS(ABS_MAX + 1)];

static int sfd = -1, ufd = -1;
static int grabbed = 0, created = 0, ever_created = 0;
static struct input_id src_id;         /* Kennung von event8, von check_source gelesen und geprueft */

static long long now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000LL + ts.tv_nsec / 1000;
}

static void cleanup(void) {
    /* Zuerst event8 freigeben (Controller sofort wieder normal), dann das virtuelle Geraet weg */
    if (grabbed) {
        if (ioctl(sfd, EVIOCGRAB, 0) < 0) perror("EVIOCGRAB 0");
        else printf("event8 freigegeben\n");
        grabbed = 0;
    }
    if (ufd >= 0) {
        if (created) {
            if (ioctl(ufd, UI_DEV_DESTROY) < 0) perror("UI_DEV_DESTROY");
            else printf("virtuelles Geraet entfernt\n");
            created = 0;
        }
        close(ufd);
        ufd = -1;
    }
    if (sfd >= 0) { close(sfd); sfd = -1; }
}

/* R2: nur der eingebaute Controller, sonst nichts anfassen */
static int check_source(void) {
    struct input_id id;
    char name[256];
    memset(name, 0, sizeof(name));
    if (ioctl(sfd, EVIOCGID, &id) < 0) { perror("EVIOCGID"); return -1; }
    if (ioctl(sfd, EVIOCGNAME(sizeof(name) - 1), name) < 0) { perror("EVIOCGNAME"); return -1; }
    printf("event8: name=\"%s\" bus=0x%04x vendor=0x%04x product=0x%04x version=0x%04x\n",
           name, id.bustype, id.vendor, id.product, id.version);
    int ok = id.bustype == BUS_USB && id.vendor == 0x2020 &&
             ((id.product == 0x0112 && strcmp(name, "Xbox Wireless Controller") == 0) ||
              (id.product == 0x0111 && strcmp(name, "Odin Controller") == 0));
    if (!ok) { printf("FALSCHES GERAET -> Abbruch, nichts angefasst\n"); return -1; }
    src_id = id;

    if (ioctl(sfd, EVIOCGBIT(0, sizeof(evbits)), evbits) < 0 ||
        ioctl(sfd, EVIOCGBIT(EV_KEY, sizeof(keybits)), keybits) < 0 ||
        ioctl(sfd, EVIOCGBIT(EV_ABS, sizeof(absbits)), absbits) < 0) {
        perror("EVIOCGBIT"); return -1;
    }
    if (!TEST_BIT(EV_ABS, evbits) || !TEST_BIT(ABS_X, absbits) || !TEST_BIT(ABS_RZ, absbits)) {
        printf("event8 hat keine Stick-Achsen -> Abbruch\n");
        return -1;
    }
    return 0;
}

static void print_caps(void) {
    int nkeys = 0, nabs = 0;
    printf("Ereignis-Typen:");
    for (int t = 0; t <= EV_MAX; t++) if (TEST_BIT(t, evbits)) printf(" 0x%02x", t);
    printf(" (weitergegeben werden nur 0x00 SYN, 0x01 KEY, 0x03 ABS)\nTasten:");
    for (int c = 0; c <= KEY_MAX; c++) if (TEST_BIT(c, keybits)) { printf(" 0x%03x", c); nkeys++; }
    printf("  (%d)\n", nkeys);
    for (int c = 0; c <= ABS_MAX; c++) {
        if (!TEST_BIT(c, absbits)) continue;
        struct input_absinfo ai;
        if (ioctl(sfd, EVIOCGABS(c), &ai) < 0) { perror("EVIOCGABS"); continue; }
        printf("Achse 0x%02x: value %d, min %d, max %d, fuzz %d, flat %d, res %d\n",
               c, ai.value, ai.minimum, ai.maximum, ai.fuzz, ai.flat, ai.resolution);
        nabs++;
    }
    printf("Achsen: %d\n", nabs);
}

static int emit(int type, int code, int value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    return write(ufd, &ev, sizeof(ev)) == (ssize_t)sizeof(ev) ? 0 : -1;
}

/* ===== Kurven (nur Modus curve und curve_check, Phase 6b) ===== */

/* Ein Stick: letzte Werte von event8 (raw), zuletzt an das virtuelle Geraet geschriebene (out) und seine Tabelle.
 * R145: rechter Stick = ABS_Z/ABS_RZ (Vermutung aus der Achsenliste von event8, Trigger sind ABS_GAS/ABS_BRAKE) */
struct stick {
    const char *name;
    int code_x, code_y;
    int raw_x, raw_y;
    int out_x, out_y;
    int moved;                     /* neue Werte von event8 seit dem letzten SYN_REPORT */
    uint16_t table[CURVE_POINTS];
};
static struct stick sticks[2] = {
    { .name = "links", .code_x = ABS_X, .code_y = ABS_Y },
    { .name = "rechts", .code_x = ABS_Z, .code_y = ABS_RZ },
};
/* Ein Trigger (Z11, Wunsch Besitzer 05.11.2026): letzter Wert von event8 und die Grenze, ab der er schon voll ausgibt.
 * CURVE_MAX = keine Grenze. L2 = ABS_BRAKE, R2 = ABS_GAS (gesichert, Messung 15.10.2026). Die Tasten BTN_TL2/BTN_TR2 bleiben 1:1 */
struct trigger {
    const char *name;
    int code;
    int raw;
    int limit;
};
static struct trigger triggers[2] = {
    { .name = "L2", .code = ABS_BRAKE, .limit = CURVE_MAX },
    { .name = "R2", .code = ABS_GAS, .limit = CURVE_MAX },
};
static int curve_mode = 0;         /* Modus curve: Stick-Werte laufen ueber sticks[] */
static int curve_on = 0;           /* 1 = gueltige Tabelle geladen, 0 = Sticks 1:1 (B3) */

/* Zaehler fuer das Protokoll am Ende (Test 6b: Linear darf im Kreis hoechstens 1 abweichen, R144) */
static long long curve_points = 0, curve_changed = 0, curve_on_circle = 0;
static int curve_max_dev_inside = 0;
static long long trigger_changed = 0; /* Trigger-Werte, die durch die Grenze anders ausgegeben wurden */

static int round_to_int(double v) { return (int)(v >= 0 ? v + 0.5 : v - 0.5); }
static int limit_axis(int v) { return v > CURVE_MAX ? CURVE_MAX : v < -CURVE_MAX ? -CURVE_MAX : v; }
static unsigned read_u16(const unsigned char *p) { return p[0] | (p[1] << 8); }
static uint32_t read_u32(const unsigned char *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

/* CRC32 wie java.util.zip.CRC32 (Polynom 0xEDB88320), damit App und Programm dieselbe Pruefsumme rechnen */
static uint32_t crc32_of(const unsigned char *data, size_t len) {
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < len; i++) {
        crc ^= data[i];
        for (int bit = 0; bit < 8; bit++) crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
    }
    return ~crc;
}

/* Pruefregel wie CurveTable.isValid in der App: beginnt bei 0, endet bei CURVE_MAX, nie darueber,
 * steigt nie ab (sonst liefe der Stick rueckwaerts, R136). NULL = in Ordnung, sonst der Grund */
static const char *table_problem(const uint16_t *t) {
    if (t[0] != 0) return "beginnt nicht bei 0";
    if (t[CURVE_POINTS - 1] != CURVE_MAX) return "endet nicht bei 32767";
    for (int i = 1; i < CURVE_POINTS; i++) {
        if (t[i] > CURVE_MAX) return "Wert ueber 32767";
        if (t[i] < t[i - 1]) return "faellt ab";
    }
    return NULL;
}

/* Liest genau CURVE_FILE_SIZE Bytes aus einer normalen Datei (R142). NULL = gelesen, sonst der Grund */
static const char *read_curve_bytes(unsigned char *buf) {
    static char why[80];
    int fd = open(CURVE_PATH, O_RDONLY | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) {
        if (errno == ENOENT) return "fehlt";
        snprintf(why, sizeof(why), "ungueltig: nicht lesbar (%s)", strerror(errno));
        return why;
    }
    struct stat st;
    const char *problem = NULL;
    if (fstat(fd, &st) < 0) problem = "ungueltig: nicht lesbar (fstat)";
    else if (!S_ISREG(st.st_mode)) problem = "ungueltig: keine normale Datei";
    else if (st.st_size != CURVE_FILE_SIZE) problem = "ungueltig: falsche Groesse";
    size_t got = 0;
    while (!problem && got < CURVE_FILE_SIZE) {
        ssize_t n = read(fd, buf + got, CURVE_FILE_SIZE - got);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) problem = "ungueltig: zu kurz gelesen";
        else got += (size_t)n;
    }
    close(fd);
    return problem;
}

/* Liest und prueft die ganze Datei (R142, R143). Nur wenn alles stimmt, stehen die Tabellen in left/right und die
 * Trigger-Grenzen in limits. NULL = gueltig, sonst der Grund ("fehlt" oder "ungueltig: ...") */
static const char *read_curve_file(uint16_t *left, uint16_t *right, int *limits) {
    static char why[80];
    unsigned char buf[CURVE_FILE_SIZE];
    const char *problem = read_curve_bytes(buf);
    if (problem) return problem;
    if (memcmp(buf, CURVE_MAGIC, 4) != 0) return "ungueltig: falsche Kennung";
    if (read_u16(buf + 4) != CURVE_VERSION) return "ungueltig: falsche Version";
    if (read_u16(buf + 6) != CURVE_POINTS) return "ungueltig: falsche Punktzahl";
    if (read_u32(buf + CURVE_CRC_OFFSET) != crc32_of(buf, CURVE_CRC_OFFSET)) return "ungueltig: Pruefsumme falsch";
    for (int i = 0; i < CURVE_POINTS; i++) {
        left[i] = (uint16_t)read_u16(buf + CURVE_HEADER_SIZE + 2 * i);
        right[i] = (uint16_t)read_u16(buf + CURVE_HEADER_SIZE + 2 * (CURVE_POINTS + i));
    }
    for (int k = 0; k < 2; k++) {
        limits[k] = (int)read_u16(buf + CURVE_TRIGGERS_OFFSET + 2 * k);
        if (limits[k] < TRIGGER_LIMIT_MIN || limits[k] > CURVE_MAX) return "ungueltig: Trigger-Grenze ausserhalb";
    }
    const char *bad_left = table_problem(left), *bad_right = table_problem(right);
    if (!bad_left && !bad_right) return NULL;
    snprintf(why, sizeof(why), "ungueltig: %s %s", bad_left ? "links" : "rechts", bad_left ? bad_left : bad_right);
    return why;
}

/* Ein Stick-Punkt durch die Kurve (K6-2, K6-4): Laenge des Ausschlags in der Tabelle nachschlagen (zwischen zwei
 * Punkten gerade verbunden), Richtung bleibt. Ueber dem Kreis (schraeg, bis 141 %) zaehlt die Laenge als 100 %
 * -> Ausgabe auf dem Kreis. Faktor out_len / len ist nie negativ -> der Stick kehrt sich nie um (R136) */
static void curve_point(const uint16_t *t, int x, int y, int *ox, int *oy) {
    x = limit_axis(x);
    y = limit_axis(y);
    if (x == 0 && y == 0) { *ox = 0; *oy = 0; return; }   /* Ruhe bleibt immer genau 0 */
    double len = sqrt((double)x * x + (double)y * y);
    double pos = (len < CURVE_MAX ? len : CURVE_MAX) * (CURVE_POINTS - 1) / CURVE_MAX;
    int i = (int)pos;
    double out_len = i >= CURVE_POINTS - 1 ? t[CURVE_POINTS - 1] : t[i] + (pos - i) * (t[i + 1] - t[i]);
    double factor = out_len / len;
    *ox = limit_axis(round_to_int(x * factor));
    *oy = limit_axis(round_to_int(y * factor));
}

/* Trigger-Wert mit Grenze (Z11): ab limit voll, darunter gleichmaessig gestreckt. 0 bleibt immer genau 0, und ein
 * groesserer Wert wird nie kleiner ausgegeben. Ohne gueltige Tabelle kommt der Wert 1:1 durch (B3) */
static int trigger_value(int raw, int limit) {
    if (!curve_on || raw <= 0) return raw;
    long long scaled = ((long long)raw * CURVE_MAX + limit / 2) / limit;
    return scaled > CURVE_MAX ? CURVE_MAX : (int)scaled;
}

/* Gehoert die Achse zu einem Trigger? Sonst NULL */
static struct trigger *trigger_for(int code) {
    for (int k = 0; k < 2; k++) {
        if (code == triggers[k].code) return &triggers[k];
    }
    return NULL;
}

/* Neuen Wert von event8 merken und liefern, was an das virtuelle Geraet geht */
static int trigger_output(struct trigger *t, int raw) {
    t->raw = raw;
    int out = trigger_value(raw, t->limit);
    if (out != raw) trigger_changed++;
    return out;
}

/* Selbsttest beim Laden (R136): was die Tabelle aus 0/10/25/50/75/100 % geradeaus und schraeg voll macht.
 * Zum Vergleich mit der App (Precision: 25 % -> ca. 8 %) */
static void print_selftest(void) {
    static const int percents[] = { 0, 10, 25, 50, 75, 100 };
    for (int k = 0; k < 2; k++) {
        printf("Selbsttest %s (voll ab %.0f%%):", triggers[k].name, triggers[k].limit * 100.0 / CURVE_MAX);
        for (size_t p = 0; p < sizeof(percents) / sizeof(percents[0]); p++) {
            int out = trigger_value(round_to_int(percents[p] / 100.0 * CURVE_MAX), triggers[k].limit);
            printf(" %d%%->%.1f%%", percents[p], out * 100.0 / CURVE_MAX);
        }
        printf("\n");
    }
    for (int k = 0; k < 2; k++) {
        int ox, oy;
        printf("Selbsttest %s:", sticks[k].name);
        for (size_t p = 0; p < sizeof(percents) / sizeof(percents[0]); p++) {
            curve_point(sticks[k].table, round_to_int(percents[p] / 100.0 * CURVE_MAX), 0, &ox, &oy);
            printf(" %d%%->%.1f%%", percents[p], ox * 100.0 / CURVE_MAX);
        }
        curve_point(sticks[k].table, CURVE_MAX, CURVE_MAX, &ox, &oy);
        printf(" schraeg %d/%d->%d/%d\n", CURVE_MAX, CURVE_MAX, ox, oy);
    }
}

/* Laedt die Tabelle (beim Start und beim Neu-Laden). Gueltig -> Kurve an; sonst sofort 1:1 (B3) */
static void load_curve(const char *when) {
    uint16_t left[CURVE_POINTS], right[CURVE_POINTS];
    int limits[2];
    const char *problem = read_curve_file(left, right, limits);
    if (problem) {
        curve_on = 0;
        printf("Kurve (%s): Tabelle %s -> Sticks und Trigger 1:1\n", when, problem);
        return;
    }
    memcpy(sticks[0].table, left, sizeof(left));
    memcpy(sticks[1].table, right, sizeof(right));
    triggers[0].limit = limits[0];
    triggers[1].limit = limits[1];
    curve_on = 1;
    printf("Kurve (%s): Tabelle geladen\n", when);
    print_selftest();
}

/* Kennzeichen der Datei fuer das Neu-Laden (B1): andere Inode (die App benennt um), Zeit oder Groesse = neu.
 * lstat folgt keinem Link. Nicht lesbar zaehlt wie "fehlt" */
struct file_stamp { int exists; ino_t ino; time_t sec; long nsec; off_t size; };
static struct file_stamp curve_stamp;

static struct file_stamp stamp_now(void) {
    struct file_stamp s = { 0, 0, 0, 0, 0 };
    struct stat st;
    if (lstat(CURVE_PATH, &st) == 0) {
        s.exists = 1;
        s.ino = st.st_ino;
        s.sec = st.st_mtim.tv_sec;
        s.nsec = st.st_mtim.tv_nsec;
        s.size = st.st_size;
    }
    return s;
}

static int same_stamp(const struct file_stamp *a, const struct file_stamp *b) {
    return a->exists == b->exists && a->ino == b->ino && a->sec == b->sec && a->nsec == b->nsec && a->size == b->size;
}

/* Zaehler je umgerechnetem Punkt. Abweichung nur im Kreis gemessen, ausserhalb kuerzt K6-4 absichtlich */
static void count_curve_point(const struct stick *s, int ox, int oy) {
    curve_points++;
    curve_changed += (ox != s->raw_x) + (oy != s->raw_y);
    long long x = s->raw_x, y = s->raw_y;
    if (x * x + y * y > (long long)CURVE_MAX * CURVE_MAX) { curve_on_circle++; return; }
    int dev = abs(ox - s->raw_x) > abs(oy - s->raw_y) ? abs(ox - s->raw_x) : abs(oy - s->raw_y);
    if (dev > curve_max_dev_inside) curve_max_dev_inside = dev;
}

/* Gibt den Stick an das virtuelle Geraet: mit Kurve umgerechnet, sonst 1:1. Nur geaenderte Achsen,
 * all_axes = 1 schreibt beide (resync). Ohne SYN_REPORT, den schreibt der Aufrufer */
static int write_stick(struct stick *s, int all_axes) {
    int ox = s->raw_x, oy = s->raw_y;
    if (curve_on) {
        curve_point(s->table, s->raw_x, s->raw_y, &ox, &oy);
        count_curve_point(s, ox, oy);
    }
    if ((all_axes || ox != s->out_x) && emit(EV_ABS, s->code_x, ox) < 0) return -1;
    if ((all_axes || oy != s->out_y) && emit(EV_ABS, s->code_y, oy) < 0) return -1;
    s->out_x = ox;
    s->out_y = oy;
    s->moved = 0;
    return 0;
}

/* Gehoert die Achse zu einem Stick? Sonst NULL (Trigger, Steuerkreuz bleiben 1:1) */
static struct stick *stick_for(int code) {
    for (int k = 0; k < 2; k++) {
        if (code == sticks[k].code_x || code == sticks[k].code_y) return &sticks[k];
    }
    return NULL;
}

/* Neuen Wert von event8 merken. Ohne Kurve wird das Ereignis gleich 1:1 geschrieben, dann ist out = raw */
static void remember_raw(struct stick *s, int code, int value) {
    if (code == s->code_x) s->raw_x = value;
    else s->raw_y = value;
    s->moved = 1;
    if (!curve_on) {
        s->out_x = s->raw_x;
        s->out_y = s->raw_y;
        s->moved = 0;
    }
}

/* Einmal pro Sekunde (B1): Datei neu, anders oder weg -> neu laden und beide Sticks sofort neu ausgeben,
 * damit ein still gehaltener Stick nicht auf der alten Kurve stehen bleibt. -1 = Schreiben gescheitert */
static int check_curve_file(void) {
    struct file_stamp now = stamp_now();
    if (same_stamp(&now, &curve_stamp)) return 0;
    curve_stamp = now;
    load_curve("neu");
    for (int k = 0; k < 2; k++) {
        if (write_stick(&sticks[k], 0) < 0) return -1;
        /* Ein gehaltener Trigger bekommt die neue Grenze sofort, nicht erst bei der naechsten Bewegung */
        if (emit(EV_ABS, triggers[k].code, trigger_output(&triggers[k], triggers[k].raw)) < 0) return -1;
    }
    return emit(EV_SYN, SYN_REPORT, 0);
}

/* Modus curve_check: nur Datei lesen, pruefen, Selbsttest. 0 = gueltig, 5 = fehlt oder ungueltig */
static int curve_check(void) {
    load_curve("Pruefung");
    printf("curve_check: fertig, nur die Tabellen-Datei gelesen\n");
    return curve_on ? 0 : 5;
}

/* copy_id = 1: Kennung von event8 uebernehmen (mirror_hide_id), sonst vid/pid 0 */
static int create_virtual(int copy_id) {
    ufd = open(UINPUT_PATH, O_WRONLY | O_CLOEXEC);
    if (ufd < 0) { perror("open /dev/uinput"); return -1; }
    if (ioctl(ufd, UI_SET_EVBIT, EV_KEY) < 0 || ioctl(ufd, UI_SET_EVBIT, EV_ABS) < 0) {
        perror("UI_SET_EVBIT"); return -1;
    }
    for (int c = 0; c <= KEY_MAX; c++) {
        if (TEST_BIT(c, keybits) && ioctl(ufd, UI_SET_KEYBIT, c) < 0) { perror("UI_SET_KEYBIT"); return -1; }
    }
    for (int c = 0; c <= ABS_MAX; c++) {
        if (!TEST_BIT(c, absbits)) continue;
        struct uinput_abs_setup as;
        memset(&as, 0, sizeof(as));
        as.code = c;
        if (ioctl(sfd, EVIOCGABS(c), &as.absinfo) < 0) { perror("EVIOCGABS"); return -1; }
        if (ioctl(ufd, UI_SET_ABSBIT, c) < 0 || ioctl(ufd, UI_ABS_SETUP, &as) < 0) {
            perror("UI_ABS_SETUP"); return -1;
        }
    }
    struct uinput_setup us;
    memset(&us, 0, sizeof(us));
    if (copy_id) us.id = src_id;   /* gleiche Kennung wie event8, R2 hat sie geprueft */
    else us.id.bustype = BUS_USB;  /* vid/pid 0 wie in den Tests 1b-2 und 2 */
    snprintf(us.name, sizeof(us.name), "%s", VIRT_NAME);
    if (ioctl(ufd, UI_DEV_SETUP, &us) < 0) { perror("UI_DEV_SETUP"); return -1; }
    if (ioctl(ufd, UI_DEV_CREATE) < 0) { perror("UI_DEV_CREATE"); return -1; }
    created = ever_created = 1;
    char sys[64] = "?";
    ioctl(ufd, UI_GET_SYSNAME(sizeof(sys)), sys);
    printf("virtuelles Geraet \"%s\" angelegt (%s) bus=0x%04x vendor=0x%04x product=0x%04x version=0x%04x\n",
           VIRT_NAME, sys, us.id.bustype, us.id.vendor, us.id.product, us.id.version);
    return 0;
}

/* Aktuellen Stand von event8 auf das virtuelle Geraet uebertragen
 * (nach dem Abgreifen und nach SYN_DROPPED), damit nichts haengen bleibt.
 * Im Modus curve laufen die Sticks dabei durch die Kurve, sonst springt der Stick kurz (R146) */
static int resync(void) {
    unsigned long keystate[NLONGS(KEY_MAX + 1)];
    memset(keystate, 0, sizeof(keystate));
    if (ioctl(sfd, EVIOCGKEY(sizeof(keystate)), keystate) < 0) { perror("EVIOCGKEY"); return -1; }
    for (int c = 0; c <= KEY_MAX; c++) {
        if (TEST_BIT(c, keybits) && emit(EV_KEY, c, TEST_BIT(c, keystate) ? 1 : 0) < 0) return -1;
    }
    for (int c = 0; c <= ABS_MAX; c++) {
        if (!TEST_BIT(c, absbits)) continue;
        struct input_absinfo ai;
        if (ioctl(sfd, EVIOCGABS(c), &ai) < 0) { perror("EVIOCGABS"); return -1; }
        struct stick *s = curve_mode ? stick_for(c) : NULL;
        if (s) { remember_raw(s, c, ai.value); continue; }
        struct trigger *t = curve_mode ? trigger_for(c) : NULL;
        if (emit(EV_ABS, c, t ? trigger_output(t, ai.value) : ai.value) < 0) return -1;
    }
    for (int k = 0; curve_mode && k < 2; k++) {
        if (write_stick(&sticks[k], 1) < 0) return -1;
    }
    return emit(EV_SYN, SYN_REPORT, 0);
}

static int grab_source(void) {
    int clk = CLOCK_MONOTONIC;         /* betrifft nur unsere eigene Leseverbindung */
    if (ioctl(sfd, EVIOCSCLOCKID, &clk) < 0) perror("EVIOCSCLOCKID (nur Messung betroffen)");
    if (ioctl(sfd, EVIOCGRAB, 1) < 0) { perror("EVIOCGRAB 1"); return -1; }
    grabbed = 1;
    return 0;
}

/* serve, Waechter: Ist /proc/<pid>/cmdline genau APP_NAME? (erster Text bis zum Nullzeichen) */
static int app_pid = -1;               /* zuletzt gefundener App-Prozess, spart die volle Suche */

static int is_app(int pid) {
    char path[32], buf[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return 0;
    buf[n] = 0;
    return strcmp(buf, APP_NAME) == 0;
}

/* 1 = App-Prozess gefunden (eingefroren zaehlt als da). Liest nur, aendert nichts.
 * Kann /proc nicht gelesen werden -> 0 (lieber beenden als blind weiterlaufen) */
static int app_alive(void) {
    if (app_pid > 0 && is_app(app_pid)) return 1;
    app_pid = -1;
    DIR *d = opendir("/proc");
    if (!d) return 0;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        int pid = 0;
        const char *c = e->d_name;
        for (; *c >= '0' && *c <= '9'; c++) pid = pid * 10 + (*c - '0');
        if (*c != 0 || pid <= 0) continue;     /* nur Ordner, die nur aus Ziffern bestehen */
        if (is_app(pid)) { app_pid = pid; break; }
    }
    closedir(d);
    return app_pid > 0;
}

/* Wartet in 100-ms-Schritten auf Strg+C/kill; 1 = abgebrochen */
static int wait_ms(int ms) {
    for (int waited = 0; waited < ms && !stop_flag; waited += 100) usleep(100 * 1000);
    return stop_flag != 0;
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IOLBF, 0);
    if (argc != 2 || (strcmp(argv[1], "check") != 0 && strcmp(argv[1], "mirror") != 0 &&
                      strcmp(argv[1], "mirror_hide") != 0 && strcmp(argv[1], "mirror_hide_id") != 0 &&
                      strcmp(argv[1], "serve") != 0 && strcmp(argv[1], "curve") != 0 &&
                      strcmp(argv[1], "curve_check") != 0)) {
        fprintf(stderr, "Aufruf: sticklab_mirror check|mirror|mirror_hide|mirror_hide_id|serve|curve|curve_check\n");
        return 2;
    }
    if (strcmp(argv[1], "curve_check") == 0) return curve_check();
    curve_mode = strcmp(argv[1], "curve") == 0;
    int serve = curve_mode || strcmp(argv[1], "serve") == 0;   /* curve laeuft wie serve, nur mit Kurve */
    int copy_id = serve || strcmp(argv[1], "mirror_hide_id") == 0;
    int hide = copy_id || strcmp(argv[1], "mirror_hide") == 0;
    int mirror = hide || strcmp(argv[1], "mirror") == 0;
    /* Zeitlimit nur fuer die alten Testmodi; serve laeuft ohne Frist und ohne alarm
     * (Z-1, 15.10.2026, ersetzt die 10 min aus E-S5b) */
    int has_limit = mirror && !serve;
    long long limit_ms = hide ? HIDE_LIMIT_MS : LIMIT_MS;
    if (has_limit) alarm(hide ? HIDE_HARD_LIMIT_S : HARD_LIMIT_S);   /* SIGALRM: Prozess endet hart */
    if (serve && !app_alive()) {
        printf("Waechter: " APP_NAME " nicht gefunden -> Abbruch, nichts angefasst\n");
        return 4;
    }
    if (serve) printf("Waechter: " APP_NAME " gefunden (pid %d)\n", app_pid);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = on_signal;         /* ohne SA_RESTART: poll wird unterbrochen */
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGHUP, &sa, NULL);
    signal(SIGPIPE, SIG_IGN);

    sfd = open(SRC_PATH, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (sfd < 0) { perror("open " SRC_PATH); return 3; }
    if (check_source() < 0) { cleanup(); return 3; }
    print_caps();
    if (!mirror) {
        cleanup();
        printf("check: fertig, nichts veraendert\n");
        return 0;
    }
    /* Kennzeichen vor dem Laden merken: aendert sich die Datei genau dazwischen, laedt der 1-s-Takt neu */
    if (curve_mode) {
        curve_stamp = stamp_now();
        load_curve("Start");
    }

    const char *reason = "?";
    int rc = 1;
    long long total_read = 0, total_written = 0, skipped = 0, dropped = 0;
    long long lat_n = 0, lat_sum = 0, lat_min = -1, lat_max = 0;

    if (hide) {
        /* Erst abgreifen, dann auf das Loeschen der Datei warten, erst dann anlegen */
        if (grab_source() < 0) { reason = "Abgreifen gescheitert"; goto out; }
        long long t0 = now_us();
        printf("event8 abgegriffen, warte auf Loeschen von " SRC_PATH " (hoechstens %d s)\n",
               HIDE_WAIT_MS / 1000);
        int gone = 0;
        while (!stop_flag && now_us() - t0 < HIDE_WAIT_MS * 1000LL) {
            if (access(SRC_PATH, F_OK) != 0 && errno == ENOENT) { gone = 1; break; }
            usleep(100 * 1000);
        }
        if (stop_flag) { reason = "Signal beim Warten"; rc = 0; goto out; }
        if (!gone) { reason = "Datei nicht verschwunden (Skript lief nicht?), nichts angelegt"; rc = 0; goto out; }
        printf("Datei weg nach %lld ms\n", (now_us() - t0) / 1000);
        if (copy_id && wait_ms(ID_GAP_MS)) { reason = "Signal vor dem Anlegen"; rc = 0; goto out; }
        if (create_virtual(copy_id) < 0) { reason = "virtuelles Geraet nicht angelegt"; goto out; }
        if (wait_ms(HIDE_SETTLE_MS)) { reason = "Signal nach dem Anlegen"; rc = 0; goto out; }
    } else {
        if (create_virtual(0) < 0) { reason = "virtuelles Geraet nicht angelegt"; goto out; }
        if (wait_ms(SETTLE_MS)) { reason = "Signal vor dem Abgreifen"; rc = 0; goto out; }
        if (grab_source() < 0) { reason = "Abgreifen gescheitert"; goto out; }
    }
    if (has_limit) printf("event8 abgegriffen, gebe 1:1 weiter (hoechstens %lld s)\n", limit_ms / 1000);
    else if (curve_mode) printf("event8 abgegriffen, gebe weiter: Sticks %s, Rest 1:1 (ohne Zeitlimit, bis Stopp oder Waechter)\n",
                                curve_on ? "mit Kurve" : "1:1");
    else printf("event8 abgegriffen, gebe 1:1 weiter (ohne Zeitlimit, bis Stopp oder Waechter)\n");

    /* Alte Ereignisse aus der Zeit vor dem Abgreifen verwerfen (Android hat sie schon) */
    struct input_event ev[64];
    while (read(sfd, ev, sizeof(ev)) > 0) {}
    if (resync() < 0) { perror("resync"); reason = "Schreiben an uinput gescheitert"; goto out; }

    long long start = now_us(), deadline = start + limit_ms * 1000LL;
    long long win_start = start, win_read = 0, win_written = 0;
    int second = 0, app_missing = 0;

    while (1) {
        if (stop_flag) { reason = "Signal (Strg+C / kill)"; rc = 0; break; }
        long long t = now_us();
        if (has_limit && t >= deadline) {
            reason = hide ? "Zeitlimit 15 s" : "Zeitlimit 20 s";
            rc = 0; break;
        }
        if (t - win_start >= 1000000) {
            second++;
            if (has_limit) printf("t=%2ds gelesen=%lld geschrieben=%lld\n", second, win_read, win_written);
            win_start += 1000000;
            win_read = win_written = 0;
            if (serve) {
                /* Waechter einmal pro Sekunde. Zweimal hintereinander weg -> Ende (R30) */
                app_missing = app_alive() ? 0 : app_missing + 1;
                if (app_missing > 0) printf("Waechter: " APP_NAME " nicht gefunden (%d/%d)\n", app_missing, APP_MISSES);
                if (app_missing >= APP_MISSES) { reason = "Waechter: App weg"; rc = 0; break; }
            }
            if (curve_mode && check_curve_file() < 0) { perror("write uinput"); reason = "Schreiben an uinput gescheitert"; break; }
        }
        int poll_timeout_ms = POLL_MS;
        if (has_limit) {
            long long left_ms = (deadline - t) / 1000 + 1;   /* nicht ueber die Frist hinaus warten */
            if (left_ms < poll_timeout_ms) poll_timeout_ms = (int)left_ms;
        }
        struct pollfd p = { .fd = sfd, .events = POLLIN, .revents = 0 };
        int r = poll(&p, 1, poll_timeout_ms);
        if (r < 0) {
            if (errno == EINTR) continue;
            perror("poll"); reason = "poll-Fehler"; break;
        }
        if (p.revents & (POLLERR | POLLHUP | POLLNVAL)) { reason = "event8 verschwunden (Layout-Wechsel?)"; break; }
        if (r == 0 || !(p.revents & POLLIN)) continue;

        ssize_t n = read(sfd, ev, sizeof(ev));
        if (n < 0) {
            if (errno == EAGAIN || errno == EINTR) continue;
            if (errno == ENODEV) { reason = "event8 verschwunden (Layout-Wechsel?)"; break; }
            perror("read event8"); reason = "Lesefehler"; break;
        }
        int cnt = (int)(n / (ssize_t)sizeof(struct input_event));
        total_read += cnt;
        win_read += cnt;
        if (win_read > MAX_EVENTS_PER_S) { reason = "R9: zu viele Ereignisse auf event8 (Rueckfluss?)"; break; }

        int failed = 0;
        for (int i = 0; i < cnt; i++) {
            if (ev[i].type == EV_SYN && ev[i].code == SYN_DROPPED) {
                dropped++;
                if (resync() < 0) { failed = 1; break; }
                continue;
            }
            if (ev[i].type != EV_SYN && ev[i].type != EV_KEY && ev[i].type != EV_ABS) { skipped++; continue; }
            /* Modus curve: Stick-Werte merken; mit Kurve erst beim SYN_REPORT umgerechnet schreiben (je Paar X/Y) */
            struct stick *s = curve_mode && ev[i].type == EV_ABS ? stick_for(ev[i].code) : NULL;
            if (s) {
                remember_raw(s, ev[i].code, ev[i].value);
                if (curve_on) continue;
            }
            /* Modus curve: Trigger mit Grenze (Z11). Je Wert fuer sich, gleich hier umgeschrieben; Rest laeuft normal */
            struct trigger *t = curve_mode && ev[i].type == EV_ABS ? trigger_for(ev[i].code) : NULL;
            if (t) ev[i].value = trigger_output(t, ev[i].value);
            if (curve_on && ev[i].type == EV_SYN && ev[i].code == SYN_REPORT) {
                for (int k = 0; k < 2 && !failed; k++) {
                    if (sticks[k].moved && write_stick(&sticks[k], 0) < 0) failed = 1;
                }
                if (failed) break;
            }
            if (write(ufd, &ev[i], sizeof(ev[i])) != (ssize_t)sizeof(ev[i])) { failed = 1; break; }
            total_written++;
            win_written++;
            if (ev[i].type == EV_SYN && ev[i].code == SYN_REPORT) {
                /* Verzoegerung: Zeitstempel von event8 bis nach unserem Schreiben */
                long long ev_us = (long long)ev[i].input_event_sec * 1000000LL + ev[i].input_event_usec;
                long long lat = now_us() - ev_us;
                lat_n++;
                lat_sum += lat;
                if (lat_min < 0 || lat < lat_min) lat_min = lat;
                if (lat > lat_max) lat_max = lat;
            }
        }
        if (failed) { perror("write uinput"); reason = "Schreiben an uinput gescheitert"; break; }
    }

out:
    cleanup();
    /* R20: erst enden (dann legt das Skript event8 wieder an), wenn Android das Geraet
     * mit derselben Kennung sicher geschlossen hat. Auch nach Strg+C, nur nicht nach KILL
     * (dann fehlt die Zeile "Ende:" und s5.sh wartet selbst 0,3 s, Z-1) */
    if (copy_id && ever_created) usleep(ID_GAP_MS * 1000);
    printf("Ende: %s\n", reason);
    printf("gelesen=%lld geschrieben=%lld uebersprungen=%lld SYN_DROPPED=%lld\n",
           total_read, total_written, skipped, dropped);
    if (lat_n > 0)
        printf("Verzoegerung event8 -> uinput (je SYN_REPORT, n=%lld): min %lld us, mittel %lld us, max %lld us\n",
               lat_n, lat_min, lat_sum / lat_n, lat_max);
    if (curve_mode)
        printf("Kurve: umgerechnete Punkte=%lld geaenderte Werte=%lld groesste Abweichung im Kreis=%d "
               "auf den Kreis gekuerzt=%lld (am Ende %s)\n",
               curve_points, curve_changed, curve_max_dev_inside, curve_on_circle, curve_on ? "mit Kurve" : "1:1");
    if (curve_mode) printf("Trigger: durch die Grenze geaenderte Werte=%lld\n", trigger_changed);
    return rc;
}
