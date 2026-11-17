#!/usr/bin/env python3
"""Turn data/processed/workout-history.json into an app-importable backup.

The Android app stores loads in kilograms and imports the versioned
`workout-tracker-backup` format. This script converts pounds to kg, assigns
stable integer IDs, and writes only sessions that have a resolved date.
Undated or unreviewed fragments stay in workout-history.json for manual work.
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

LB_TO_KG = 0.45359237
DEFAULT_INCREMENT = {
    "Приседания": 5.0,
    "Становая тяга": 5.0,
    "Жим лёжа": 2.5,
    "Тяга верхнего блока": 2.5,
    "Армейский жим": 1.25,
}


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("source", nargs="?", type=Path, default=root / "data/processed/workout-history.json")
    cli.add_argument("--out", type=Path, default=root / "data/processed/workout-backup.json")
    args = cli.parse_args()
    if not args.source.is_file():
        cli.error(f"source file does not exist: {args.source}")

    history = json.loads(args.source.read_text(encoding="utf-8"))
    if history.get("format") != "workout-tracker-data":
        cli.error("unexpected input format; expected workout-tracker-data")

    locations: dict[str, int] = {}
    exercises: dict[str, int] = {}
    exercise_bodyweight: dict[str, bool] = {}
    workouts: list[dict[str, Any]] = []
    sets: list[dict[str, Any]] = []
    set_id = 0
    skipped_sessions = 0
    skipped_sets = 0

    def location_id(name: str | None) -> int | None:
        if not name:
            return None
        if name not in locations:
            locations[name] = len(locations) + 1
        return locations[name]

    def ensure_exercise(name: str, bodyweight: bool) -> int:
        if name not in exercises:
            exercises[name] = len(exercises) + 1
            exercise_bodyweight[name] = bodyweight
        else:
            exercise_bodyweight[name] = exercise_bodyweight[name] and bodyweight
        return exercises[name]

    for workout in history.get("workouts", []):
        if not workout.get("date"):
            skipped_sessions += 1
            continue
        parsed_exercises = [e for e in workout.get("exercises", []) if e.get("sets")]
        if not parsed_exercises:
            continue
        workout_id = len(workouts) + 1
        workouts.append({
            "id": workout_id,
            "date": workout["date"],
            "location_id": location_id(workout.get("location")),
            "readiness_score": None,
            "note": f"Импорт из заметок Obsidian; исходная дата: {workout.get('date_text', '')}".strip(),
        })
        set_numbers: dict[tuple[int, int], int] = defaultdict(int)
        for exercise in parsed_exercises:
            all_bodyweight = all(s.get("weight") is None for s in exercise["sets"])
            ex_id = ensure_exercise(exercise["name"], all_bodyweight)
            for s in exercise["sets"]:
                weight = s.get("weight")
                if weight is None:
                    weight_kg = 0.0
                elif s.get("unit") == "lb":
                    weight_kg = round(weight * LB_TO_KG, 2)
                else:
                    weight_kg = float(weight)
                if weight_kg > 500 or s["reps"] <= 0:
                    skipped_sets += 1
                    continue
                set_id += 1
                key = (workout_id, ex_id)
                set_numbers[key] += 1
                sets.append({
                    "id": set_id,
                    "workout_id": workout_id,
                    "exercise_id": ex_id,
                    "set_number": set_numbers[key],
                    "weight_kg": weight_kg,
                    "reps": int(s["reps"]),
                    "rpe": s.get("rpe"),
                    "equipment": None,
                })

    exercises_json = []
    for name, ident in sorted(exercises.items(), key=lambda item: item[1]):
        exercises_json.append({
            "id": ident,
            "name": name,
            "default_increment_kg": DEFAULT_INCREMENT.get(name, 2.5),
            "muscle_group": None,
            "is_bodyweight": exercise_bodyweight[name],
        })

    # Integrity checks before writing anything.
    location_ids = set(locations.values())
    workout_ids = {w["id"] for w in workouts}
    exercise_ids = set(exercises.values())
    assert all(w["location_id"] is None or w["location_id"] in location_ids for w in workouts)
    assert all(s["workout_id"] in workout_ids and s["exercise_id"] in exercise_ids for s in sets)
    assert len({(s["workout_id"], s["exercise_id"], s["set_number"]) for s in sets}) == len(sets)

    backup = {
        "format": "workout-tracker-backup",
        "schema_version": 1,
        "description": "Импорт тренировочного дневника. Требует проверки сомнительных исходных записей.",
        "units": {"weight": "kg", "date": "YYYY-MM-DD"},
        "locations": [{"id": ident, "name": name} for name, ident in sorted(locations.items(), key=lambda i: i[1])],
        "exercises": exercises_json,
        "workouts": workouts,
        "sets": sets,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(backup, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Тренировок: {len(workouts)}; упражнений: {len(exercises_json)}; подходов: {len(sets)}; залов: {len(locations)}")
    print(f"Пропущено недатированных сессий: {skipped_sessions}; некорректных подходов: {skipped_sets}")
    print(f"Файл для импорта в приложение: {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
