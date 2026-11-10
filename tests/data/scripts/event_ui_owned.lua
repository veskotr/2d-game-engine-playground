local entity = ...
-- executeScriptAssetForEntity supplies ownership context, not chunk arguments.
local callback = function(event)
    local pos = Engine.getTransformPosition(event.sourceEntity)
    if pos then Engine.setTransformPosition(event.sourceEntity, pos.x, pos.y + 1) end
end
ui_event_weak = setmetatable({callback}, {__mode = "v"})
Engine.Events.subscribe("ui.owned", callback)
function ui_event_collected()
    collectgarbage("collect")
    return ui_event_weak[1] == nil
end
