local SPEED = 120.0
local MIN_SPAWN_RADIUS = 32.0      -- right next to parent
local MAX_SPAWN_RADIUS = 500.0    -- max distance away
local CHILD_COUNT = 250            -- target spawn count per burst
local MAX_CHILDREN = 20000         -- hard cap for active direct children
local SPAWN_BUDGET_PER_SECOND = 3000 -- max spawned children per second
local SPAWN_INTERVAL_SEC = 0.10    -- while holding SPACE, spawn at most every 100ms
local STATUS_LOG_INTERVAL_SEC = 1.0 -- aggregated status log period
local LOG_LEVEL = "info"          -- "debug", "info", "warn", "silent"
local childrenSpawnedCount = 0
local spawnCooldown = 0.0
local spawnBudget = 0.0
local spawnCapWarned = false
local statusLogTimer = 0.0
local spawnedSinceLastStatus = 0
local removedSinceLastStatus = 0

local function isLogEnabled(level)
    if LOG_LEVEL == "silent" then
        return false
    end

    if level == "warn" then
        return LOG_LEVEL == "warn" or LOG_LEVEL == "info" or LOG_LEVEL == "debug"
    end

    if level == "info" then
        return LOG_LEVEL == "info" or LOG_LEVEL == "debug"
    end

    if level == "debug" then
        return LOG_LEVEL == "debug"
    end

    return false
end

local function logWarn(msg)
    if isLogEnabled("warn") then
        Engine.warn(msg)
    end
end

local function logInfo(msg)
    if isLogEnabled("info") then
        Engine.log(msg)
    end
end

local function logDebug(msg)
    if isLogEnabled("debug") then
        Engine.log(msg)
    end
end

local function flushStatusLog(parentEntity)
    if spawnedSinceLastStatus <= 0 and removedSinceLastStatus <= 0 then
        return
    end

    local activeChildren = Engine.getChildCount(parentEntity)
    logInfo(
        "Spawn stress status for " .. tostring(parentEntity)
        .. ": +" .. tostring(spawnedSinceLastStatus)
        .. ", -" .. tostring(removedSinceLastStatus)
        .. ", active=" .. tostring(activeChildren)
        .. ", totalSpawned=" .. tostring(childrenSpawnedCount))

    spawnedSinceLastStatus = 0
    removedSinceLastStatus = 0
end

local function spawnChildrenAround(parentEntity, spawnCount)
    local parentScale = Engine.getTransformScale(parentEntity)
    if parentScale == nil then
        parentScale = { x = 1.0, y = 1.0 }
    end

    local safeScaleX = math.max(math.abs(parentScale.x), 0.0001)
    local safeScaleY = math.max(math.abs(parentScale.y), 0.0001)

    for _ = 1, spawnCount do
        -- random angle (0 -> 360 degrees in radians)
        local angle = math.random() * math.pi * 2

        -- random distance from parent (min -> max)
        local radius = MIN_SPAWN_RADIUS + math.random() * (MAX_SPAWN_RADIUS - MIN_SPAWN_RADIUS)

        -- convert polar -> cartesian
        local offsetX = (math.cos(angle) * radius) / safeScaleX
        local offsetY = (math.sin(angle) * radius) / safeScaleY

        local child = Engine.createEntity()
        Engine.setSpriteTexture(child, "assets/textures/tile2.png")
        Engine.setParent(child, parentEntity)
        Engine.setTransformPosition(child, offsetX, offsetY)
    end

    childrenSpawnedCount = childrenSpawnedCount + spawnCount
    spawnedSinceLastStatus = spawnedSinceLastStatus + spawnCount
    logDebug("Spawned burst of " .. tostring(spawnCount) .. " children")
end

return {
    init = function(entity)
        logInfo("player_move.lua initialized for entity " .. tostring(entity))
    end,

    update = function(entity, dt)
        spawnCooldown = math.max(0.0, spawnCooldown - dt)
        spawnBudget = math.min(SPAWN_BUDGET_PER_SECOND, spawnBudget + dt * SPAWN_BUDGET_PER_SECOND)
        statusLogTimer = statusLogTimer + dt

        local pos = Engine.getTransformPosition(entity)
        if pos == nil then
            return
        end

        local dx = 0.0
        local dy = 0.0

        if Engine.Input.isKeyDown(Engine.Keys.W) then
            dy = dy + SPEED * dt
        end
        if Engine.Input.isKeyDown(Engine.Keys.S) then
            dy = dy - SPEED * dt
        end
        if Engine.Input.isKeyDown(Engine.Keys.A) then
            dx = dx - SPEED * dt
        end
        if Engine.Input.isKeyDown(Engine.Keys.D) then
            dx = dx + SPEED * dt
        end

        if dx ~= 0.0 or dy ~= 0.0 then
            Engine.setTransformPosition(entity, pos.x + dx, pos.y + dy)
        end

        if Engine.Input.isKeyPressed(Engine.Keys.C) then
            local removed = Engine.destroyChildren(entity)
            removedSinceLastStatus = removedSinceLastStatus + removed
            logInfo("Removed children from " .. tostring(entity) .. ": " .. tostring(removed))
            spawnCapWarned = false
        end

        local activeChildren = Engine.getChildCount(entity)
        if activeChildren < MAX_CHILDREN and Engine.Input.isKeyDown(Engine.Keys.SPACE) and spawnCooldown <= 0.0 then
            local spawnByCap = MAX_CHILDREN - activeChildren
            local spawnByBudget = math.floor(spawnBudget)
            local spawnCount = math.min(CHILD_COUNT, spawnByCap, spawnByBudget)

            if spawnCount > 0 then
                spawnChildrenAround(entity, spawnCount)
                spawnBudget = spawnBudget - spawnCount
                spawnCooldown = SPAWN_INTERVAL_SEC
                spawnCapWarned = false
            end
        elseif activeChildren >= MAX_CHILDREN and Engine.Input.isKeyDown(Engine.Keys.SPACE) and not spawnCapWarned then
            logWarn("Active child cap reached: " .. tostring(MAX_CHILDREN) .. " (press C to clear)")
            spawnCapWarned = true
        end

        if statusLogTimer >= STATUS_LOG_INTERVAL_SEC then
            flushStatusLog(entity)
            statusLogTimer = 0.0
        end
    end,

    destroy = function(entity)
        flushStatusLog(entity)
        logInfo("player_move.lua destroyed for entity " .. tostring(entity))
    end
}
