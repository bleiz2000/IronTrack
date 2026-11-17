#!/usr/bin/env python3
"""Convert the Obsidian workout note to a readable, loss-aware JSON + Markdown.

No network or third-party packages. The source is never changed. Anything the
parser cannot safely interpret stays in notes/source_text with its line number
instead of being silently invented. Missing years are inferred only from the
diary's chronological order and are marked with date_status="inferred_year".
"""
from __future__ import annotations

import argparse
import json
import re
from datetime import date
from pathlib import Path
from typing import Any

HEADING_DATE = re.compile(
    r"^\s*#{1,6}\s*(?:🗓️?\s*)?(?P<a>\d{1,2})[./](?P<b>\d{1,2})"
    r"(?:[./](?P<year>\d{2,4}))?\.?(?P<tail>.*)$"
)
BARE_DATE = re.compile(
    r"^\s*(?P<a>\d{1,2})[./](?P<b>\d{1,2})(?:[./](?P<year>\d{2,4}))?\.?\s*"
    r"(?P<tail>динамо|ереван|зайтун|зал\b.*)?\s*$",
    re.IGNORECASE,
)
SET_PAIR = re.compile(
    r"(?<![\w.])(?P<weight>\d+(?:[.,]\d+)?)\s*(?P<unit>кг|kg|lbs?|лбс?|фунт(?:а|ов)?)?\s*"
    r"(?:×|х|x|[-–])\s*(?P<reps>\d+)(?!\d)",
    re.IGNORECASE,
)

ALIASES = {
    "жим лежа": "Жим лёжа", "жым лёжа": "Жим лёжа", "жым лежа": "Жим лёжа",
    "жим штанги лёжа": "Жим лёжа", "жим штанги лежа": "Жим лёжа",
    "подяг": "Подтягивания", "подтягивания": "Подтягивания",
    "тяга военного блока": "Тяга верхнего блока",
    "тяга штанги по поясу стоя": "Тяга штанги к поясу",
    "армейская жим": "Армейский жим",
    "тренажёр скотта": "Скамья Скотта", "тренажер скотта": "Скамья Скотта",
    "тренажёр скота": "Скамья Скотта", "тренажер скота": "Скамья Скотта",
    "бруся": "Отжимания на брусьях", "брус": "Отжимания на брусьях", "брусы": "Отжимания на брусьях",
    "малаток": "Молотки", "молоток": "Молотки",
    "тяга к спине смит": "Тяга штанги к поясу в Смите",
    # High-confidence spelling/OCR variants seen in the diary.
    "жим верхнего блока": "Тяга верхнего блока",
    "крыялья": "Крылья",
    "хамер плечи": "Хаммер плечи", "хаммер плечи": "Хаммер плечи",
    "жыс лежа": "Жим лёжа", "дым лёжа": "Жим лёжа", "дым лежа": "Жим лёжа",
    "трицепс тяга": "Трицепс тяга", "тяга трицепс": "Трицепс тяга",
    "трицепс тяга вниз": "Трицепс тяга вниз",
    "бицес тяга": "Бицепс тяга",
}
GYMS = ("Динамо", "Ереван", "Зайтун")
DATE_STATE: dict[str, Any] = {"last": None, "year": None}


def clean(value: str) -> str:
    value = re.sub(r"<br\s*/?>", " ; ", value, flags=re.I)
    value = re.sub(r"\[\[([^]|]+)(?:\|([^]]+))?\]\]", lambda m: m.group(2) or m.group(1), value)
    value = re.sub(r"[*_`#]", "", value)
    value = re.sub(r"\s+", " ", value).strip(" |:-—–")
    return value


def canonical_exercise(name: str) -> str:
    cleaned = clean(name)
    key = re.sub(r"\s+", " ", cleaned).lower().replace("ё", "е")
    normalized_aliases = {k.replace("ё", "е"): v for k, v in ALIASES.items()}
    return normalized_aliases.get(key, cleaned)


def _date(y: int, month: int, day: int) -> date | None:
    try:
        return date(y, month, day)
    except ValueError:
        return None


