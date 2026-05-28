function integration_mark(entityId, token)
    if token ~= "ok_token" then
        error("unexpected token: " .. tostring(token))
    end

    if entityId == 0 then
        error("expected non-zero entity id")
    end
end

return {
    init = function(entity)
    end,
    update = function(entity, dt)
    end,
    destroy = function(entity)
    end
}
