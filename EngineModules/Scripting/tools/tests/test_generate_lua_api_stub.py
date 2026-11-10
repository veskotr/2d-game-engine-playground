"""Regression coverage for the source/manifest/stub maintenance contract."""
import contextlib
import io
import json
import importlib.util
import pathlib
import tempfile
import unittest

TOOLS = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('lua_stub', TOOLS / 'generate_lua_api_stub.py')
stub = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stub)
ROOT = TOOLS.parents[2]


class StubTests(unittest.TestCase):
    def test_events_types_and_real_constants(self):
        output = stub.generate(ROOT / 'EngineModules/Scripting/src', TOOLS / 'lua_api.json')
        self.assertIn('function Engine.Events.subscribe(eventName, callback)', output)
        self.assertIn('---@param callback fun(event: table)', output)
        self.assertIn('Engine.Keys.A = 65', output)
        self.assertIn('Engine.Keys.F3 = 292', output)
        self.assertIn('Engine.MouseButtons.RIGHT = 1', output)
        self.assertNotIn('---@param ... any', output)

    def fixture(self, source='', method='float getDeltaTime() const = 0', mapping=True):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = pathlib.Path(directory.name)
        (path / 'src').mkdir()
        (path / 'src/LuaBindingsTest.cpp').write_text(source)
        (path / 'ScriptApi.hpp').write_text('class ScriptApi { public: virtual ' + method + '; };')
        manifest = {'script_api': 'ScriptApi.hpp', 'input_header': 'Input.hpp',
                    'tables': {'engineTable': 'Engine'}, 'constants': {}, 'classes': {},
                    'internal_methods': {}, 'functions': {}}
        if mapping:
            manifest['functions']['Engine.getDeltaTime'] = {'method': 'getDeltaTime', 'params': [], 'returns': ['number']}
        (path / 'lua_api.json').write_text(json.dumps(manifest))
        return path

    def test_unregistered_manifest_function_fails(self):
        path = self.fixture()
        with self.assertRaisesRegex(ValueError, 'not registered'):
            stub.generate(path / 'src', path / 'lua_api.json')

    def test_unlisted_registration_fails(self):
        path = self.fixture('int l_surprise(lua_State* L) { return 0; } setEngineFunction(L, engineTable, api, "surprise", l_surprise);')
        with self.assertRaisesRegex(ValueError, 'missing manifest'):
            stub.generate(path / 'src', path / 'lua_api.json')

    def test_unclassified_api_method_fails(self):
        path = self.fixture(mapping=False)
        with self.assertRaisesRegex(ValueError, 'unclassified ScriptApi'):
            stub.generate(path / 'src', path / 'lua_api.json')

    def test_generation_can_stage_new_unclassified_api_methods(self):
        path = self.fixture(mapping=False)
        with contextlib.redirect_stderr(io.StringIO()) as diagnostics:
            output = stub.generate(path / 'src', path / 'lua_api.json', audit_api=False)
        self.assertIn('unclassified ScriptApi methods: getDeltaTime', diagnostics.getvalue())
        self.assertIn('Engine = Engine or {}', output)

    def test_wrong_method_mapping_fails(self):
        path = self.fixture('int l_time(lua_State* L) { getApi(L)->other(); return 0; }\nsetEngineFunction(L, engineTable, api, "getDeltaTime", l_time);')
        with self.assertRaisesRegex(ValueError, 'mapping mismatch'):
            stub.generate(path / 'src', path / 'lua_api.json')

    def test_check_detects_stale_output_without_overwriting(self):
        path = self.fixture('int l_time(lua_State* L) { getApi(L)->getDeltaTime(); return 0; }\nsetEngineFunction(L, engineTable, api, "getDeltaTime", l_time);')
        output = path / 'api.lua'
        output.write_text('stale\n')
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(stub.main(['--bindings-dir', str(path / 'src'), '--manifest', str(path / 'lua_api.json'), '--output', str(output), '--check']), 1)
        self.assertEqual(output.read_text(), 'stale\n')

    def test_check_accepts_exact_generated_output(self):
        path = self.fixture('int l_time(lua_State* L) { getApi(L)->getDeltaTime(); return 0; } setEngineFunction(L, engineTable, api, "getDeltaTime", l_time);')
        output = path / 'api.lua'
        output.write_text(stub.generate(path / 'src', path / 'lua_api.json'))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(stub.main(['--bindings-dir', str(path / 'src'), '--manifest', str(path / 'lua_api.json'), '--output', str(output), '--check']), 0)

    def test_missing_typed_signature_fails(self):
        path = self.fixture('int l_time(lua_State* L) { getApi(L)->getDeltaTime(); return 0; } setEngineFunction(L, engineTable, api, "getDeltaTime", l_time);')
        manifest = json.loads((path / 'lua_api.json').read_text())
        del manifest['functions']['Engine.getDeltaTime']['params']
        (path / 'lua_api.json').write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'missing typed signature'):
            stub.generate(path / 'src', path / 'lua_api.json')

    def test_constant_resolves_current_enum_reference(self):
        self.assertEqual(stub.constant_value('static_cast<int>(sle::input::Input::Key::A)', {'Key.A': 'GLFW_KEY_A'}, {'GLFW_KEY_A': 65}), 65)
        self.assertEqual(stub.constant_value('static_cast<int>(sle::input::Input::Key::A)', {'Key.A': '67'}, {}), 67)
        with self.assertRaisesRegex(ValueError, 'unsupported constant expression'):
            stub.constant_value('GLFW_KEY_NEW', {}, {})

    def test_runtime_lifecycle_hooks_stay_internal(self):
        output = stub.generate(ROOT / 'EngineModules/Scripting/src', TOOLS / 'lua_api.json')
        self.assertNotIn('function Engine.clearAllEventSubscriptions', output)
        self.assertNotIn('function Engine.getExecutingEntity', output)


if __name__ == '__main__':
    unittest.main()
