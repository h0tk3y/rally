#!/usr/bin/env python3
"""Captures and annotates all the screenshots for both locales, in parallel on several emulators.

Every series of capture.py starts from clean data, so the series can run on different emulators at the same time.
Each emulator takes one job (a locale and a series) at a time; the phone series are spread over all the phones.

Usage: capture_all.py [--build] [--locales en,ru] [--phones SERIAL,...] [--tablets SERIAL,...] [--series NAME,...]
"""
import os, queue, subprocess, sys, threading, time

import capture

HERE = os.path.dirname(os.path.abspath(__file__))
LOGS = os.path.join(HERE, "tmp", "logs")


def opt(name, default):
    args = sys.argv[1:]
    return args[args.index(name) + 1] if name in args else default


def install(device, apk):
    subprocess.run(["adb", "-s", device, "install", "-r", apk], capture_output=True, check=True)


def worker(device, kind, jobs, failures):
    while True:
        try:
            locale, series = jobs.get_nowait()
        except queue.Empty:
            return
        env = dict(os.environ, RALLY_LOCALE=locale, ANDROID_SERIAL=device,
                   **({"RALLY_TABLET": device} if kind == "tablet" else {"RALLY_PHONE": device}))
        log = os.path.join(LOGS, f"{locale}-{series}.log")
        started = time.time()
        with open(log, "w") as out:
            code = subprocess.run([sys.executable, "-u", os.path.join(HERE, "capture.py"), "--series", series],
                                  env=env, stdout=out, stderr=subprocess.STDOUT).returncode
        print(f"{'ok  ' if code == 0 else 'FAIL'} {locale} {series:<10} on {device}  {time.time() - started:5.1f} s")
        if code != 0:
            failures.append(log)


def main():
    os.makedirs(LOGS, exist_ok=True)
    started = time.time()
    phones = opt("--phones", os.environ.get("RALLY_PHONES", "emulator-5560,emulator-5564,emulator-5566")).split(",")
    tablets = opt("--tablets", os.environ.get("RALLY_TABLETS", "emulator-5562")).split(",")
    locales = opt("--locales", "en,ru").split(",")
    names = opt("--series", ",".join(capture.SERIES)).split(",")

    if "--build" in sys.argv:
        capture.build_apk()
        apk = os.path.join(capture.ROOT, "android/build/outputs/apk/debug/android-debug.apk")
        threads = [threading.Thread(target=install, args=(d, apk)) for d in phones + tablets]
        [t.start() for t in threads]
        [t.join() for t in threads]
        print(f"built and installed in {time.time() - started:.0f} s")

    phone_jobs, tablet_jobs = queue.Queue(), queue.Queue()
    for locale in locales:
        for name in names:
            (tablet_jobs if name in capture.TABLET_SERIES else phone_jobs).put((locale, name))
    failures = []
    threads = [threading.Thread(target=worker, args=(d, "phone", phone_jobs, failures)) for d in phones] + \
              [threading.Thread(target=worker, args=(d, "tablet", tablet_jobs, failures)) for d in tablets]
    [t.start() for t in threads]
    [t.join() for t in threads]
    print(f"captured in {time.time() - started:.0f} s")
    if failures:
        sys.exit("failed, see:\n  " + "\n  ".join(failures))

    procs = [subprocess.Popen([sys.executable, os.path.join(HERE, "annotate.py")], env=dict(os.environ, RALLY_LOCALE=l),
                              stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True) for l in locales]
    for locale, p in zip(locales, procs):
        if p.wait() != 0:
            sys.exit(f"annotate.py failed for {locale}:\n{p.stderr.read()}")
    print(f"done in {time.time() - started:.0f} s")


if __name__ == "__main__":
    main()
