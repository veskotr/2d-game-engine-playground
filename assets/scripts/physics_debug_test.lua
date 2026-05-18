local MOVE_SPEED = 75.0
local JUMP_IMPULSE = 100.0
local LOG_INTERVAL = 1.0

local logTimer = 0.0
local wasTouching = false

local function logVec2(prefix, v)
    Engine.log(prefix .. " {x=" .. string.format("%.2f", v.x) .. ", y=" .. string.format("%.2f", v.y) .. "}")
end

return {
    init = function(entity)
        Engine.log("physics_debug_test.lua init for entity " .. tostring(entity))
        Engine.log("Controls: WASD move, SPACE jump impulse, F toggle debug, R raycast down, Q low gravity, E normal gravity")
        Engine.Physics.setDebugEnabled(true)
    end,

    update = function(entity, dt)
        local moveX = 0.0

        if Engine.Input.isKeyDown(Engine.Keys.A) then
            moveX = moveX - 1.0
        end
        if Engine.Input.isKeyDown(Engine.Keys.D) then
            moveX = moveX + 1.0
        end

        local velocity = Engine.Physics.getVelocity(entity)
        if velocity ~= nil then
            Engine.Physics.setVelocity(entity, moveX * MOVE_SPEED, velocity.y)
        end

        if Engine.Input.isKeyPressed(Engine.Keys.SPACE) then
            Engine.Physics.addImpulse(entity, 0.0, JUMP_IMPULSE)
        end

        if Engine.Input.isKeyPressed(Engine.Keys.F) then
            local enabled = Engine.Physics.isDebugEnabled()
            Engine.Physics.setDebugEnabled(not enabled)
            Engine.log("Physics debug toggled from script: " .. tostring(not enabled))
        end

        if Engine.Input.isKeyPressed(Engine.Keys.Q) then
            Engine.Physics.setGravityScale(entity, 0.2)
            Engine.log("Gravity scale set to 0.2")
        end

        if Engine.Input.isKeyPressed(Engine.Keys.E) then
            Engine.Physics.setGravityScale(entity, 1.0)
            Engine.log("Gravity scale set to 1.0")
        end

        if Engine.Input.isKeyPressed(Engine.Keys.R) then
            local pos = Engine.getTransformPosition(entity)
            if pos ~= nil then
                local hit = Engine.Physics.raycastFirst(pos.x, pos.y, pos.x, pos.y - 250.0)
                if hit ~= nil then
                    Engine.log("raycastFirst hit entity=" .. tostring(hit.entityId) .. " fraction=" .. string.format("%.3f", hit.fraction))
                    logVec2("Hit point", hit.point)
                    logVec2("Hit normal", hit.normal)
                else
                    Engine.warn("raycastFirst: no hit")
                end

                local hits = Engine.Physics.raycastAll(pos.x, pos.y, pos.x, pos.y - 250.0)
                Engine.log("raycastAll hit count: " .. tostring(#hits))
            end
        end

        local touching = Engine.Physics.isTouching(entity)
        if touching ~= wasTouching then
            wasTouching = touching
            Engine.log("isTouching changed: " .. tostring(touching))
        end

        logTimer = logTimer + dt
        if logTimer >= LOG_INTERVAL then
            logTimer = 0.0
            local velocity = Engine.Physics.getVelocity(entity)
            if velocity ~= nil then
                logVec2("velocity", velocity)
            end
        end
    end,

    destroy = function(entity)
        Engine.log("physics_debug_test.lua destroy for entity " .. tostring(entity))
    end
}
