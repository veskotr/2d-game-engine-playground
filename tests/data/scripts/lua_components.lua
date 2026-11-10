local e = Engine.createEntity()
assert(Engine.Components.get(e, 'Audio') == nil)
assert(Engine.Components.set(e, 'Transform', {position={x=3,y=4}, rotation=0.5}))
assert(Engine.Components.get(e, 'Transform').position.x == 3)
assert(Engine.Components.set(e, 'Audio', {assetPath='test.wav', volume=0.4, loop=true}))
local ok, err = Engine.Components.set(e, 'Audio', {volume=0.8, runtimeHandle=42})
assert(not ok and type(err)=='string')
assert(math.abs(Engine.Components.get(e, 'Audio').volume - 0.4)<0.001)
assert(not Engine.Components.set(e, 'Audio', {volume=2}))
assert(not Engine.Components.set(e, 'Audio', {loop=1}))
assert(not Engine.Components.set(e, 'WorldTransform', {rotation=1}))
assert(Engine.Components.has(e, 'Audio'))
local found=false
for _, id in ipairs(Engine.Components.find('Audio')) do if id == e then found=true end end
assert(found)
assert(Engine.Components.remove(e, 'Audio'))
assert(not Engine.Components.has(e, 'Audio'))
assert(not Engine.Components.remove(e,'Audio'))
assert(not Engine.Components.set(0, 'Audio', {}))
local schemas = {
 SpriteRenderer={color={x=1,y=0.5,z=0.2,w=1},layer=-2,visible=false},
 Script={scriptAsset='',enabled=false},
 RigidBody={bodyType='Kinematic',linearDamping=0.2,velocity={x=2,y=3}},
 BoxCollider={size={x=2,y=3},categoryBits=2,maskBits=4},
 CircleCollider={radius=2,density=0.5},
 BoxZone={zoneId='entry',size={x=3,y=2}},
 CircleZone={zoneId='exit',radius=4},
 Animator={clipAsset='',loopMode='Once',targetEntities={self=e},floatParameters={speed=0.2}},
 StateMachine={definitionAsset='',boolParameters={moving=true},triggers={'jump'}},
 UI={layoutAsset='',spaceMode='World',layer=2},
 ChunkActivation={homeChunkX=-2,mode='AlwaysActive',keepScriptsWhenInactive=true}
}
for name, patch in pairs(schemas) do
 assert(Engine.Components.set(e,name,patch),name)
 assert(Engine.Components.has(e,name),name)
 assert(Engine.Components.get(e,name) ~= nil,name)
 assert(not Engine.Components.set(e,name,{internalInvalid=true}),name)
 assert(Engine.Components.remove(e,name),name)
end
assert(Engine.Components.set(e,'StateMachine',{triggers={'a'}}))
assert(Engine.Components.set(e,'StateMachine',{triggers={}}))
assert(next(Engine.Components.get(e,'StateMachine').triggers)==nil)
assert(not Engine.Components.set(e,'RigidBody',{mass=2}))
assert(not Engine.Components.set(e,'CircleCollider',{radius=-1}))
assert(not Engine.Components.set(e,'BoxCollider',{maskBits=65536}))
assert(not Engine.Components.set(e,'Animator',{targetEntities={invalid=0}}))
assert(not Engine.Components.set(e,'Transform',{position={x=0/0,y=1}}))
components_test_ok = true
function components_passed() return components_test_ok == true end

local keyed = Engine.createEntity()
assert(Engine.Components.set(keyed,'Animator',{floatParameters={['a\0b']=1,a=2}}))
local keys=Engine.Components.get(keyed,'Animator').floatParameters
assert(keys['a\0b']==1 and keys.a==2,'map keys must round-trip without truncation')
