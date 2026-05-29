local SPEED = 85.0
local NPC_ID = 2
local PLAYER_ENTITY = nil
local inOuterZone = false
local stepCooldown = 0.0
local STEP_INTERVAL = 0.32  -- seconds between gravel step sounds

local distanceSub = nil
local collisionBeginSub = nil
local collisionEndSub = nil
local zoneEnterSub = nil
local zoneExitSub = nil

local function unsubscribeNpcScopedEvents()
    if distanceSub ~= nil then
        Engine.log("[npc_demo] unsub distance stream handle=" .. tostring(distanceSub))
        Engine.Events.unsubscribe(distanceSub)
        distanceSub = nil
    end
    if collisionBeginSub ~= nil then
        Engine.log("[npc_demo] unsub collision.begin handle=" .. tostring(collisionBeginSub))
        Engine.Events.unsubscribe(collisionBeginSub)
        collisionBeginSub = nil
    end
    if collisionEndSub ~= nil then
        Engine.log("[npc_demo] unsub collision.end handle=" .. tostring(collisionEndSub))
        Engine.Events.unsubscribe(collisionEndSub)
        collisionEndSub = nil
    end
end

local function subscribeNpcScopedEvents()
    if distanceSub == nil then
        distanceSub = Engine.Events.subscribe("zone.npc_outer.distance", function(evt)
            local value = tonumber(evt.payload or "")
            if value ~= nil then
                local speed = 0.55 + math.max(0.0, math.min(1.4, (100.0 - value) / 70.0))
                Engine.setAnimatorSpeed(PLAYER_ENTITY, speed)
            end
        end)
        Engine.log("[npc_demo] sub distance stream handle=" .. tostring(distanceSub))
    end

    if collisionBeginSub == nil then
        collisionBeginSub = Engine.Events.subscribe("collision.begin", function(evt)
            local a = evt.entityA or 0
            local b = evt.entityB or 0
            if a == PLAYER_ENTITY or b == PLAYER_ENTITY then
                Engine.log("[npc_demo] collision.begin for player while in NPC zone")
            end
        end)
        Engine.log("[npc_demo] sub collision.begin handle=" .. tostring(collisionBeginSub))
    end

    if collisionEndSub == nil then
        collisionEndSub = Engine.Events.subscribe("collision.end", function(evt)
            local a = evt.entityA or 0
            local b = evt.entityB or 0
            if a == PLAYER_ENTITY or b == PLAYER_ENTITY then
                Engine.log("[npc_demo] collision.end for player while in NPC zone")
            end
        end)
        Engine.log("[npc_demo] sub collision.end handle=" .. tostring(collisionEndSub))
    end
end

return {
    init = function(entity)
        PLAYER_ENTITY = entity
        Engine.log("npc_zone_demo.lua initialized for entity " .. tostring(entity))

        zoneEnterSub = Engine.Events.subscribe("zone.enter", function(evt)
            if evt.zoneId == "npc_outer" and evt.entityB == PLAYER_ENTITY then
                if not inOuterZone then
                    inOuterZone = true
                    subscribeNpcScopedEvents()
                    Engine.log("[npc_demo] ENTER npc_outer by player -> subscribed")
                end
            end
        end)

        zoneExitSub = Engine.Events.subscribe("zone.exit", function(evt)
            if evt.zoneId == "npc_outer" and evt.entityB == PLAYER_ENTITY then
                if inOuterZone then
                    inOuterZone = false
                    unsubscribeNpcScopedEvents()
                    Engine.setAnimatorSpeed(PLAYER_ENTITY, 1.0)
                    Engine.log("[npc_demo] EXIT npc_outer by player -> unsubscribed")
                end
            end
        end)
    end,

    update = function(entity, dt)
        local moveX = 0.0
        local moveY = 0.0

        if Engine.Input.isKeyDown(Engine.Keys.A) then moveX = moveX - 1.0 end
        if Engine.Input.isKeyDown(Engine.Keys.D) then moveX = moveX + 1.0 end
        if Engine.Input.isKeyDown(Engine.Keys.S) then moveY = moveY - 1.0 end
        if Engine.Input.isKeyDown(Engine.Keys.W) then moveY = moveY + 1.0 end

        Engine.Physics.setVelocity(entity, moveX * SPEED, moveY * SPEED)

        -- Play gravel step sound while moving, throttled by STEP_INTERVAL.
        stepCooldown = math.max(0.0, stepCooldown - dt)
        local isMoving = (moveX ~= 0.0 or moveY ~= 0.0)
        if isMoving and stepCooldown <= 0.0 then
            Engine.playSound(entity, "", false)  -- path pre-set in C++ via resolveAssetPath
            stepCooldown = STEP_INTERVAL
        end

        -- Distance display is updated by zone event stream while subscribed.
    end,

    destroy = function(entity)
        unsubscribeNpcScopedEvents()
        if zoneEnterSub ~= nil then
            Engine.Events.unsubscribe(zoneEnterSub)
            zoneEnterSub = nil
        end
        if zoneExitSub ~= nil then
            Engine.Events.unsubscribe(zoneExitSub)
            zoneExitSub = nil
        end
        Engine.log("npc_zone_demo.lua destroyed for entity " .. tostring(entity))
    end
}
