local count = 0
return {
    update = function(entity, dt)
        count = count + 1
        Engine.setTransformPosition(entity, count, 0)
    end
}
