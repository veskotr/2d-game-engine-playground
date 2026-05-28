local M = {}

function M.init(entity)
    Engine.log("init_missing:" .. tostring(entity))
end

function M.update(entity, dt)
    -- no-op
end

return M
