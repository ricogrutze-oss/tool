#!/usr/bin/env python3
"""Lädt den FET-Stundenplan (PDF) der HS Anhalt, parst ihn und schreibt
einen ICS-Kalender-Feed + events.json. Schreibt nur bei echten Änderungen.

Aufruf: stundenplan.py <ausgabeordner> [pdf-datei]
Ohne pdf-datei wird die PDF von PDF_URL geladen.
"""
import datetime as dt
import json
import os
import re
import sys
import urllib.request

import pdfplumber

PDF_URL = os.environ.get(
    "PDF_URL",
    "https://www.hs-anhalt.de/fileadmin/Dateien/FB6/studiengaenge/"
    "Elektrotechnik_Fern_B_Eng/Dokumente/FET_2025_WS_26_27_11.09.2026.pdf",
)
CAL_NAME = "Stundenplan FET 2025"

RE_DATE = re.compile(r"\b(\d{2})\.(\d{2})\.(\d{4})\b")
RE_TIME = re.compile(r"(\d{1,2})[:.](\d{2})\s*-\s*(\d{1,2})[:.](\d{2})")
RE_ROOM = re.compile(r"\s+(\d{2}/[A-Za-z0-9]+(?:-\d+)?)\s*$")
RE_SEM = re.compile(r"Stundenplan f\S*r das (\d+)\. Semester")
RE_STAND = re.compile(r"Stand:\s*(\d{2}\.\d{2}\.\d{4})")
WEEKDAYS = ("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")


def load_pdf(path=None):
    if path:
        return path
    req = urllib.request.Request(PDF_URL, headers={"User-Agent": "Mozilla/5.0 (Stundenplan-Sync)"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    if not data.startswith(b"%PDF"):
        raise SystemExit("Antwort ist keine PDF – URL geändert?")
    tmp = "/tmp/stundenplan.pdf"
    with open(tmp, "wb") as f:
        f.write(data)
    return tmp


def parse(pdf_path):
    events, stands = [], []
    semester, day = None, None
    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            for line in (page.extract_text(layout=True) or "").splitlines():
                if m := RE_SEM.search(line):
                    semester = m.group(1)
                    continue
                if m := RE_STAND.search(line):
                    stands.append(m.group(1))
                    continue
                if m := RE_DATE.search(line):
                    day = dt.date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
                    line = line[: m.start()] + line[m.end():]
                t = RE_TIME.search(line)
                if not t or not day:
                    continue
                rest = line[t.end():]
                for w in WEEKDAYS:
                    rest = rest.replace(w, " ")
                room = ""
                if r := RE_ROOM.search(rest):
                    room = r.group(1)
                    rest = rest[: r.start()]
                subject = " ".join(rest.split()).strip(" -")
                if not subject:
                    continue  # leerer Block / "-"
                h1, m1, h2, m2 = map(int, t.groups())
                events.append({
                    "date": day.isoformat(),
                    "start": f"{h1:02d}:{m1:02d}",
                    "end": f"{h2:02d}:{m2:02d}",
                    "subject": subject,
                    "room": room,
                    "semester": semester,
                })
    events.sort(key=lambda e: (e["date"], e["start"]))
    return events, stands


def esc(s):
    return (s.replace("\\", "\\\\").replace(";", "\\;")
             .replace(",", "\\,").replace("\n", "\\n"))


VTIMEZONE = """BEGIN:VTIMEZONE
TZID:Europe/Berlin
BEGIN:DAYLIGHT
TZOFFSETFROM:+0100
TZOFFSETTO:+0200
TZNAME:CEST
DTSTART:19700329T020000
RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
END:DAYLIGHT
BEGIN:STANDARD
TZOFFSETFROM:+0200
TZOFFSETTO:+0100
TZNAME:CET
DTSTART:19701025T030000
RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
END:STANDARD
END:VTIMEZONE"""


def to_ics(events):
    lines = [
        "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//stundenplan-sync//DE",
        "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
        f"X-WR-CALNAME:{CAL_NAME}", "X-WR-TIMEZONE:Europe/Berlin",
        "REFRESH-INTERVAL;VALUE=DURATION:PT4H", "X-PUBLISHED-TTL:PT4H",
        VTIMEZONE,
    ]
    seen = {}
    for e in events:
        d = e["date"].replace("-", "")
        # UID hängt am Zeitslot -> Änderungen an Fach/Raum aktualisieren den Termin
        slot = f"{d}-{e['start'].replace(':', '')}"
        n = seen.get(slot, 0)
        seen[slot] = n + 1
        uid = f"fet2025-{slot}{'-' + str(n) if n else ''}@stundenplan-sync"
        desc = f"{e['semester']}. Semester FET Matrikel 2025" if e["semester"] else ""
        loc = f"Raum {e['room']}, Hochschule Anhalt" if e["room"] else "Hochschule Anhalt"
        lines += [
            "BEGIN:VEVENT",
            f"UID:{uid}",
            f"DTSTAMP:{d}T000000Z",
            f"DTSTART;TZID=Europe/Berlin:{d}T{e['start'].replace(':', '')}00",
            f"DTEND;TZID=Europe/Berlin:{d}T{e['end'].replace(':', '')}00",
            f"SUMMARY:{esc(e['subject'])}",
            f"LOCATION:{esc(loc)}",
            f"DESCRIPTION:{esc(desc)}",
            "TRANSP:OPAQUE",
            "END:VEVENT",
        ]
    lines.append("END:VCALENDAR")
    return "\r\n".join(lines) + "\r\n"


def diff(old, new):
    k = lambda e: f"{e['date']} {e['start']}"
    o, n = {k(e): e for e in old}, {k(e): e for e in new}
    out = []
    for key in sorted(set(o) | set(n)):
        a, b = o.get(key), n.get(key)
        fmt = lambda e: f"{e['subject']}" + (f" ({e['room']})" if e["room"] else "")
        if a and not b:
            out.append(f"- entfallen: {key} {fmt(a)}")
        elif b and not a:
            out.append(f"- neu: {key}–{b['end']} {fmt(b)}")
        elif a != b:
            out.append(f"- geändert: {key}: {fmt(a)} → {fmt(b)} (bis {b['end']})")
    return out


def main():
    outdir = sys.argv[1]
    pdf = load_pdf(sys.argv[2] if len(sys.argv) > 2 else None)
    events, stands = parse(pdf)
    if len(events) < 5:
        raise SystemExit(f"Nur {len(events)} Termine erkannt – PDF-Layout geändert?")

    jpath = os.path.join(outdir, "events.json")
    old = []
    if os.path.exists(jpath):
        with open(jpath, encoding="utf-8") as f:
            old = json.load(f).get("events", [])
    changes = diff(old, events)
    if old and not changes:
        print("Keine Änderungen.")
        return

    with open(os.path.join(outdir, "plan.ics"), "w", encoding="utf-8", newline="") as f:
        f.write(to_ics(events))
    with open(jpath, "w", encoding="utf-8") as f:
        json.dump({
            "stand": stands,
            "checked": dt.date.today().isoformat(),
            "source": PDF_URL,
            "events": events,
        }, f, ensure_ascii=False, indent=1)
    summary = "\n".join(changes) if old else f"Erstimport: {len(events)} Termine"
    print(summary)
    if gh := os.environ.get("GITHUB_OUTPUT"):
        with open(gh, "a") as f:
            f.write("changed=true\n")
            f.write(f"stand={', '.join(dict.fromkeys(stands))}\n")
        with open("/tmp/changes.md", "w", encoding="utf-8") as f:
            f.write(summary + "\n")


if __name__ == "__main__":
    main()
