Glass Connected Textures

For Minecraft 1.20.1 and Forge
Hides internal borders between adjacent glass blocks so connected windows appear as one continuous surface.

Main Features

Place the JAR in the mods folder and launch the game.
Client side only. No server installation is required.
Works with glass that is already placed in existing worlds.
Removing the mod restores the vanilla appearance.
Supports regular glass, tinted glass, all 16 stained glass colors, glass panes, and all 16 stained glass panes.
Different stained glass colors can connect to each other.
Preserves the original glass surface texture.
Single glass panes and exposed pane ends keep their original appearance.
Creates no additional generated files.

Requirements

Minecraft 1.20.1
Forge 47.4.10 or later within the Forge 47.x series
Java 17

The mod uses the Forge BakedModel and ModelData rendering path and is designed with Embeddium and Oculus compatibility in mind.

Installation

1. Install Minecraft 1.20.1 with Forge.
2. Place GlassConnectedTextures-1.20.1-1.0.0.jar in the mods folder.
3. Launch Minecraft.

To remove the mod, delete the JAR. The mod does not modify world data.

Supported Blocks

Glass
Tinted Glass
All 16 stained glass colors
Glass Pane
All 16 stained glass panes

Glass blocks connect with other glass blocks. Glass panes connect with other glass panes. Full glass blocks and glass panes are treated as separate groups.

Performance Optimizations

Neighbor states are checked only when block or chunk models are rebuilt.

Only the six directly adjacent positions are checked.
MutableBlockPos is reused to reduce temporary object allocation.
Connection states are packed into integers and reused.
ModelData is reused for each connection state.
Connection patterns are precomputed to reduce branching during rendering.
Quad UV and orientation analysis results are cached.
Common single Quad outputs are cached.
Only the relevant vanilla glass blocks are registered during startup.