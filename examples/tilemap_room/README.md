# Tilemap room

Open `Room.sleproject` in the SLE Editor and open **Room.scene.json** from
the Asset Browser. The room has a terrain layer with solid, sloped and
perforated tiles, plus a separate decoration layer. Press **T** or click
**Tile Palette** in the Scene toolbar to paint more cells. Use **Play** to see the startup camera's view.

## Make a room from the PNG

1. Create a project. In its Asset Browser, click **Import…** and select
   [Room.png](Assets/Room.png). Select the imported PNG, set **Texture
   filter** to **Nearest**, and click **Open spritesheet…**.
2. In Grid mode, enter `16, 16` for cell size and `0, 0` for both margins
   and spacing. Click **Add grid regions**. The editor names the four
   regions Slice 1–4. From left to right they correspond to Solid, Slope,
   Perforated and Decoration.
3. Optionally rename the regions to Solid, Slope, Perforated and Decoration
   in Manual rectangle mode; tiles take their names from the regions. Back
   in Grid mode, click **Create Tileset…** and accept `Room.tileset.json`.
   The editor writes one tile per cell, next to the PNG. (You can also start
   from **New Tileset…** in the PNG's details or the Assets toolbar.)
4. The Tileset Editor opens on creation. Double-click the tileset in Assets
   to open it again. Set Solid to **Full Cell**. For Perforated, choose
   **From Alpha** with threshold 128 and zero simplification, then inspect the
   opening in the preview. For Slope, choose **From Alpha** to generate
   the stepped pixel outline and drag the handles to adjust it. The
   bundled room uses a smooth triangular collider for Slope. Set Decoration
   to **None** and click
   **Save** to write the staged changes.
5. Create or open a project scene. Choose **Create → Tile Layer → Room**.
   The editor adds the layer and opens the Tile Palette beside Details.
   Paint with the brush or rectangle tool.
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
