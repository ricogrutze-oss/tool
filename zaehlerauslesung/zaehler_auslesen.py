#!/usr/bin/env python3
"""
Zählerauslesung über optischen Tastkopf (z. B. Device DvBTIR 7G) nach IEC 62056-21.

Voraussetzung unter Windows:
  - DvBTIR 7G BLE-zu-COM-Treiber von device.de installiert (stellt virtuellen COM-Port bereit)
  - pip install pyserial

Beispiele:
  python zaehler_auslesen.py --list                 # verfügbare COM-Ports anzeigen
  python zaehler_auslesen.py COM5                   # Datenauslesung Mode C (alle Werte)
  python zaehler_auslesen.py COM5 --adresse 12345   # Zähler mit Geräteadresse ansprechen
  python zaehler_auslesen.py COM5 --max-baud 9600   # Baudrate begrenzen
  python zaehler_auslesen.py COM5 --sml             # SML-Zähler (eHZ/mME) passiv mitschneiden
  python zaehler_auslesen.py COM5 --ultraheat       # Wärmezähler Landis+Gyr UH50/T550

Ergebnis: Rohdaten (.txt) und geparste Werte (.csv) im aktuellen Ordner.
"""

import argparse
import csv
import re
import sys
import time
from datetime import datetime

try:
    import serial
    import serial.tools.list_ports
except ImportError:
    sys.exit("pyserial fehlt: pip install pyserial")

STX, ETX, ACK, NAK, EOT = b"\x02", b"\x03", b"\x06", b"\x15", b"\x04"
BAUD_CODES = {"0": 300, "1": 600, "2": 1200, "3": 2400, "4": 4800,
              "5": 9600, "6": 19200, "7": 38400, "8": 57600, "9": 115200}
SATZ_RE = re.compile(r"([^()\s]+)((?:\([^)]*\))+)")


def oeffne(port, baud):
    return serial.Serial(port, baudrate=baud, bytesize=serial.SEVENBITS,
                         parity=serial.PARITY_EVEN, stopbits=serial.STOPBITS_ONE,
                         timeout=2)


def lies_zeile(ser, timeout=3.0):
    ende = time.time() + timeout
    puffer = b""
    while time.time() < ende:
        b = ser.read(1)
        if not b:
            continue
        puffer += b
        if puffer.endswith(b"\r\n"):
            return puffer
    return puffer


def bcc_ok(block):
    """block = alles nach STX bis einschließlich ETX, gefolgt von BCC-Byte."""
    bcc = 0
    for b in block[:-1]:
        bcc ^= b
    return bcc == block[-1]


def mode_c(port, adresse, max_baud):
    ser = oeffne(port, 300)
    ser.reset_input_buffer()

    anfrage = f"/?{adresse}!\r\n".encode("ascii")
    print(f"> {anfrage!r}")
    ser.write(anfrage)
    ser.flush()

    kennung = lies_zeile(ser, 4)
    # Echo der eigenen Anfrage überspringen (manche Köpfe spiegeln)
    if kennung.startswith(b"/?"):
        kennung = lies_zeile(ser, 4)
    if not kennung.startswith(b"/"):
        ser.close()
        sys.exit(f"Keine Antwort vom Zähler (empfangen: {kennung!r}). Kopf richtig aufgesetzt?")

    kennung_txt = kennung.decode("ascii", "replace").strip()
    print(f"< {kennung_txt}")
    hersteller = kennung_txt[1:4]
    baud_z = kennung_txt[4]
    zaehler_baud = BAUD_CODES.get(baud_z, 300)
    if zaehler_baud > max_baud:
        baud_z = next(k for k, v in sorted(BAUD_CODES.items(), key=lambda x: -x[1]) if v <= max_baud)
        zaehler_baud = BAUD_CODES[baud_z]
    print(f"Hersteller: {hersteller}, Baudrate: {zaehler_baud}")

    # Optionsauswahl: ACK 0 Z 0 = Datenauslesung mit Baudrate Z
    time.sleep(0.3)
    ser.write(ACK + f"0{baud_z}0\r\n".encode("ascii"))
    ser.flush()
    time.sleep(0.3)
    ser.baudrate = zaehler_baud  # Treiber setzt die Umschaltung am Kopf selbst um

    rohdaten = b""
    ende = time.time() + 120
    while time.time() < ende:
        chunk = ser.read(512)
        if chunk:
            rohdaten += chunk
            ende = time.time() + 5  # nach letztem Byte noch 5 s warten
            i = rohdaten.find(ETX)
            if i != -1 and len(rohdaten) > i + 1:
                break
    ser.close()

    if STX not in rohdaten:
        sys.exit(f"Kein Datenblock empfangen ({len(rohdaten)} Bytes).")

    start = rohdaten.index(STX) + 1
    etx = rohdaten.index(ETX, start)
    block = rohdaten[start:etx + 2]
    print("BCC OK" if len(block) == etx - start + 2 and bcc_ok(block)
          else "WARNUNG: Prüfsumme (BCC) passt nicht – Daten ggf. unvollständig")

    text = rohdaten[start:etx].decode("ascii", "replace")
    return kennung_txt, text


