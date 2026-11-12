return {
    update = function(entity, dt)
        local position = Engine.getTransformPosition(entity)
        if not position then return end
        local x, y = 0, 0
        if Engine.Input.isKeyDown(Engine.Keys.A) then x = x - 1 end
        if Engine.Input.isKeyDown(Engine.Keys.D) then x = x + 1 end
        if Engine.Input.isKeyDown(Engine.Keys.W) then y = y + 1 end
        if Engine.Input.isKeyDown(Engine.Keys.S) then y = y - 1 end
        if x ~= 0 or y ~= 0 then
            Engine.setTransformPosition(entity, position.x + x * 120 * dt, position.y + y * 120 * dt)
        end
    end
}
