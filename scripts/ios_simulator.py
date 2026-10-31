#!/usr/bin/env python3
"""Create a test-owned full iOS simulator; never erase or reuse a user's devices."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import uuid

STATE = Path(__file__).resolve().parents[1] / "build/ios-simulator.json"


def simctl(*args):
    return subprocess.check_output(["xcrun", "simctl", *args], text=True).strip()


def state():
    data = json.loads(STATE.read_text())
    uuid.UUID(data["udid"])
    if not data["name"].startswith("EncryptedDataStore-KMP-"):
        raise SystemExit("Refusing to act on a simulator without this test's ownership prefix")
    return data


def create():
    if STATE.exists():
        raise SystemExit(f"A simulator is already recorded in {STATE}; destroy that owned simulator first")
    devices = json.loads(simctl("list", "devices", "available", "-j"))["devices"]
    types = {t["name"]: t["identifier"] for t in json.loads(simctl("list", "devicetypes", "-j"))["devicetypes"]}
    candidates = []
    for runtime, entries in devices.items():
        version = re.fullmatch(r"com\.apple\.CoreSimulator\.SimRuntime\.iOS-(\d+)-(\d+)(?:-(\d+))?", runtime)
        if version:
            for device in entries:
                if device["name"].startswith("iPhone ") and device["name"] in types:
                    candidates.append((tuple(int(x or 0) for x in version.groups()), runtime, types[device["name"]]))
    if not candidates:
        raise SystemExit("No available iOS iPhone runtime; install an Xcode simulator runtime first")
    _, runtime, device_type = max(candidates)
    name = f"EncryptedDataStore-KMP-{uuid.uuid4().hex[:8]}"
    udid = simctl("create", name, device_type, runtime)
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps({"udid": udid, "name": name}) + "\n")
    print(f"Created {name}: {udid}", flush=True)
    simctl("boot", udid)
    subprocess.run(["xcrun", "simctl", "bootstatus", udid, "-b"], check=True, stdout=subprocess.DEVNULL, timeout=180)
    print("Simulator ready", flush=True)


def destroy():
    if not STATE.exists():
        print("No test-owned simulator recorded")
        return
    owned = state()
    entries = json.loads(simctl("list", "devices", "-j"))["devices"]
    actual = next((d for group in entries.values() for d in group if d["udid"] == owned["udid"]), None)
    if actual is not None:
        if actual["name"] != owned["name"]:
            raise SystemExit("Recorded device ownership no longer matches; leaving it untouched")
        if actual["state"] != "Shutdown":
            simctl("shutdown", owned["udid"])
        simctl("delete", owned["udid"])
    STATE.unlink()
    print(f"Deleted only test-owned simulator {owned['udid']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("create", "id", "destroy"))
    action = parser.parse_args().action
    if action == "create":
        create()
    elif action == "destroy":
        destroy()
    else:
        print(state()["udid"])
