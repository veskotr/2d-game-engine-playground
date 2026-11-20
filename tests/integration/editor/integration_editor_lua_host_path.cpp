#include <sle/scripting/EditorLuaHost.hpp>

#include <iostream>

int main()
{
    using sle::scripting::inspectBehaviourSource;
    const auto schema = inspectBehaviourSource(R"(
        leaked = true
        return {
            properties = {
                speed = {type = "number", default = 3},
                active = {type = "boolean", default = false},
            },
            init = function() error("init ran") end,
        }
    )", "probe.lua");
    if (!schema.error.empty() || schema.defaults.value("speed", 0) != 3 ||
        schema.defaults.value("active", true) || schema.fields.at(0).at("path").at(0) != "active")
        return 1;

    const auto isolated = inspectBehaviourSource(
        "assert(leaked == nil and io == nil and os == nil and require == nil and string.dump == nil and table.move == nil)",
        "probe.lua");
    if (!isolated.error.empty() || !isolated.fields.empty()) return 2;

    for (const auto* source : {"return {", "error('probe')", "error('', 0)", "return {properties = false}",
             "return {properties = {x = {type = 'boolean', default = 1}}}",
             "while true do end", "return string.rep('a', 9000000)"})
    {
        if (inspectBehaviourSource(source, "probe.lua").error.empty())
        {
            std::cerr << "Expected discovery to reject " << source << '\n';
            return 3;
        }
    }
    using sle::scripting::runEditorLuaConsole;
    const auto command = runEditorLuaConsole(
        "print(snapshot.selection[1], 42); return {{op = 'deleteEntity', entity = snapshot.selection[1]}}",
        {{"selection", nlohmann::json::array({"player"})}});
    if (!command.error.empty() || command.output != "player\t42\n" ||
        command.edits.size() != 1 || command.edits.at(0).at("entity") != "player")
        return 4;
    for (const auto* source : {"return {op = 'deleteEntity'}", "return {{value = function() end}}",
            "local t = {}; t[1] = t; return t", "while true do end", "return io.open('test')"})
        if (runEditorLuaConsole(source, nlohmann::json::object()).error.empty()) return 5;
    using sle::scripting::runEditorLuaCommands;
    const auto* commands = R"(
        assert(io == nil and os == nil and package == nil)
        return {{
            id = "delete", label = "Delete selected", menu = "Tools", shortcut = "Ctrl+F8",
            enabled = function(s) return #s.selection > 0 end,
            run = function(s)
                print(s.selection[1])
                return {{op = "deleteEntity", entity = s.selection[1]}}
            end,
        }}
    )";
    const nlohmann::json emptySelection = {{"selection", nlohmann::json::array()}};
    const nlohmann::json player = {{"selection", nlohmann::json::array({"player"})}};
    const auto declarations = runEditorLuaCommands(commands, "Editor/delete.editor.lua", {}, "discover", "");
    if (!declarations.error.empty() || declarations.edits.size() != 1 ||
        declarations.edits[0].at("id") != "delete" || !declarations.edits[0].at("predicate").get<bool>())
        return 6;
    const auto status = runEditorLuaCommands(commands, "Editor/delete.editor.lua", emptySelection, "enabled", "delete");
    if (!status.error.empty() || status.edits[0].at("enabled").get<bool>()) return 7;
    if (runEditorLuaCommands(commands, "Editor/delete.editor.lua", emptySelection, "run", "delete").error.empty())
        return 8;
    const auto invoked = runEditorLuaCommands(commands, "Editor/delete.editor.lua", player, "run", "delete");
    if (!invoked.error.empty() || invoked.output != "player\n" || invoked.edits.size() != 1 ||
        invoked.edits[0].at("entity") != "player")
        return 9;
    for (const auto* invalid : {"return {{id = 'x', label = 'Broken'}}",
             "return {[2] = {id = 'x', label = 'Broken', run = function() end}}",
             "return {{id = 'x', label = 'Broken', run = function() end, enabled = true}}"})
        if (runEditorLuaCommands(invalid, "Editor/bad.editor.lua", player, "discover", "").error.empty())
            return 10;
    const auto* extension = R"(
        return {
            commands = {{id = "delete", label = "Delete", run = function(s)
                return {{op = "deleteEntity", entity = s.selection[1]}}
            end}},
            drawers = {{component = "SpriteRenderer", path = {"region", "uv"}, widget = "uv"}},
            panels = {{id = "tools", label = "Tools", widgets = {
                {kind = "label", text = "Selection tools"},
                {kind = "button", command = "delete"},
            }}},
        }
    )";
    const auto ui = runEditorLuaCommands(extension, "Editor/tools.editor.lua", {}, "discover", "");
    if (!ui.error.empty() || ui.edits.size() != 3 || ui.edits[1].at("kind") != "drawer" ||
        ui.edits[2].at("kind") != "panel") return 12;
    if (!runEditorLuaCommands(extension, "Editor/tools.editor.lua", player, "run", "delete").error.empty())
        return 13;
    const auto runaway = runEditorLuaCommands(
        "return {{id='x', label='X', run=function() while true do end end}}",
        "Editor/bad.editor.lua", player, "run", "x");
    if (runaway.error.empty()) return 11;
}
