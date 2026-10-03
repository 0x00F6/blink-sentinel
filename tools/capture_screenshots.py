#!/usr/bin/env python3
"""Export actual app views from an empty emulator; never use a personal device."""

import argparse
import os
from pathlib import Path
import shutil
import struct
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit emulator serial from adb devices")
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    adb = shutil.which("adb") or (str(Path(sdk) / "platform-tools" / "adb") if sdk else None)
    if not adb:
        parser.error("Put adb on PATH or set ANDROID_HOME")
    prefix = [adb, "-s", args.serial]

    def execute(*arguments):
        return subprocess.run(prefix + list(arguments), check=True, capture_output=True).stdout

    if execute("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        parser.error("Screenshot generation is permitted only on an emulator")
    for apk in (
        project / "app/build/outputs/apk/debug/app-debug.apk",
        project / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    ):
        if not apk.is_file():
            parser.error("First run ./gradlew assembleDebug assembleDebugAndroidTest")
        print(execute("install", "-r", str(apk)).decode().strip())

    result = execute(
        "shell", "am", "instrument", "-w", "-r",
        "dev.homesentinel.test/dev.homesentinel.docs.CaptureScreenshotsInstrumentation",
    ).decode()
    print(result.strip())
    if "Captured 14 real application views with DEMO fixtures" not in result or "INSTRUMENTATION_CODE: -1" not in result:
        raise RuntimeError("Capture failed; existing documentation images were not replaced")

    destination = project / "docs/screenshots"
    captures = {}
    for theme in ("light", "dark"):
        for screen in ("home", "network", "blink", "blink-2fa", "blink-systems", "event-log", "error-details"):
            name = f"{screen}-{theme}.png"
            data = execute("exec-out", "run-as", "dev.homesentinel", "cat", f"files/documentation-screenshots/{name}")
            if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                raise RuntimeError(f"Invalid screenshot: {name}")
            width, height = struct.unpack(">II", data[16:24])
            if width < 320 or height < 600:
                raise RuntimeError(f"Unexpected screenshot dimensions: {name}")
            captures[name] = data
    destination.mkdir(parents=True, exist_ok=True)
    icon = execute("exec-out", "run-as", "dev.homesentinel", "cat", "files/documentation-screenshots/launcher-icon.png")
    if not icon.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Invalid packaged launcher preview")
    for name, data in captures.items():
        (destination / name).write_bytes(data)
        print(f"Saved docs/screenshots/{name}")
    (project / "artwork/launcher-preview.png").write_bytes(icon)
    print("Saved artwork/launcher-preview.png")


if __name__ == "__main__":
    main()
