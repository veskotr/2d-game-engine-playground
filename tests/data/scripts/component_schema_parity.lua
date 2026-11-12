local cases = {
 transform = {'Transform', {position={x=3,y=4},rotation=0.5,scale={x=2,y=3}}},
 sprite = {'SpriteRenderer', {color={x=0.2,y=0.4,z=0.6,w=1},visible=false,layer=-2,region={uv={x=0,y=0,z=0.5,w=0.5}}}},
 sprite_geometry = {'SpriteRenderer', {size={x=3,y=4},offset={x=1,y=-2}}},
 sprite_size_negative = {'SpriteRenderer', {size={x=-1,y=2}}},
 sprite_size_zero = {'SpriteRenderer', {size={x=0,y=2}}},
 body = {'RigidBody', {bodyType='Kinematic',velocity={x=3,y=4},linearDamping=0.2}},
 box = {'BoxCollider', {size={x=3,y=4},maskBits=4}},
 circle = {'CircleCollider', {radius=2,density=0.4}},
 box_zone = {'BoxZone', {size={x=3,y=4},zoneId='entry'}},
 circle_zone = {'CircleZone', {radius=2,zoneId='exit'}},
 script = {'Script', {scriptAsset='assets/scripts/player_move.lua',enabled=false}},
 audio = {'Audio', {assetPath='assets/sounds/gravel.ogg',volume=0.4,pitch=1.2}},
 ui = {'UI', {layoutAsset='  ',fontAsset='',behaviorAsset='',spaceMode='World'}},
 animator = {'Animator', {clipAsset='',timeSeconds=0.5,overrideLoopMode=true,loopMode='Once',floatParameters={speed=2}}},
 state = {'StateMachine', {definitionAsset='',boolParameters={moving=true},triggers={'jump'}}},
 chunk = {'ChunkActivation', {homeChunkX=-2,mode='AlwaysActive',keepScriptsWhenInactive=true}},
 volume_high = {'Audio', {volume=2}},
 pitch_zero = {'Audio', {pitch=0}},
 wrong_bool = {'Audio', {loop=1}},
 wrong_number = {'Audio', {volume='0.5'}},
 negative_size = {'BoxCollider', {size={x=-1,y=2}}},
 bad_mask = {'BoxCollider', {maskBits=65536}},
 fractional_mask = {'BoxCollider', {maskBits=1.5}},
 bad_enum = {'RigidBody', {bodyType='Flying'}},
 negative_damping = {'RigidBody', {linearDamping=-1}},
 negative_layer = {'UI', {layer=-1}},
 bad_vector = {'Transform', {position={x=1}}},
 long_vector = {'Transform', {position={x=1,y=2,z=3}}},
 unknown_field = {'Audio', {volume=0.2,typo=true}},
 internal_field = {'Audio', {volume=0.2,runtimeHandle=1}},
 bad_map = {'Animator', {floatParameters={speed='fast'}}},
 bad_trigger = {'StateMachine', {triggers={3}}},
 bad_asset = {'Script', {scriptAsset=false}},
 read_only = {'WorldTransform', {rotation=1}},
 overflow_float = {'Transform', {rotation=1e100}},
 not_object = {'Audio',3},
 asset_maps = {'Animator',{clipAsset='',stateClipMap={Idle='assets/animations/player_color_red_blue.clip.json'}}}
}
cases.transform_canonical=cases.transform
cases.script_canonical=cases.script
cases.audio_canonical=cases.audio
function apply_schema_case(entity, name)
    if string.sub(name,1,8)=='default_' then
        return Engine.Components.set(entity,string.sub(name,9),{})
    end
    local case=assert(cases[name],name)
    return Engine.Components.set(entity,case[1],case[2])
end
