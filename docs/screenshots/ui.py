#!/usr/bin/env python3
"""Tiny adb UI driver: ui.py dump | tap <text> [n] | find <text> | shot <name> | key <code> | text <s> | xy <x> <y>"""
import re, subprocess, sys, time, os

HERE = os.path.dirname(os.path.abspath(__file__))
# The app's language for the screenshots: en (default) or ru. Captures go to raw/<locale>.
LOCALE = os.environ.get("RALLY_LOCALE", "en")
OUT = os.path.join(HERE, "raw", LOCALE)
RES = os.path.join(HERE, "..", "..", "android", "src", "main", "res")
# Texts that come from Android itself, not from the app's resources.
SYSTEM_STRINGS = {"en": {"cancel": "Cancel"}, "ru": {"cancel": "Отмена"}}


def _load_strings(locale):
    def parse(path):
        out = {}
        for name, value in re.findall(r'<string name="(\w+)"[^>]*>(.*?)</string>', open(path).read(), re.S):
            if len(value) >= 2 and value[0] == value[-1] == '"':
                value = value[1:-1]
            out[name] = value.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
        return out
    strings = parse(os.path.join(RES, "values", "strings.xml"))
    if locale != "en":
        strings.update(parse(os.path.join(RES, f"values-{locale}", "strings.xml")))
    strings.update(SYSTEM_STRINGS[locale])
    return strings


STRINGS = _load_strings(LOCALE)


def S(name, *args):
    """An app string in the current locale, with Android's %1$s placeholders filled in."""
    value = STRINGS[name]
    for i, arg in enumerate(args, 1):
        value = value.replace(f"%{i}$s", str(arg)).replace(f"%{i}$d", str(arg))
    return value
# The dedicated screenshot emulator (see README.md); override with ANDROID_SERIAL.
os.environ.setdefault("ANDROID_SERIAL", "emulator-5560")


def adb(*a, binary=False):
    r = subprocess.run(["adb", *a], capture_output=True)
    return r.stdout if binary else r.stdout.decode(errors="replace")


def nodes():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/ui.xml")
    xml = adb("shell", "cat", "/data/local/tmp/ui.xml")
    res = []
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        g = lambda k: (re.search(k + r'="([^"]*)"', n) or [None, ""])[1]
        b = list(map(int, re.findall(r'\d+', g("bounds"))))
        res.append(dict(text=g("text"), desc=g("content-desc"), cls=g("class").split(".")[-1],
                        click=g("clickable") == "true", b=b))
    return res


def match(text):
    ns = nodes()
    exact = [n for n in ns if text in (n["text"], n["desc"])]
    return exact or [n for n in ns if text in n["text"] or text in n["desc"]]


def tap_node(n):
    x0, y0, x1, y1 = n["b"]
    adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))


def main():
    cmd, *args = sys.argv[1:]
    if cmd == "dump":
        for n in nodes():
            if n["text"] or n["desc"] or n["click"]:
                print(n["b"], n["cls"], repr(n["text"]), repr(n["desc"]), "C" if n["click"] else "")
    elif cmd == "find":
        for n in match(args[0]):
            print(n["b"], repr(n["text"]), repr(n["desc"]))
    elif cmd == "tap":
        ms = match(args[0])
        idx = int(args[1]) if len(args) > 1 else 0
        if not ms:
            sys.exit(f"not found: {args[0]}")
        tap_node(ms[idx]); time.sleep(0.8)
    elif cmd == "xy":
        adb("shell", "input", "tap", args[0], args[1]); time.sleep(0.6)
    elif cmd == "key":
        adb("shell", "input", "keyevent", args[0]); time.sleep(0.5)
    elif cmd == "text":
        adb("shell", "input", "text", args[0].replace(" ", "%s")); time.sleep(0.4)
    elif cmd == "shot":
        os.makedirs(OUT, exist_ok=True)
        p = os.path.join(OUT, args[0] + ".png")
        open(p, "wb").write(adb("exec-out", "screencap", "-p", binary=True))
        print(p)


if __name__ == "__main__":
    main()
