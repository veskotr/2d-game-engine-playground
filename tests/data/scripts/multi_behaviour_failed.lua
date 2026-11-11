return {
 init=function(entity)
    record_behaviour('failed:'..Engine.getBehaviourKey())
    Engine.Events.subscribe('multi.tick',function() error('failed behaviour listener leaked') end)
    error('intentional failed behaviour')
 end
}
