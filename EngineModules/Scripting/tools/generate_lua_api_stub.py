#!/usr/bin/env python3
"""Validate the Lua API manifest against C++ registrations and emit LuaLS metadata.

Signatures are deliberately declared, not guessed from C++ parameter types: Lua
wrappers may flatten vectors, return nil, or rename methods. No C++ build needed.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import re
import sys

TOOLS = pathlib.Path(__file__).resolve().parent
ROOT = TOOLS.parents[2]


def uncomment(text):
    return re.sub(r'//[^\n]*|/\*.*?\*/', '', text, flags=re.S)


def registrations(directory, tables):
    functions, constants = {}, {}
    for path in sorted(directory.glob('LuaBindings*.cpp')):
        source = uncomment(path.read_text(encoding='utf-8'))
        for table, name, wrapper in re.findall(
                r'set(?:Engine|Table)Function\s*\(\s*\w+\s*,\s*(\w+)\s*,\s*\w+\s*,\s*"(\w+)"\s*,\s*(\w+)\s*\)', source):
            if table not in tables:
                raise ValueError(f'unknown Lua table {table} in {path.name}')
            full_name = f'{tables[table]}.{name}'
            if full_name in functions:
                raise ValueError(f'duplicate registration: {full_name}')
            # Find the wrapper body by balanced braces (nested failure branches matter).
            match = re.search(r'\bint\s+' + re.escape(wrapper) + r'\s*\([^)]*\)\s*\{', source)
            if not match:
                raise ValueError(f'wrapper definition missing: {wrapper}')
            start, depth, end = match.end(), 1, match.end()
            while depth and end < len(source):
                depth += (source[end] == '{') - (source[end] == '}')
                end += 1
            body = source[start:end - 1]
            functions[full_name] = set(re.findall(r'(?:->|\bapi\.)\s*(\w+)\s*\(', body))
        for table, name, expression in re.findall(
                r'setTableInt\s*\(\s*\w+\s*,\s*(\w+)\s*,\s*"(\w+)"\s*,\s*([^;]+)\);', source):
            if table not in tables:
                raise ValueError(f'unknown constant table: {table}')
            constants[f'{tables[table]}.{name}'] = expression.strip()
    return functions, constants


def constant_value(expression, enums, glfw):
    cast = re.fullmatch(r'static_cast<int>\((.*)\)', expression)
    if cast:
        expression = cast.group(1)
    enum = re.fullmatch(r'(?:\w+::)*Input::(\w+)::(\w+)', expression)
    if enum:
        key = '.'.join(enum.groups())
        if key not in enums:
            raise ValueError(f'unknown Input enum: {expression}')
        return constant_value(enums[key], enums, glfw)
    if expression in glfw:
        value = glfw[expression]
        return value if isinstance(value, int) else constant_value(value, enums, glfw)
    if re.fullmatch(r'-?\d+', expression):
        return int(expression)
    raise ValueError(f'unsupported constant expression: {expression}')


def generate(bindings_dir, manifest_path, *, audit_api=True):
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    base = manifest_path.parent
    registered, constants = registrations(bindings_dir, manifest['tables'])
    declared = manifest['functions']
    missing = set(registered) - set(declared)
    extra = set(declared) - set(registered)
    if missing:
        raise ValueError('missing manifest declarations: ' + ', '.join(sorted(missing)))
    if extra:
        raise ValueError('manifest functions not registered: ' + ', '.join(sorted(extra)))
    api_source = uncomment((base / manifest['script_api']).read_text(encoding='utf-8'))
    api_methods = set(re.findall(r'virtual\s+[^;{}]*?\b(\w+)\s*\([^;{}]*?\)\s*(?:const\s*)?(?:=\s*0\s*;|\{)', api_source))
    internal = manifest['internal_methods']
    mapped = set()
    for name, declaration in declared.items():
        method = declaration['method']
        mapped.add(method)
        if method not in api_methods:
            raise ValueError(f'missing ScriptApi method {method} for {name}')
        if not isinstance(declaration.get('params'), list) or not isinstance(declaration.get('returns'), list):
            raise ValueError(f'missing typed signature: {name}')
        for param in declaration['params']:
            if not param.get('name') or not param.get('type'):
                raise ValueError(f'invalid typed parameter: {name}')
        if method not in registered[name] or registered[name] - {method} - set(internal):
            raise ValueError(f'ScriptApi mapping mismatch: {name}: {sorted(registered[name])}')
    if mapped & set(internal):
        raise ValueError('ScriptApi methods cannot be both public and internal')
    if set(internal) - api_methods:
        raise ValueError('unknown internal ScriptApi methods: ' + ', '.join(sorted(set(internal) - api_methods)))
    if any(not reason for reason in internal.values()):
        raise ValueError('internal ScriptApi methods require reasons')
    unclassified = api_methods - mapped - set(internal)
    if unclassified:
        diagnostic = 'unclassified ScriptApi methods: ' + ', '.join(sorted(unclassified))
        if audit_api:
            raise ValueError(diagnostic)
        print('Lua API warning: ' + diagnostic + ' (--check fails until mapped or classified)', file=sys.stderr)
    if set(constants) != set(manifest['constants']):
        raise ValueError('constant manifest mismatch: ' + ', '.join(sorted(set(constants) ^ set(manifest['constants']))))
    enums = {}
    if constants:
        input_source = uncomment((base / manifest['input_header']).read_text(encoding='utf-8'))
        for enum, body in re.findall(r'enum\s+class\s+(\w+)[^{]*\{([^}]+)\}', input_source):
            for name, expression in re.findall(r'(\w+)\s*=\s*([^,\n]+)', body):
                enums[f'{enum}.{name}'] = expression.strip()
    if audit_api and 'component_audit' in manifest:
        config = manifest['component_audit']
        spec = importlib.util.spec_from_file_location('sle_component_audit', TOOLS / 'audit_component_bindings.py')
        auditor = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(auditor)
        include_dirs = [path for pattern in config['include_roots'] for path in base.glob(pattern)]
        auditor.audit_components(include_dirs, base / config['codecs_dir'], base / config['registry'])
    glfw = manifest.get('glfw_constants', {})
    lines = ['---@meta', '-- Generated from lua_api.json and validated C++ Lua bindings. Do not edit.', '']
    for name, fields in manifest['classes'].items():
        lines.append(f'---@class {name}')
        lines.extend(f'---@field {field} {kind}' for field, kind in fields.items())
        lines.append('')
    lines.extend(['Engine = Engine or {}', ''])
    for table in sorted(set(manifest['tables'].values()) - {'Engine'}):
        lines.extend([f'{table} = {table} or {{}}', ''])
    for name, declaration in sorted(declared.items()):
        for param in declaration['params']:
            lines.append(f"---@param {param['name']} {param['type']}")
        lines.extend(f'---@return {kind}' for kind in declaration['returns'])
        parameters = ', '.join(param['name'].rstrip('?') for param in declaration['params'])
        lines.extend([f'function {name}({parameters}) end', ''])
    for name, expression in sorted(constants.items()):
        if expression != manifest['constants'][name]:
            raise ValueError(f'constant declaration mismatch: {name}')
        lines.append(f'{name} = {constant_value(expression, enums, glfw)}')
    return '\n'.join(lines).rstrip() + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bindings-dir', type=pathlib.Path, default=ROOT / 'EngineModules/Scripting/src')
    parser.add_argument('--output', type=pathlib.Path, default=ROOT / 'assets/scripts/engine_api.lua')
    parser.add_argument('--manifest', type=pathlib.Path, default=TOOLS / 'lua_api.json')
    parser.add_argument('--check', action='store_true', help='validate without writing; fail if the stub is stale')
    args = parser.parse_args(argv)
    try:
        output = generate(args.bindings_dir, args.manifest, audit_api=args.check)
        if args.check:
            if not args.output.exists() or args.output.read_text(encoding='utf-8') != output:
                print(f'Stale Lua API stub: {args.output}. Run {pathlib.Path(__file__).name} to regenerate.', file=sys.stderr)
                return 1
            print(f'Lua API manifest and stub are current: {args.output}')
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(output, encoding='utf-8', newline='\n')
            print(f'Generated {args.output}')
        return 0
    except (ValueError, KeyError, OSError) as error:
        print(f'Lua API validation failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
