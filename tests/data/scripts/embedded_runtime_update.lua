return {
    init = function(entity)
        Engine.setTransformPosition(entity, 100, 40)
    end,
    update = function(entity, dt)
        if Engine.Input.isKeyDown(Engine.Keys.D) then
            Engine.setTransformPosition(entity, 120 + dt, 40)
        end
    end
}
