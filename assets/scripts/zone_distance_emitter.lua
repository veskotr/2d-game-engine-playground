local PLAYER_ID = 1
local NPC_ID = 2
local TICK_INTERVAL = 0.12

local tick = 0.0

return {
    init = function(entity)
        Engine.log("zone_distance_emitter.lua init on zone entity " .. tostring(entity))
    end,

    update = function(entity, dt)
        tick = tick + dt
        if tick < TICK_INTERVAL then
            return
        end
        tick = 0.0

        local playerPos = Engine.getTransformPosition(PLAYER_ID)
        local npcPos = Engine.getTransformPosition(NPC_ID)
        if playerPos == nil or npcPos == nil then
            return
        end

        local dx = npcPos.x - playerPos.x
        local dy = npcPos.y - playerPos.y
        local distance = math.sqrt(dx * dx + dy * dy)

        Engine.Events.emit("zone.npc_outer.distance", string.format("%.3f", distance), entity)
    end,

    destroy = function(entity)
        Engine.log("zone_distance_emitter.lua destroy on zone entity " .. tostring(entity))
    end
}
