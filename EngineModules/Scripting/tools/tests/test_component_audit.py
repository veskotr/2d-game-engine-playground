"""Guard maintenance failures using miniature real C++/codec sources."""
import importlib.util
import pathlib
import tempfile
import unittest

TOOLS = pathlib.Path(__file__).resolve().parents[1]


class ComponentAuditTests(unittest.TestCase):
    def audit(self, component, codec, registry='{"Example",descriptor<ExampleComponent>()}'):
        spec = importlib.util.spec_from_file_location('component_audit', TOOLS / 'audit_component_bindings.py')
        audit = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(audit)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'include').mkdir()
            (root / 'codecs').mkdir()
            (root / 'include/Example.hpp').write_text(component)
            (root / 'codecs/ExampleBindings.hpp').write_text(codec)
            (root / 'registry.cpp').write_text(registry)
            audit.audit_components([root / 'include'], root / 'codecs', root / 'registry.cpp')

    def test_new_field_must_be_exposed_or_internal(self):
        with self.assertRaisesRegex(ValueError, 'unclassified component fields.*newField'):
            self.audit('struct ExampleComponent { bool visible=true; float newField=0; };',
                       'Json snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_explicit_internal_component_type_is_classified(self):
        self.audit('struct ExampleComponent { bool visible=true; }; // Lua-internal-type: RuntimeComponent: orchestration cache\nstruct RuntimeComponent { int value; };',
                   'Json snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_inherited_component_declaration_fails_explicitly(self):
        with self.assertRaisesRegex(ValueError, 'unsupported component type declaration'):
            self.audit('struct ExampleComponent { bool visible=true; }; struct AnotherComponent : Base { int value; };',
                       'Json snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_new_component_type_requires_codec_and_registration(self):
        with self.assertRaisesRegex(ValueError, 'unclassified component types.*AnotherComponent'):
            self.audit('struct ExampleComponent { bool visible=true; }; struct AnotherComponent { int value; };',
                       'Json snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_unsupported_callback_field_cannot_silently_disappear(self):
        with self.assertRaisesRegex(ValueError, 'unsupported component field declaration'):
            self.audit('struct ExampleComponent { bool visible=true; std::function<void()> callback; };',
                       'Json snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_explicit_internal_field_and_accessor_alias(self):
        self.audit('struct ExampleComponent { public: float getValue() const { return value; } private: float value{0}; bool dirty=true; };',
                   '// Lua-field: value -> amount\n// Lua-internal: dirty: derived runtime state\nJson snapshot(const ExampleComponent& c) { j["amount"]=c.getValue(); return j; }')

    def test_internal_field_requires_reason_on_same_line(self):
        with self.assertRaisesRegex(ValueError, 'unclassified component fields.*hidden'):
            self.audit('struct ExampleComponent { bool visible=true; int hidden; };',
                       '// Lua-internal: hidden: \nJson snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_stale_internal_marker_fails(self):
        with self.assertRaisesRegex(ValueError, 'unknown classified component fields.*gone'):
            self.audit('struct ExampleComponent { bool visible=true; };',
                       '// Lua-internal: gone: runtime state\nJson snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }')

    def test_accessor_marker_must_name_a_real_lua_field(self):
        with self.assertRaisesRegex(ValueError, 'Lua field.*missing'):
            self.audit('struct ExampleComponent { float value; };',
                       '// Lua-field: value -> missing\nJson snapshot(const ExampleComponent& c) { return {}; }')

    def test_codec_requires_actual_descriptor_registration(self):
        with self.assertRaisesRegex(ValueError, 'descriptor registration'):
            self.audit('struct ExampleComponent { bool visible=true; };',
                       'Json snapshot(const ExampleComponent& c) { j["visible"]=c.visible; return j; }', registry='')


if __name__ == '__main__':
    unittest.main()