def ultraheat(port):
    """Landis+Gyr UH50/T550: 40 Null-Bytes wecken, /?! bei 300 Baud 7E2,
    danach sendet der Zähler selbstständig mit 2400 Baud."""
    ser = serial.Serial(port, baudrate=300, bytesize=serial.SEVENBITS,
                        parity=serial.PARITY_EVEN, stopbits=serial.STOPBITS_TWO, timeout=2)
    ser.reset_input_buffer()
    ser.write(b"\x00" * 40 + b"/?!\r\n")
    ser.flush()
    kennung = lies_zeile(ser, 6)
    if kennung.startswith(b"/?"):
        kennung = lies_zeile(ser, 6)
    if not kennung.startswith(b"/"):
        ser.close()
        sys.exit(f"Keine Antwort vom Wärmezähler (empfangen: {kennung!r}).")
    ser.baudrate = 2400
    print(f"< {kennung.decode('ascii', 'replace').strip()}  -> 2400 Baud")
    rohdaten = b""
    ende = time.time() + 30
    while time.time() < ende:
        chunk = ser.read(512)
        if chunk:
            rohdaten += chunk
            ende = time.time() + 4
            i = rohdaten.find(ETX)
            if i != -1 and len(rohdaten) > i + 1:
                break
    ser.close()
    start = rohdaten.find(STX) + 1
    etx = rohdaten.find(ETX, start)
    if etx < 0:
        sys.exit(f"Kein vollständiger Datenblock ({len(rohdaten)} Bytes).")
    print("BCC OK" if bcc_ok(rohdaten[start:etx + 2]) else "WARNUNG: Prüfsumme (BCC) passt nicht")
    return kennung.decode("ascii", "replace").strip(), rohdaten[start:etx].decode("ascii", "replace")


def parse(text):
    werte = []
    for zeile in text.splitlines():
        zeile = zeile.strip()
        if not zeile or zeile == "!":
            continue
        treffer = list(SATZ_RE.finditer(zeile))
        if not treffer:
            werte.append(("", zeile, "", ""))
            continue
        # mehrere Sätze pro Zeile möglich (z. B. Wärmezähler UH50)
        for m in treffer:
            kennzahl, klammern = m.groups()
            gruppen = re.findall(r"\(([^)]*)\)", klammern)
            inhalt = gruppen[0] if gruppen else ""
            if "&" in inhalt:
                wert, einheit = inhalt, ""
            else:
                wert, einheit = (inhalt.split("*", 1) + [""])[:2]
            werte.append((kennzahl, wert, einheit, " | ".join(gruppen[1:])))
    return werte


def sml_mitschnitt(port, dauer):
    ser = serial.Serial(port, 9600, bytesize=8, parity="N", stopbits=1, timeout=1)
    print(f"Schneide {dauer} s SML mit …")
    daten = b""
    ende = time.time() + dauer
    while time.time() < ende:
        daten += ser.read(1024)
    ser.close()
    stempel = datetime.now().strftime("%Y%m%d_%H%M%S")
    name = f"sml_{stempel}.bin"
    with open(name, "wb") as f:
        f.write(daten)
    print(f"{len(daten)} Bytes gespeichert: {name}")
    # OBIS-Kennungen grob finden (77 07 + 6 Byte OBIS)
    for m in re.finditer(rb"\x77\x07(.{6})", daten, re.S):
        a, b, c, d, e, f_ = m.group(1)
        print(f"  gefunden: {a}-{b}:{c}.{d}.{e}*{f_}")


def main():
    ap = argparse.ArgumentParser(description="Zähler über optischen Tastkopf auslesen")
    ap.add_argument("port", nargs="?", help="COM-Port, z. B. COM5")
    ap.add_argument("--list", action="store_true", help="COM-Ports auflisten")
    ap.add_argument("--adresse", default="", help="Geräteadresse (optional)")
    ap.add_argument("--max-baud", type=int, default=115200)
    ap.add_argument("--sml", action="store_true", help="SML-Zähler passiv mitschneiden")
    ap.add_argument("--ultraheat", action="store_true", help="Wärmezähler Landis+Gyr UH50/T550")
    ap.add_argument("--dauer", type=int, default=10, help="Mitschnittdauer SML in s")
    args = ap.parse_args()

    if args.list or not args.port:
        for p in serial.tools.list_ports.comports():
            print(f"{p.device}: {p.description}")
        return

    if args.sml:
        sml_mitschnitt(args.port, args.dauer)
        return

    if args.ultraheat:
        kennung, text = ultraheat(args.port)
    else:
        kennung, text = mode_c(args.port, args.adresse, args.max_baud)
    werte = parse(text)

    stempel = datetime.now().strftime("%Y%m%d_%H%M%S")
    with open(f"auslesung_{stempel}.txt", "w", encoding="utf-8") as f:
        f.write(kennung + "\n" + text)
    with open(f"auslesung_{stempel}.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["Kennzahl", "Wert", "Einheit", "Zusatz"])
        w.writerows(werte)

    print(f"\n{len(werte)} Werte:")
    for k, v, e, z in werte:
        print(f"  {k:<14} {v:>18} {e:<6} {z}")
    print(f"\nGespeichert: auslesung_{stempel}.txt / .csv")


if __name__ == "__main__":
    main()