def session_date(line: str) -> tuple[str | None, str, str, int | None, int | None] | None:
    """Return (ISO date, label, status, day, month) or None.

    Day/month order: Russian diary style is DD.MM. If one component is > 12 it
    disambiguates. A slash date with both <= 12 is resolved by chronology and
    marked inferred_day_month_order.
    """
    match = HEADING_DATE.match(line) or BARE_DATE.match(line)
    if not match:
        return None
    a, b = int(match.group("a")), int(match.group("b"))
    if not (1 <= a <= 31 and 1 <= b <= 31):
        return None
    year_text = match.group("year")
    tail = clean(match.group("tail") or "")
    parsed: date | None = None
    status = "missing_year"

    if b > 12 and a <= 12:
        month, day = a, b
    else:
        day, month = a, b

    if year_text:
        y = int(year_text)
        y = y + 2000 if y < 100 else y
        candidates = []
        if b > 12 and a <= 12:
            candidates = [_date(y, a, b)]
        elif a > 12:
            candidates = [_date(y, b, a)]
        else:
            candidates = [_date(y, b, a), _date(y, a, b)]  # DD.MM first
            if "/" not in line.split(year_text)[0]:
                candidates = candidates[:1]
        candidates = [c for c in candidates if c]
        if candidates:
            last = DATE_STATE["last"]
            if len(candidates) > 1 and last:
                parsed = min(candidates, key=lambda c: abs((c - last).days))
                status = "inferred_day_month_order"
            else:
                parsed = candidates[0]
                status = "parsed"
    else:
        y = DATE_STATE["year"] or 2025
        parsed = _date(y, month, day)
        if parsed:
            last = DATE_STATE["last"]
            if last and parsed < last and (last - parsed).days > 300:
                parsed = _date(y + 1, month, day)
            status = "inferred_year"

    if parsed:
        DATE_STATE["last"] = parsed
        DATE_STATE["year"] = parsed.year
    label = f"{day:02d}.{month:02d}" + (f".{year_text}" if year_text else "")
    if tail:
        label += f" {tail}"
    return (parsed.isoformat() if parsed else None), label, status, day, month


def gym_from(text: str) -> str | None:
    lowered = text.lower()
    for name in GYMS:
        if name.lower() in lowered:
            return name
    match = re.search(r"зал\s+([^(),;]+)", text, re.I)
    return clean(match.group(1)).title() if match else None


def _unit_value(raw: str | None) -> str | None:
    unit = (raw or "").lower()
    if unit.startswith("lb") or unit.startswith("фунт") or unit.startswith("лб"):
        return "lb"
    return "kg" if unit else None


def _set(weight: float | None, unit: str | None, reps: int) -> dict[str, Any]:
    return {"weight": weight, "unit": unit, "reps": reps, "rpe": None}


def parse_sets(text: str, exercise_name: str) -> tuple[list[dict[str, Any]], str]:
    matches = list(SET_PAIR.finditer(text))
    if matches:
        sets = []
        for m in matches:
            sets.append(_set(float(m.group("weight").replace(",", ".")), _unit_value(m.group("unit")),
                             int(m.group("reps"))))
        return sets, "parsed_explicit_pairs"

    stripped = re.sub(r"\([^)]*\)", " ", text.lower())
    tokens = [(float(m.group(1).replace(",", ".")), _unit_value(m.group(2)))
              for m in re.finditer(r"(\d+(?:[.,]\d+)?)\s*(кг|kg|lbs?|лбс?|фунт(?:а|ов)?)?", stripped)]
    nums = [n for n, _ in tokens]
    has_separator = bool(re.search(r"[/,]", stripped))
    residual = re.sub(r"[\d.,/\sхx×+\-–]", "", re.sub(r"(?:кг|kg|lbs?|лбс?|фунт(?:а|ов)?)", " ", stripped))
    exercise_key = exercise_name.lower().replace("ё", "е")
    is_pullup = "подтяг" in exercise_key or "подяг" in exercise_key

    if is_pullup:
        reps = [int(n) for n in nums if abs(n - int(n)) < 1e-9 and 1 <= n <= 100]
        if reps:
            return [_set(None, None, r) for r in reps], "parsed_bodyweight_reps"

    if residual:
        return [], "unparsed"

    # Explicit unit markers can split a line into several weighted groups:
    # "40 8 8 35 кг 9" -> 40×8, 40×8, 35×9.
    unit_positions = [i for i, (_, unit) in enumerate(tokens) if unit is not None]
    if unit_positions and unit_positions[0] > 0:
        groups: list[list[tuple[float, str | None]]] = []
        for i, token in enumerate(tokens):
            if i == 0 or (i in unit_positions):
                groups.append([token])
            else:
                groups[-1].append(token)
        if all(len(g) >= 2 for g in groups):
            sets: list[dict[str, Any]] = []
            for group in groups:
                weight, unit = group[0]
                reps = [int(n) for n, _ in group[1:]]
                if weight < 1 or weight > 500 or not all(1 <= r <= 30 for r in reps):
                    sets = []
                    break
                sets.extend(_set(weight, unit, r) for r in reps)
            if sets:
                return sets, "parsed_weight_groups"

    # One leading load, remaining integers as repetitions.
    if len(nums) >= 2:
        weight = nums[0]
        rep_values = [int(n) for n in nums[1:]]
        if 1 <= weight <= 500 and all(1 <= r <= 30 for r in rep_values):
            unit = tokens[0][1] or ("lb" if re.search(r"\b(?:lbs?|лбс?|фунт)", text, re.I) else
                                    ("kg" if re.search(r"\b(?:кг|kg)", text, re.I) else None))
            status = "parsed_single_load_rep_list" if has_separator else "parsed_implicit_load_rep_list"
            return [_set(weight, unit, r) for r in rep_values], status

    # Alternating load/reps pairs such as "60 8 80 1 75 2".
    pairs: list[dict[str, Any]] = []
    index = 0
    while index + 1 < len(nums):
        weight, reps = nums[index], int(nums[index + 1])
        if not (1 <= weight <= 500 and 1 <= reps <= 30):
            pairs = []
            break
        pairs.append(_set(weight, tokens[index][1], reps))
        index += 2
    if pairs:
        return pairs, "parsed_alternating_pairs"
    return [], "unparsed"


