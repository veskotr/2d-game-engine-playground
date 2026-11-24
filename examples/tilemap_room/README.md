# Tilemap room

Open `Room.sleproject` in the SLE Editor and open **Room.scene.json** from
the Asset Browser. The room has a terrain layer with solid, sloped and
perforated tiles, plus a separate decoration layer. Open **Tiles…** in the
Scene viewport to paint more cells. Use **Play** to see the startup camera's view.

## Make a room from the PNG

1. Create a project. In its Asset Browser, click **Import…** and select
   [Room.png](Assets/Room.png). Select the imported PNG, set **Texture
   filter** to **Nearest**, and click **Open spritesheet…**.
2. In Grid mode, enter `16, 16` for cell size and `0, 0` for both margins
   and spacing. Click **Add grid regions**. The editor names the four
   regions Slice 1–4. From left to right they correspond to Solid, Slope,
   Perforated and Decoration.
3. Copy [Room.tileset.json](Assets/Room.tileset.json) into the new project's
   Assets folder. Set its `spritesheet` to `project-asset://` followed by
   the imported PNG's UUID. Update each tile's `region` with its
   corresponding UUID from the PNG's details. Keep the tile IDs in the
   template and refresh the Asset Browser to register the new tileset.
4. Select the tileset and open **Tile Collision…**. Set Solid to **Full Cell**.
   For Perforated, select **Generate from Alpha** with threshold 128 and
   zero simplification, then inspect the opening in the preview. The Slope
   template has one smooth triangular collider. Use **Preview vertices** to
   adjust it, or generate from alpha to see the stepped pixel outline. Set
   Decoration to **None** and click **Apply** to save the staged changes.
5. Create or open a project scene. Open **Tiles…**, choose the room tileset,
   and click **Add Tile Layer**. Paint with the brush or rectangle tool.
   Add a second layer for decoration and clear its **Collision** checkbox.
   Undo and redo one stroke, then save and reopen the scene.
6. Set the scene as the project startup scene and position its camera to
   frame the painted area. Use **Play** to check the layout. To test terrain
   response, add a dynamic body and let it fall onto the floor and slope.
   A body small enough to fit in the perforation can occupy the opening.
7. Save the project and use **File → Export Game…**. Move the whole export
   folder and launch its executable from another working directory.

See [tile-layer and collision settings](../../docs/topics/Scenes.md#native-project-tile-layers)
and [project export](../../docs/topics/Projects.md#export-a-runnable-game).
