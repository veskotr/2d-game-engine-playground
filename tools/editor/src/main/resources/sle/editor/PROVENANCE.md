# Editor resource provenance

Copied from `docs/design/editor/SLE_Starter_Assets_Handjet_v1 (1).zip`, root `SLE_Starter_Assets_v1/`.
Archive SHA-256: `365b6ffe9cb23c5322a3380c0e345f64d8dc16871f35620d33dd58aeb2196ffb`.

SLE icons and mascot are the supplied approved SLE graphics. SVG originals and
both 16/24px themed PNG families are retained; Compose uses PNG painters.
Only the consumed 256px app icon is included. No runtime textures are copied.

Command, tool and notice icons (27 keys such as `tool_move`, `undo`, `play`, `info`) were added
from the supplied `SLE_Editor_Icons_Expanded.zip` (SHA-256
`e2e64f877f3ba5ba4304c31808532d81be5e5c83c2d254fde9b6e22890865d44`), in the same SVG and
16/24px light/dark PNG layout. Its 20 original icons are byte-identical to the starter set.
Handjet is Copyright The Handjet Project Authors under SIL OFL 1.1;
see licenses/Handjet-OFL.txt. Source: https://fonts.google.com/specimen/Handjet.
Handjet Medium/SemiBold are used only for branding (the empty-project state);
Regular/Bold are retained from the supplied archive but unused. The user
requested Roboto for headings and body text during session 2.

Roboto Regular/Medium/Bold: official Google Fonts `googlefonts/roboto-2`,
`main/src/hinted/Roboto-{Regular,Medium,Bold}.ttf`, downloaded 2026-11-16.
Source: https://github.com/googlefonts/roboto-2 (the archived classic release).
Apache License 2.0, full text in `licenses/Roboto-APACHE-2.0.txt`.

SHA-256 of bundled Roboto resources:

```text
61f89f8db49261c2f6106e8dccc35df7b2f7ed909020db40a3fc905e95f99334  Roboto-Bold.ttf
2879a5ecb7fbfa13a7fc3e2cdd7fecbf73aa45e91b541dfdfa2c442eed0aac21  Roboto-Medium.ttf
56a45233d29f11b4dfb86d248e921939d115778f87325e7ae8cc108383d6664d  Roboto-Regular.ttf
```
