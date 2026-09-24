#!/usr/bin/env python3
"""Build OTA notes from changelog entries added since the preceding release tag."""

import argparse
import re
import subprocess
from pathlib import Path


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True, encoding="utf-8").strip()


def previous_tag(current_tag: str, prefix: str = "r") -> str | None:
    current_number = int(current_tag.removeprefix(prefix))
    candidates = git("tag", "--list", f"{prefix}[0-9]*").splitlines()
    candidates = sorted(
        (tag for tag in candidates if re.fullmatch(re.escape(prefix) + r"\d+", tag) and int(tag[len(prefix):]) < current_number),
        key=lambda tag: int(tag[len(prefix):]),
        reverse=True,
    )
    for tag in candidates:
        if subprocess.run(["git", "merge-base", "--is-ancestor", tag, "HEAD"], check=False).returncode == 0:
            return tag
    return None


def sections(markdown: str) -> list[tuple[str, list[str]]]:
    result: list[tuple[str, list[str]]] = []
    heading = ""
    entries: list[str] = []
    current: list[str] = []

    def flush_entry() -> None:
        if current:
            entries.append(" ".join(current))
            current.clear()

    def flush_section() -> None:
        flush_entry()
        if heading:
            result.append((heading, entries.copy()))
        entries.clear()

    for line in markdown.splitlines():
        if line.startswith("## "):
            flush_section()
            heading = line[3:]
        elif heading and line.startswith("- "):
            flush_entry()
            current.append(line[2:].strip())
        elif current and line.startswith("  "):
            current.append(line.strip())
        else:
            flush_entry()
    flush_section()
    return result


def release_notes(current: str, previous: str | None, tag: str, product: str = "Nyanime") -> str:
    current_sections = sections(current)
    old_entries = {entry for _, entries in sections(previous or "") for entry in entries}
    notes = [f"## Novità di {product} {tag}", ""]
    for heading, entries in current_sections:
        added = [entry for entry in entries if entry not in old_entries]
        if added:
            notes.extend((f"### {heading}", ""))
            notes.extend(f"- {entry}" for entry in added)
            notes.append("")
    return "\n".join(notes).rstrip() + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--changelog", type=Path, default=Path("CHANGELOG.md"))
    parser.add_argument("--tag-prefix", default="r")
    parser.add_argument("--product", default="Nyanime")
    args = parser.parse_args()

    if not re.fullmatch(re.escape(args.tag_prefix) + r"\d+", args.tag):
        raise SystemExit("Release tag does not match the requested channel.")
    current = args.changelog.read_text(encoding="utf-8")
    previous = previous_tag(args.tag, args.tag_prefix)
    old = git("show", f"{previous}:{args.changelog.as_posix()}") if previous else None
    notes = release_notes(current, old, args.tag, args.product)
    if not re.search(r"(?m)^- ", notes):
        raise SystemExit("No new changelog entries since the previous release; add release notes before publishing.")
    notes += f"\n[Changelog completo](https://github.com/{args.repository}/blob/{args.revision}/{args.changelog.as_posix()}).\n"
    args.output.write_text(notes, encoding="utf-8")


if __name__ == "__main__":
    main()
