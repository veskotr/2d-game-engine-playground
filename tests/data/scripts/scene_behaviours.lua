function check_scene_behaviours()
 local scene=assert(Engine.getSceneEntity())
 assert(Engine.findEntityByKey('@scene')==scene)
 local child=assert(Engine.findEntityByKey('child'))
 assert(Engine.getParent(child)==scene)
 assert(Engine.Components.get(scene,'Script').behaviours[1].key=='scene')
end
