behaviour_trace = {}
function record_behaviour(label) table.insert(behaviour_trace,label) end
function expect_behaviour_trace(expected)
    local actual=table.concat(behaviour_trace,',')
    behaviour_trace={}
    if actual~=expected then Engine.log('Expected '..expected..' got '..actual) end
    return actual==expected
end
function clear_behaviour_trace() behaviour_trace={} end
function spawn_scripted_prefab()
 local root,err=Engine.instantiatePrefab('tests/data/prefabs/actor.prefab.json','spawned',{
    root={Script={behaviours={{key='prefab',scriptAsset='tests/data/scripts/multi_behaviour.lua',order=20}}}}
 })
 assert(root,err)
 assert(Engine.getParent(root)==Engine.getSceneEntity())
 assert(Engine.findEntityByKey('spawned/child'))
 local failed,message=Engine.instantiatePrefab('tests/data/prefabs/actor.prefab.json','spawned')
 assert(failed==nil and type(message)=='string')
 return true
end
