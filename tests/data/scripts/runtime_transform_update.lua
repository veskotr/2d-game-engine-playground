return {
    update = function(entity, dt)
        -- Move every frame, so a previous frame's cached pose cannot pass.
        local position = Engine.getTransformPosition(entity)
        Engine.setTransformPosition(entity, position.x + 60, position.y + 20)
    end
}
