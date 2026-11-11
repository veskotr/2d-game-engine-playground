#!/usr/bin/env python3
"""Audit the repository's plain component structs and adjacent Lua codecs.

This intentionally scans the supported declaration/codec idioms rather than
pretending to parse arbitrary C++. New syntax fails with an actionable diagnostic.
Nested resource types and runtime serialization behavior remain review/test work.
"""
from __future__ import annotations

import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[3]


def uncomment(source):
    return re.sub(r'//[^\n]*|/\*.*?\*/', '', source, flags=re.S)


def block(source, start):
    """Return a balanced brace body and the offset after its closing brace."""
    depth, end = 1, start
    while depth and end < len(source):
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    if depth:
        raise ValueError('unbalanced component declaration')
    return source[start:end - 1], end


def is_method(declaration):
    # Parentheses inside a template type (std::function<void()>) are not methods.
    return '(' in re.sub(r'<.*>', '', declaration.split('=')[0])


def member_fields(body):
    fields, statement, index = set(), '', 0
    while index < len(body):
        char = body[index]
        if char == '{':
            nested, end = block(body, index + 1)
            if is_method(statement):
                # Inline member function: its statements are not component fields.
                statement = ''
            else:
                statement += '{' + nested + '}'
            index = end
            continue
        if char == ';':
            declaration = re.sub(r'\b(?:public|private|protected)\s*:', '', statement).strip()
            if declaration:
                prefix = re.split(r'=|\{', declaration, maxsplit=1)[0].strip()
                if not is_method(prefix) and not prefix.startswith(('using ', 'static ', 'typedef ')):
                    match = re.fullmatch(r'[\w:<>,\s*&]+\s+(\w+)\s*(?:\[[^]]*\])?', prefix)
                    if not match:
                        raise ValueError(f'unsupported component field declaration: {declaration}')
                    fields.add(match.group(1))
            statement = ''
        else:
            statement += char
        index += 1
    return fields


def audit_components(include_dirs, codecs_dir, registry_path):
    types, internal_types = {}, {}
    for directory in include_dirs:
        for path in sorted(directory.rglob('*.hpp')):
            raw = path.read_text(encoding='utf-8')
            internal_types.update(re.findall(r'//[ \t]*Lua-internal-type:[ \t]*(\w+)[ \t]*:[ \t]*(\S[^\n]*)', raw))
            source = uncomment(raw)
            for match in re.finditer(r'\b(?:struct|class)\s+(\w+)([^;{]*)\{', source):
                name = match.group(1)
                if not (name.endswith('Component') or name == 'SpriteRenderer'):
                    continue
                if match.group(2).strip():
                    raise ValueError(f'unsupported component type declaration: {name}{match.group(2)}')
                body, _ = block(source, match.end())
                if name in types:
                    raise ValueError(f'duplicate component type: {name}')
                types[name] = member_fields(body)
    registry = uncomment(registry_path.read_text(encoding='utf-8'))
    descriptors = set(re.findall(r'\{\s*"[^"]+"\s*,\s*descriptor\s*<\s*(\w+)', registry))
    classified = set()
    for path in sorted(codecs_dir.glob('*Bindings.hpp')):
        raw = path.read_text(encoding='utf-8')
        source = uncomment(raw)
        match = re.search(r'\bsnapshot\s*\(\s*const\s+(\w+)\s*&\s*(\w+)\s*\)\s*\{', source)
        if not match:
            raise ValueError(f'missing component snapshot: {path.name}')
        name, variable = match.groups()
        if name not in types:
            raise ValueError(f'unknown component codec type: {name}')
        if name in classified:
            raise ValueError(f'duplicate component codec: {name}')
        if name not in descriptors:
            raise ValueError(f'missing descriptor registration: {name}')
        classified.add(name)
        body, _ = block(source, match.end())
        exposed = set(re.findall(r'\b' + re.escape(variable) + r'\.(\w+)\b(?!\s*\()', body))
        # Getter calls may back private invariant-preserving fields; classify those
        # explicitly and verify that their named Lua output key really exists.
        lua_keys = set(re.findall(r'\[\s*"(\w+)"\s*\]|\{\s*"(\w+)"\s*,', body))
        lua_keys = {key for pair in lua_keys for key in pair if key}
        aliases = dict(re.findall(r'//[ \t]*Lua-field:[ \t]*(\w+)[ \t]*->[ \t]*(\w+)', raw))
        for field, lua_name in aliases.items():
            if lua_name not in lua_keys:
                raise ValueError(f'Lua field {lua_name} missing from {name} snapshot')
            exposed.add(field)
        internal = dict(re.findall(r'//[ \t]*Lua-internal:[ \t]*(\w+)[ \t]*:[ \t]*(\S[^\n]*)', raw))
        if exposed & set(internal):
            raise ValueError(f'component fields both Lua and internal: {name}: {sorted(exposed & set(internal))}')
        unknown = (exposed | set(internal)) - types[name]
        if unknown:
            raise ValueError(f'unknown classified component fields: {name}: {sorted(unknown)}')
        missing = types[name] - exposed - set(internal)
        if missing:
            raise ValueError(f'unclassified component fields: {name}: {sorted(missing)}')
    if set(internal_types) - set(types):
        raise ValueError(f'unknown internal component types: {sorted(set(internal_types) - set(types))}')
    if set(internal_types) & classified:
        raise ValueError(f'component types both Lua and internal: {sorted(set(internal_types) & classified)}')
    missing = set(types) - classified - set(internal_types)
    if missing:
        raise ValueError(f'unclassified component types: {sorted(missing)}')
    if descriptors - classified:
        raise ValueError(f'descriptor registration without codec: {sorted(descriptors - classified)}')
    return len(classified)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    try:
        count = audit_components(list((ROOT / 'EngineModules').glob('*/include')),
                                 ROOT / 'EngineModules/Systems/src/lua/components',
                                 ROOT / 'EngineModules/Systems/src/ComponentRegistry.cpp')
        print(f'Lua component classifications are current: {count} component types')
        return 0
    except (ValueError, OSError) as error:
        parser.exit(1, f'Lua component audit failed: {error}\n')


if __name__ == '__main__':
    raise SystemExit(main())
