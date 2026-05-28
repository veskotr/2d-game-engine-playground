local M = {}

function M.init(entity)
    Engine.log("init:" .. tostring(entity))
end

function M.update(entity, dt)
    -- no-op for this regression path
end

function M.on_exit_idle(entity)
    Engine.log("exit_idle:" .. tostring(entity))
end

function M.on_enter_run(entity)
    Engine.log("enter_run:" .. tostring(entity))
end

return M
