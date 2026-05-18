#!/usr/bin/env python3
"""Generate a Lua API IntelliSense stub from C++ Lua binding registrations."""

from __future__ import annotations

import argparse
import pathlib
import re
from collections import defaultdict


def parse_bindings(bindings_dir: pathlib.Path):
    engine_funcs = set()
    table_funcs = defaultdict(set)
    table_consts = defaultdict(set)

    cpp_files = sorted(bindings_dir.glob("LuaBindings*.cpp"))

    engine_re = re.compile(r'setEngineFunction\s*\([^,]+,\s*[^,]+,\s*[^,]+,\s*"([A-Za-z0-9_]+)"')
    table_func_re = re.compile(r'setTableFunction\s*\([^,]+,\s*([A-Za-z0-9_]+)\s*,\s*[^,]+,\s*"([A-Za-z0-9_]+)"')
    table_int_re = re.compile(r'setTableInt\s*\([^,]+,\s*([A-Za-z0-9_]+)\s*,\s*"([A-Za-z0-9_]+)"')

    for file_path in cpp_files:
        text = file_path.read_text(encoding="utf-8")

        for name in engine_re.findall(text):
            engine_funcs.add(name)

        for table_name, fn_name in table_func_re.findall(text):
            table_funcs[table_name].add(fn_name)

        for table_name, const_name in table_int_re.findall(text):
            table_consts[table_name].add(const_name)

    return {
        "engine": sorted(engine_funcs),
        "table_funcs": {k: sorted(v) for k, v in table_funcs.items()},
        "table_consts": {k: sorted(v) for k, v in table_consts.items()},
    }


def emit_stub(parsed) -> str:
    table_map = {
        "physicsTable": "Physics",
        "inputTable": "Input",
        "cameraTable": "Camera",
    }

    const_map = {
        "keysTable": "Keys",
        "mouseButtonsTable": "MouseButtons",
    }

    lines = []
    lines.append("---@meta")
    lines.append("-- Auto-generated from C++ Lua bindings. Do not edit manually.")
    lines.append("")
    lines.append("---@class Vec2")
    lines.append("---@field x number")
    lines.append("---@field y number")
    lines.append("")
    lines.append("---@class PhysicsRaycastHit")
    lines.append("---@field entityId integer")
    lines.append("---@field point Vec2")
    lines.append("---@field normal Vec2")
    lines.append("---@field fraction number")
    lines.append("")
    lines.append("Engine = Engine or {}")
    lines.append("")

    for fn_name in parsed["engine"]:
        lines.append("---@param ... any")
        lines.append("---@return any")
        lines.append(f"function Engine.{fn_name}(...) end")
        lines.append("")

    for table_var, table_name in table_map.items():
        lines.append(f"Engine.{table_name} = Engine.{table_name} or {{}}")
        lines.append("")
        for fn_name in parsed["table_funcs"].get(table_var, []):
            lines.append("---@param ... any")
            lines.append("---@return any")
            lines.append(f"function Engine.{table_name}.{fn_name}(...) end")
            lines.append("")

    for table_var, table_name in const_map.items():
        lines.append(f"Engine.{table_name} = Engine.{table_name} or {{}}")
        for const_name in parsed["table_consts"].get(table_var, []):
            lines.append(f"Engine.{table_name}.{const_name} = 0")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate Lua API stub from Lua binding C++ sources")
    parser.add_argument("--bindings-dir", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()

    parsed = parse_bindings(args.bindings_dir)
    output_text = emit_stub(parsed)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output_text, encoding="utf-8")
    print(f"Generated {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
