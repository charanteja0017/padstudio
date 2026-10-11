#!/usr/bin/env python3
"""Extract Key Mapper's backup JSON vocabulary from its source code.

  python3 -I tools/lib/keymapper_schema.py <keymapper_checkout> <out.json>

Reads the Room/Gson entity classes and BackupContent, and writes every JSON key, action type,
constraint type and extra id they declare. PadStudio's tests check that the generated pen profile
only uses names from this snapshot, so a Key Mapper update that renames a field fails a test
instead of silently producing a backup that restores to nothing.

Re-run after updating forks/padpen and commit the new snapshot (see docs/decisions/ADR-000).
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ENTITIES = "data/src/main/java/io/github/sds100/keymapper/data/entities"
DB = "data/src/main/java/io/github/sds100/keymapper/data/db/AppDatabase.kt"
BACKUP = "base/src/main/java/io/github/sds100/keymapper/base/backup/BackupContent.kt"
SYSTEM_ACTIONS = "base/src/main/java/io/github/sds100/keymapper/base/actions/ActionDataEntityMapper.kt"

CONST = re.compile(r'const val ([A-Z0-9_]+)\s*=\s*"([^"]*)"')
CONST_INT = re.compile(r"const val ([A-Z0-9_]+)\s*=\s*(-?\d+)\b")


def consts(path: Path) -> dict[str, str]:
    return dict(CONST.findall(path.read_text()))


def int_consts(path: Path) -> dict[str, int]:
    return {k: int(v) for k, v in CONST_INT.findall(path.read_text())}


def json_keys(path: Path) -> list[str]:
    """Values of NAME_* constants: the keys Gson writes for this entity."""
    return sorted({v for k, v in consts(path).items() if k.startswith("NAME_")})


def enum_entries(path: Path, enum_name: str) -> list[str]:
    text = path.read_text()
    match = re.search(r"enum class " + enum_name + r"\s*\{(.*?)\}", text, re.S)
    if not match:
        return []
    body = re.sub(r"//[^\n]*", "", match.group(1))  # comments such as "// DONT CHANGE THESE"
    return [e.strip() for e in body.split(",") if re.fullmatch(r"[A-Z_0-9]+", e.strip())]


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 64
    root = Path(argv[1])
    entities = root / ENTITIES
    action = entities / "ActionEntity.kt"
    constraint = entities / "ConstraintEntity.kt"
    trigger = entities / "TriggerEntity.kt"
    trigger_key = entities / "TriggerKeyEntity.kt"
    db_version = int_consts(root / DB)["DATABASE_VERSION"]
    system_ids = sorted(set(re.findall(r'ActionId\.[A-Z0-9_]+ to "([a-z0-9_]+)"', (root / SYSTEM_ACTIONS).read_text())))

    schema = {
        "source": "keymapperorg/KeyMapper — extracted by tools/lib/keymapper_schema.py",
        "database_version": db_version,
        "backup": json_keys(root / BACKUP),
        "group": json_keys(entities / "GroupEntity.kt"),
        "key_map": json_keys(entities / "KeyMapEntity.kt"),
        "trigger": json_keys(trigger),
        "trigger_key_common": json_keys(trigger_key),
        "key_event_trigger_key": sorted(set(json_keys(entities / "KeyEventTriggerKeyEntity.kt")) | set(json_keys(trigger_key))),
        "evdev_trigger_key": sorted(set(json_keys(entities / "EvdevTriggerKeyEntity.kt")) | set(json_keys(trigger_key))),
        "action": json_keys(action),
        "constraint": json_keys(constraint),
        "constraint_group": json_keys(entities / "ConstraintGroupEntity.kt"),
        "extra": json_keys(entities / "EntityExtra.kt"),
        "action_types": enum_entries(action, "Type"),
        "action_extras": sorted(v for k, v in consts(action).items() if k.startswith("EXTRA_")),
        "system_action_ids": system_ids,
        "constraint_types": sorted(
            v for k, v in consts(constraint).items() if not k.startswith(("NAME_", "EXTRA_")) and "DEPRECATION" not in k
        ),
        "constraint_extras": sorted(v for k, v in consts(constraint).items() if k.startswith("EXTRA_")),
        "ints": {
            "constraint_mode_or": int_consts(constraint)["MODE_OR"],
            "constraint_mode_and": int_consts(constraint)["MODE_AND"],
            "click_short": int_consts(trigger_key)["SHORT_PRESS"],
            "click_long": int_consts(trigger_key)["LONG_PRESS"],
            "click_double": int_consts(trigger_key)["DOUBLE_PRESS"],
            "trigger_mode_parallel": int_consts(trigger)["PARALLEL"],
            "trigger_mode_sequence": int_consts(trigger)["SEQUENCE"],
            "trigger_mode_undefined": int_consts(trigger)["UNDEFINED"],
        },
        "device_ids": {
            k: v for k, v in consts(entities / "KeyEventTriggerKeyEntity.kt").items() if k.startswith("DEVICE_ID_")
        },
    }
    Path(argv[2]).write_text(json.dumps(schema, indent=2) + "\n")
    print(f"wrote {argv[2]} (database version {db_version}, {len(schema['action_types'])} action types, "
          f"{len(system_ids)} system action ids)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
