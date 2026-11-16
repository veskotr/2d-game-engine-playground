multiviewUpdates = 0
function MultiviewUpdatedOnce() return multiviewUpdates == 1 end
function MultiviewUpdatedTwice() return multiviewUpdates == 2 end
return {
    update = function(entity, dt)
        multiviewUpdates = multiviewUpdates + 1
        Engine.setTransformPosition(entity, multiviewUpdates * 10, dt)
    end
}