def new_session(date_iso: str | None, label: str, confidence: str, source_line: int, location: str | None) -> dict[str, Any]:
    return {"date": date_iso, "date_text": label, "date_status": confidence, "location": location,
            "exercises": [], "notes": [], "source_lines": [source_line]}


def line_exercise_name(text: str) -> str:
    no_markdown = clean(text)
    return clean(re.sub(r"\([^)]*\)", "", no_markdown)) or no_markdown


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("source", nargs="?", type=Path, default=root / "data/raw/Прогрессия.md")
    cli.add_argument("--out", type=Path, default=root / "data/processed/workout-history.json")
    cli.add_argument("--markdown", type=Path, default=root / "data/processed/workout-history.md")
    args = cli.parse_args()
    if not args.source.is_file():
        cli.error(f"source file does not exist: {args.source}")

    DATE_STATE["last"] = None
    DATE_STATE["year"] = None
    lines = args.source.read_text(encoding="utf-8-sig").splitlines()
    sessions: list[dict[str, Any]] = []
    current: dict[str, Any] | None = None
    current_exercise: dict[str, Any] | None = None
    pending_weight: float | None = None
    pending_unit: str | None = None

    def ensure_session(line_no: int) -> dict[str, Any]:
        nonlocal current
        if current is None:
            current = new_session(None, "дата не указана", "missing_date", line_no, None)
            sessions.append(current)
        return current

    for line_no, raw in enumerate(lines, 1):
        text = raw.strip()
        if not text or text in {"---", "___", "——", "–––", "—-"}:
            continue
        parsed_date = session_date(text)
        if parsed_date:
            date_iso, label, confidence, _, _ = parsed_date
            current = new_session(date_iso, label, confidence, line_no, gym_from(text))
            sessions.append(current)
            current_exercise = None
            pending_weight, pending_unit = None, None
            continue

        if text.startswith("|") and text.count("|") >= 2:
            cells = [c.strip() for c in text.strip("|").split("|")]
            if len(cells) < 2 or re.fullmatch(r"[-:\s]+", "".join(cells[:2])):
                continue
            name = clean(cells[0])
            if not name or name.lower() in {"упражнение", "подходы и повторения"} or not re.search(r"[а-яa-z]", name, re.I):
                continue
            session = ensure_session(line_no)
            data = " ".join(cells[1:])
            sets, status = parse_sets(data, name)
            session["exercises"].append({
                "name": canonical_exercise(name), "name_as_written": name, "sets": sets,
                "status": status if sets else "needs_review", "source_lines": [line_no], "source_text": raw,
            })
            if not sets:
                session["notes"].append({"line": line_no, "text": raw, "status": "unparsed_exercise_row"})
            current_exercise = session["exercises"][-1]
            continue

        location = gym_from(text)
        if location and not re.search(r"\d", text) and (text.lower().startswith("зал") or len(text.split()) <= 4):
            session = ensure_session(line_no)
            session["location"] = location
            current_exercise = None
            pending_weight, pending_unit = None, None
            continue

        # "30 лбс" or "40" on its own sets the load for the following reps.
        bare_weight = re.fullmatch(r"(\d+(?:[.,]\d+)?)\s*(кг|kg|lbs?|лбс?|фунт(?:а|ов)?)?", text, re.I)
        if bare_weight:
            value = float(bare_weight.group(1).replace(",", "."))
            unit = _unit_value(bare_weight.group(2))
            if unit is not None or value >= 20:
                pending_weight, pending_unit = value, unit
                continue

        has_letters = bool(re.search(r"[а-яa-z]", text, re.I))
        inline_sets, _ = parse_sets(text, "")
        if has_letters and not inline_sets:
            if location and current is not None and re.search(r"\b(?:зал|динам|ереван|зайтун)\b", text, re.I):
                current["location"] = location
                current["notes"].append({"line": line_no, "text": raw, "status": "location_context"})
                current_exercise = None
                continue
            name = line_exercise_name(text)
            if not name:
                continue
            session = ensure_session(line_no)
            current_exercise = {"name": canonical_exercise(name), "name_as_written": name,
                                "sets": [], "status": "pending", "source_lines": [line_no], "source_text": raw}
            session["exercises"].append(current_exercise)
            pending_weight, pending_unit = None, None
            continue

        if re.search(r"\d", text):
            session = ensure_session(line_no)
            if current_exercise:
                if pending_weight is not None and not SET_PAIR.search(text) and \
                        not re.search(r"\b(?:кг|kg|lbs?|лбс?|фунт)", text, re.I):
                    rep_values = [int(n) for n in re.findall(r"\d+", text) if 1 <= int(n) <= 30]
                    sets = [_set(pending_weight, pending_unit, r) for r in rep_values]
                    status = "parsed_pending_weight_reps" if sets else "unparsed"
                else:
                    sets, status = parse_sets(text, current_exercise["name"])
                pending_weight, pending_unit = None, None
                current_exercise["source_lines"].append(line_no)
                current_exercise["source_text"] += "\n" + raw
                if sets:
                    current_exercise["sets"].extend(sets)
                    current_exercise["status"] = status
                else:
                    current_exercise["status"] = "needs_review"
                    session["notes"].append({"line": line_no, "text": raw, "exercise": current_exercise["name"],
                                             "status": "unparsed_set_line"})
            else:
                session["notes"].append({"line": line_no, "text": raw, "status": "unassigned_numeric_text"})
            continue

        if current is not None:
            current["notes"].append({"line": line_no, "text": raw, "status": "source_note"})

    for session in sessions:
        session["exercises"] = [e for e in session["exercises"] if e["sets"] or e["status"] != "pending"]
        session["source_lines"] = sorted(set(session["source_lines"] +
            [ln for e in session["exercises"] for ln in e["source_lines"]]))

    document = {
        "format": "workout-tracker-data",
        "schema_version": 1,
        "description": "Тренировочные записи. Неоднозначные исходные строки сохранены в notes/source_text и требуют проверки.",
        "units": {"weight": "kg unless a set explicitly says lb", "date": "ISO-8601 local date or null when unknown"},
        "source": {"file_name": args.source.name, "encoding": "UTF-8"},
        "workouts": sessions,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.markdown.write_text(render_markdown(document), encoding="utf-8")

    exercise_count = sum(len(w["exercises"]) for w in sessions)
    set_count = sum(len(e["sets"]) for w in sessions for e in w["exercises"])
    review_count = sum(e["status"] in {"needs_review", "unparsed"} for w in sessions for e in w["exercises"]) + \
        sum(len(w["notes"]) for w in sessions)
    print(f"Сессий: {len(sessions)}; упражнений: {exercise_count}; подходов распознано: {set_count}; фрагментов для проверки: {review_count}")
    print(f"JSON: {args.out}")
    print(f"Читаемый текст: {args.markdown}")
    return 0


def render_markdown(document: dict[str, Any]) -> str:
    out = ["# Тренировочный дневник — читаемая версия", "",
           "Сгенерировано из исходной заметки. Сомнительные фрагменты требуют проверки.", ""]
    for workout in document["workouts"]:
        if not workout["exercises"] and not workout["notes"]:
            continue
        title = workout["date"] or workout["date_text"]
        location = f" · {workout['location']}" if workout["location"] else ""
        out.append(f"## {title}{location}")
        for exercise in workout["exercises"]:
            if not exercise["sets"]:
                out.append(f"- **{exercise['name']}** — данные не распознаны, проверьте исходник")
                continue
            parts = []
            for s in exercise["sets"]:
                weight = "—" if s["weight"] is None else f"{s['weight']:g}"
                unit = " lb" if s["unit"] == "lb" else ""
                parts.append(f"{weight}{unit}×{s['reps']}" if s["weight"] is not None else f"{s['reps']} повт.")
            out.append(f"- **{exercise['name']}** — {', '.join(parts)}")
        out.append("")
    return "\n".join(out).rstrip() + "\n"


if __name__ == "__main__":
    raise SystemExit(main())
