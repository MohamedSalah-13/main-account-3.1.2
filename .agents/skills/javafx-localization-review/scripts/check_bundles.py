#!/usr/bin/env python3
"""Validate the accounting application's three Java properties bundles."""

from __future__ import annotations

from pathlib import Path
import re
import sys


REPO_ROOT = Path(__file__).resolve().parents[4]
BUNDLE_DIR = REPO_ROOT / "controlsfx" / "src" / "main" / "resources" / "i18n"
BUNDLES = {
    "default": BUNDLE_DIR / "messages.properties",
    "arabic": BUNDLE_DIR / "messages_ar.properties",
    "english": BUNDLE_DIR / "messages_en.properties",
}
FORMAT_SPECIFIER = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?(?:[tT][a-zA-Z]|[a-zA-Z%])"
)


def logical_lines(text: str) -> list[str]:
    """Join Java-properties continuation lines without decoding their values."""
    result: list[str] = []
    pending = ""
    for physical in text.splitlines():
        line = pending + physical.lstrip() if pending else physical
        trailing = len(line) - len(line.rstrip("\\"))
        if trailing % 2 == 1:
            pending = line[:-1]
            continue
        result.append(line)
        pending = ""
    if pending:
        result.append(pending)
    return result


def split_property(line: str) -> tuple[str, str]:
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char in "=:":
            return line[:index].rstrip(), line[index + 1 :].lstrip()
        if char.isspace():
            rest = line[index:].lstrip()
            if rest.startswith(("=", ":")):
                rest = rest[1:].lstrip()
            return line[:index], rest
    return line, ""


def load_bundle(path: Path) -> tuple[dict[str, str], list[str]]:
    values: dict[str, str] = {}
    duplicates: list[str] = []
    for line in logical_lines(path.read_text(encoding="utf-8-sig")):
        stripped = line.lstrip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        key, value = split_property(stripped)
        if key in values:
            duplicates.append(key)
        values[key] = value
    return values, duplicates


def format_signature(value: str) -> list[str]:
    return [token for token in FORMAT_SPECIFIER.findall(value) if token != "%%"]


def main() -> int:
    errors: list[str] = []
    parsed: dict[str, dict[str, str]] = {}

    for name, path in BUNDLES.items():
        if not path.is_file():
            errors.append(f"Missing bundle: {path.relative_to(REPO_ROOT)}")
            continue
        values, duplicates = load_bundle(path)
        parsed[name] = values
        for key in duplicates:
            errors.append(f"Duplicate key in {path.name}: {key}")

    if len(parsed) == len(BUNDLES):
        all_keys = set().union(*(values.keys() for values in parsed.values()))
        for name, values in parsed.items():
            missing = sorted(all_keys - values.keys())
            if missing:
                errors.append(f"Missing from {BUNDLES[name].name}: {', '.join(missing)}")

        default = parsed["default"]
        arabic = parsed["arabic"]
        mismatched = sorted(
            key for key in default.keys() & arabic.keys() if default[key] != arabic[key]
        )
        if mismatched:
            errors.append(
                "Default Arabic values differ from messages_ar.properties: "
                + ", ".join(mismatched)
            )

        for key in sorted(all_keys):
            signatures = {
                name: format_signature(values[key])
                for name, values in parsed.items()
                if key in values
            }
            if len({tuple(signature) for signature in signatures.values()}) > 1:
                details = ", ".join(
                    f"{name}={signature}" for name, signature in signatures.items()
                )
                errors.append(f"Format placeholders differ for {key}: {details}")

    if errors:
        print("Localization bundle validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    key_count = len(parsed["default"])
    print(f"Localization bundles are synchronized ({key_count} keys in each bundle).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
