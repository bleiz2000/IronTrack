#!/usr/bin/env python3
"""Conservative first-pass parser for Obsidian workout notes.

Never edits the source. Emits JSONL records and a review queue. Ambiguous
free-form numeric lines are retained verbatim rather than guessed into sets.
Only Python's standard library is required; the script performs no networking.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

DATE_PATTERNS = (
    re.compile(r"^\s*(?:#{1,6}\s*)?(?:🗓️?\s*)?(\d{1,2})[./](\d{1,2})[./](\d{2,4})(?:\s+.*)?$"),
    re.compile(r"^\s*#{1,6}\s*(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?(?:\s+.*)?$"),
    re.compile(r"^\s*(\d{1,2})[./](\d{1,2})(?:\s+.*)?$"),
)
# Deliberately conservative: a set needs an explicit separator between load and reps.
SET_RE = re.compile(
    r"(?<![\w.])(?P<weight>\d+(?:[.,]\d+)?)\s*(?P<unit>кг|kg|lbs?|фунт(?:а|ов)?)?\s*(?:×|х|x|[-–])\s*(?P<reps>\d+)(?!\d)",
    re.IGNORECASE,
)
EXPLICIT_REPS_RE = re.compile(r"(?<!\d)(\d+)\s*(?:повт(?:ор(?:а|ов)?)?\.?|reps?)\b", re.IGNORECASE)

ALIASES = {
    "жим лежа": "Жим лёжа",
    "жым лёжа": "Жим лёжа",
    "жым лежа": "Жим лёжа",
    "подяг": "Подтягивания",
    "подтягивания": "Подтягивания",
    "тяга верхнего блока": "Тяга верхнего блока",
    "тяга военного блока": "Тяга верхнего блока",
    "тяга штанги к поясу": "Тяга штанги к поясу",
    "тяга штанги по поясу стоя": "Тяга штанги к поясу",
    "армейский жим": "Армейский жим",
    "армейская жим": "Армейский жим",
    "тренажёр скотта": "Скамья Скотта",
    "тренажер скотта": "Скамья Скотта",
    "скамья скотта": "Скамья Скотта",
}


def clean_name(value: str) -> str:
    value = re.sub(r"<br\s*/?>", " ", value, flags=re.IGNORECASE)
    value = re.sub(r"[*_`#]", "", value)
    value = re.sub(r"\s+", " ", value).strip(" |:-—–")
    return value


def parse_date(line: str) -> str | None:
    for pattern in DATE_PATTERNS:
        match = pattern.match(line)
        if not match:
            continue
        groups = match.groups()
        day, month = int(groups[0]), int(groups[1])
        year = groups[2] if len(groups) > 2 else None
        if day > 31 or month > 12:
            continue
        if year:
            year_int = int(year)
            if year_int < 100:
                year_int += 2000
            return f"{year_int:04d}-{month:02d}-{day:02d}"
        # Do not infer a year from sequence or machine date.
        return f"????-{month:02d}-{day:02d}"
    return None


def parse_gym(text: str) -> str | None:
    """Recognize only explicit gym names; never infer a gym from equipment."""
    match = re.search(r"\b(динамо|ереван|зайтун)\b", text, re.IGNORECASE)
    if match:
        return match.group(1).capitalize()
    match = re.search(r"зал\s+([^()|,;]+)", text, re.IGNORECASE)
    if match:
        return clean_name(match.group(1)).capitalize()
    return None


def parse_sets(text: str) -> list[dict[str, Any]]:
    sets = []
    for match in SET_RE.finditer(text):
        unit = (match.group("unit") or "").lower()
        if unit.startswith("lb") or unit.startswith("фунт"):
            normalized_unit = "lb"
        elif unit:
            normalized_unit = "kg"
        else:
            normalized_unit = None
        sets.append({
            "weight": float(match.group("weight").replace(",", ".")),
            "unit": normalized_unit,
            "reps": int(match.group("reps")),
            "source_text": match.group(0).strip(),
        })
    return sets


def normalize_exercise(name: str) -> tuple[str, str]:
    key = re.sub(r"\s+", " ", name).strip(" .,:;-").lower().replace("ё", "е")
    canonical = ALIASES.get(key)
    if canonical:
        return canonical, "alias"
    return name, "unreviewed"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", type=Path,
                        default=Path(__file__).resolve().parents[1] / "data/raw/Прогрессия.md")
    parser.add_argument("--out", type=Path,
                        default=Path(__file__).resolve().parents[1] / "data/processed")
    args = parser.parse_args()
    if not args.source.is_file():
        parser.error(f"source file does not exist: {args.source}")

    text = args.source.read_text(encoding="utf-8-sig")
    records: list[dict[str, Any]] = []
    review: list[dict[str, Any]] = []
    current_date: str | None = None
    current_exercise: str | None = None
    current_gym: str | None = None

    for line_no, raw in enumerate(text.splitlines(), 1):
        stripped = raw.strip()
        if not stripped or stripped in {"---", "___", "——", "–––", "—-"}:
            continue
        found_date = parse_date(stripped)
        if found_date:
            current_date = found_date
            current_exercise = None
            current_gym = parse_gym(stripped)
            continue

        # Markdown table rows: first cell is exercise, second cell contains sets.
        if stripped.startswith("|") and stripped.count("|") >= 2:
            cells = [cell.strip() for cell in stripped.strip("|").split("|")]
            if len(cells) >= 2 and not re.fullmatch(r"[-:\s]+", "".join(cells[:2])):
                name = clean_name(cells[0])
                if name and name.lower() not in {"упражнение", "подходы и повторения"}:
                    if re.search(r"[а-яa-z]", name, re.IGNORECASE):
                        current_exercise = name
                        data = " ".join(cells[1:])
                        sets = parse_sets(data)
                        canonical, status = normalize_exercise(name)
                        record = {
                            "date": current_date, "exercise": canonical,
                            "exercise_source": name, "normalization": status,
                            "sets": sets, "gym": current_gym, "equipment": None,
                            "source_file": str(args.source), "source_line": line_no,
                            "raw_text": raw,
                        }
                        records.append(record)
                        if current_date is None or current_date.startswith("????") or not sets or status == "unreviewed":
                            review.append({**record, "review_reason": "date, sets, or exercise alias needs confirmation"})
            continue

        # Exercise heading is retained as context for following numeric lines.
        # It is not treated as a real exercise if it contains explicit set syntax.
        sets = parse_sets(stripped)
        if re.search(r"[а-яa-z]", stripped, re.IGNORECASE) and not sets:
            # Preserve explicit gym context for subsequent records.
            mentioned_gym = parse_gym(stripped)
            if mentioned_gym and (stripped.lower().startswith("зал ") or len(stripped.split()) <= 4):
                current_gym = mentioned_gym
                current_exercise = None
                continue
            name = clean_name(re.sub(r"\([^)]*\)", "", stripped))
            if name and not name.startswith(("#", "*")):
                current_exercise = name
            continue

        # Number-only/raw data line: parse only explicit weight×reps expressions.
        # Slash-separated rep-only records (e.g. pull-ups) are sent to review.
        if re.search(r"\d", stripped):
            context = current_exercise
            record = {
                "date": current_date,
                "exercise": normalize_exercise(context)[0] if context else None,
                "exercise_source": context,
                "normalization": normalize_exercise(context)[1] if context else "missing_context",
                "sets": sets,
                "gym": current_gym, "equipment": None,
                "source_file": str(args.source), "source_line": line_no,
                "raw_text": raw,
            }
            records.append(record)
            if not sets or not context or current_date is None or current_date.startswith("????"):
                record["review_reason"] = "ambiguous numeric format or missing date/exercise context"
                review.append(record.copy())

    args.out.mkdir(parents=True, exist_ok=True)
    records_path = args.out / "records.jsonl"
    review_path = args.out / "review.jsonl"
    records_path.write_text("".join(json.dumps(item, ensure_ascii=False) + "\n" for item in records), encoding="utf-8")
    review_path.write_text("".join(json.dumps(item, ensure_ascii=False) + "\n" for item in review), encoding="utf-8")
    print(f"Read {len(text.splitlines())} lines from {args.source}")
    print(f"Wrote {len(records)} candidate records to {records_path}")
    print(f"Queued {len(review)} records for manual review in {review_path}")
    print("No source text was modified; gym/equipment fields remain null unless explicitly modeled.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
