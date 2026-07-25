"""Regenerate the non-kinetic construction-port panels.

The kinetic panels are the visual source of truth for this block.  Every
generated panel starts from the corresponding kinetic input/output texture, so
the worn brown-brass frame, dark recess, corner bolts and lighting direction
remain exactly in the same family.  The port type is represented by a real
mechanical fitting rather than a pictogram:

* inputs have a deep, dark receiving throat on the left;
* outputs have a raised, bright delivery collar on the right.

That large recessed-versus-protruding construction makes direction readable at
a glance without turning a block face into an icon or a warning sign.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw


SIZE = 32
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/examplemod/textures/block"

# Palette sampled from the retained kinetic-panel textures.  Keeping this
# compact is important: it stops the added ports from looking smoother or more
# saturated than the original mechanical-factory material.
INK = (41, 26, 16, 255)
BLACK_BROWN = (48, 30, 18, 255)
DEEP_BROWN = (58, 36, 21, 255)
BROWN = (64, 44, 27, 255)
LEATHER = (95, 71, 44, 255)
BRASS_DARK = (79, 50, 28, 255)
BRASS = (114, 71, 49, 255)
BRASS_MID = (126, 91, 52, 255)
BRASS_LIGHT = (158, 105, 71, 255)
BRASS_PALE = (206, 160, 90, 255)
BRASS_HOT = (247, 203, 108, 255)
CREAM = (255, 235, 140, 255)
STEEL_DARK = (53, 52, 56, 255)
STEEL = (89, 88, 88, 255)
STEEL_LIGHT = (117, 113, 113, 255)

# Muted, material-like accents.  These are deliberately used as lenses,
# oxidisation and liquid behind fittings, never as standalone flat icons.
ITEM_DARK = (53, 75, 42, 255)
ITEM = (83, 116, 61, 255)
ITEM_LIGHT = (139, 169, 91, 255)
FLUID_DARK = (34, 77, 91, 255)
FLUID = (61, 126, 145, 255)
FLUID_LIGHT = (123, 184, 193, 255)
REDSTONE_DARK = (104, 39, 31, 255)
REDSTONE = (171, 61, 43, 255)
REDSTONE_LIGHT = (235, 117, 68, 255)
AMBER = (218, 151, 58, 255)
AMBER_LIGHT = (255, 212, 111, 255)


def rect(draw: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int,
         color: tuple[int, int, int, int]) -> None:
    draw.rectangle((x0, y0, x1, y1), fill=color)


def pixel(image: Image.Image, x: int, y: int,
          color: tuple[int, int, int, int]) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        image.putpixel((x, y), color)


def load_reference(incoming: bool) -> Image.Image:
    """Load the untouched stress-panel casing for a direction state."""
    name = "construction_block_kinetic_input.png" if incoming else "construction_block_kinetic_output.png"
    with Image.open(OUT / name) as reference:
        return reference.convert("RGBA")


def bolt(image: Image.Image, x: int, y: int) -> None:
    """A tiny warm-metal bolt that matches the reference panel highlights."""
    pixel(image, x - 1, y - 1, BRASS_DARK)
    pixel(image, x, y - 1, BRASS_PALE)
    pixel(image, x - 1, y, BRASS_PALE)
    pixel(image, x, y, BRASS_HOT)
    pixel(image, x + 1, y, BRASS_LIGHT)
    pixel(image, x, y + 1, BRASS)
    pixel(image, x + 1, y + 1, BLACK_BROWN)


def cavity(image: Image.Image, incoming: bool) -> ImageDraw.ImageDraw:
    """Build the common socket with a clearly directional piece of hardware.

    Input is physically inset: a light left lip leads into a wide shadowed
    throat.  Output reverses the value structure: its core sits proud and has
    a large bright right-side collar.  The source kinetic casing remains intact
    outside this 16px module, preserving its original silhouette.
    """
    draw = ImageDraw.Draw(image)
    rect(draw, 8, 8, 23, 23, BROWN)
    rect(draw, 9, 9, 22, 22, BRASS_DARK)
    rect(draw, 10, 10, 21, 21, INK)
    rect(draw, 11, 11, 20, 20, STEEL_DARK)

    # Top-left lighting and lower-right occlusion, shared by both modes.
    rect(draw, 10, 10, 21, 10, BRASS_LIGHT)
    rect(draw, 10, 10, 10, 21, BRASS_MID)
    rect(draw, 10, 21, 21, 21, BLACK_BROWN)
    rect(draw, 21, 10, 21, 21, BLACK_BROWN)

    if incoming:
        # A deep receiving mouth: its bright outer lip is on the left and all
        # accent material is one step back in the socket.
        rect(draw, 11, 12, 13, 19, BRASS)
        rect(draw, 11, 12, 11, 19, BRASS_HOT)
        rect(draw, 12, 13, 13, 18, BRASS_LIGHT)
        rect(draw, 14, 12, 19, 19, BLACK_BROWN)
        rect(draw, 15, 13, 19, 18, INK)
        rect(draw, 20, 12, 20, 19, STEEL_DARK)
        pixel(image, 12, 12, CREAM)
        pixel(image, 12, 19, BRASS_DARK)
    else:
        # A raised delivery collar: broad bright metal on the right throws the
        # socket outward, while the left edge stays recessed and dark.
        rect(draw, 11, 12, 12, 19, STEEL_DARK)
        rect(draw, 13, 12, 18, 19, INK)
        rect(draw, 14, 13, 18, 18, BLACK_BROWN)
        rect(draw, 19, 11, 21, 20, BRASS)
        rect(draw, 20, 12, 21, 19, BRASS_LIGHT)
        rect(draw, 20, 12, 20, 19, BRASS_HOT)
        pixel(image, 20, 12, CREAM)
        pixel(image, 21, 19, BRASS_DARK)

    return draw


def direction_latches(image: Image.Image, incoming: bool,
                      accent: tuple[int, int, int, int],
                      accent_light: tuple[int, int, int, int]) -> None:
    """Three substantial side latches reinforce orientation without an arrow."""
    draw = ImageDraw.Draw(image)
    if incoming:
        for y in (12, 16, 20):
            rect(draw, 7, y, 8, y + 1, BRASS_DARK)
            pixel(image, 8, y, BRASS_HOT)
            pixel(image, 9, y, accent_light)
            pixel(image, 9, y + 1, accent)
            pixel(image, 23, y, STEEL_DARK)
    else:
        for y in (12, 16, 20):
            pixel(image, 8, y, STEEL_DARK)
            pixel(image, 9, y, BRASS_DARK)
            pixel(image, 22, y, accent)
            pixel(image, 22, y + 1, accent_light)
            rect(draw, 23, y, 24, y + 1, BRASS_LIGHT)
            pixel(image, 23, y, BRASS_HOT)


def item_panel(incoming: bool) -> Image.Image:
    image = load_reference(incoming)
    draw = cavity(image, incoming)
    direction_latches(image, incoming, ITEM, ITEM_LIGHT)

    # Shuttered item throat: rails and chunky slats suggest a belt feed rather
    # than a chest, hopper or arrow symbol.
    if incoming:
        rect(draw, 15, 13, 18, 18, ITEM_DARK)
        for y in (13, 15, 17):
            rect(draw, 15, y, 18, y, ITEM)
            pixel(image, 15, y, ITEM_LIGHT)
            pixel(image, 18, y, LEATHER)
        rect(draw, 18, 14, 18, 17, BLACK_BROWN)
    else:
        rect(draw, 14, 13, 18, 18, ITEM_DARK)
        for y in (13, 15, 17):
            rect(draw, 14, y, 18, y, ITEM)
            pixel(image, 14, y, LEATHER)
            pixel(image, 18, y, ITEM_LIGHT)
        rect(draw, 14, 14, 14, 17, BLACK_BROWN)
    bolt(image, 14 if incoming else 19, 11)
    bolt(image, 14 if incoming else 19, 20)
    return image


def fluid_panel(incoming: bool) -> Image.Image:
    image = load_reference(incoming)
    draw = cavity(image, incoming)
    direction_latches(image, incoming, FLUID, FLUID_LIGHT)

    # A machined pipe coupling with liquid visible through a square of glass.
    # Input keeps it buried in the throat; output moves the gleam to the proud
    # collar, so its state stays obvious even from a short distance.
    center_x = 17 if incoming else 16
    rect(draw, center_x - 3, 13, center_x + 3, 18, BRASS_DARK)
    rect(draw, center_x - 2, 12, center_x + 2, 19, BRASS_DARK)
    rect(draw, center_x - 2, 13, center_x + 2, 18, FLUID_DARK)
    rect(draw, center_x - 1, 13, center_x + 2, 17, FLUID)
    rect(draw, center_x, 13, center_x + 2, 14, FLUID_LIGHT)
    rect(draw, center_x - 1, 18, center_x + 2, 18, STEEL_DARK)
    pixel(image, center_x - 2, 13, BRASS_PALE if incoming else BRASS_LIGHT)
    pixel(image, center_x + 2, 17, BRASS_LIGHT if incoming else CREAM)
    bolt(image, 14 if incoming else 19, 11)
    bolt(image, 14 if incoming else 19, 20)
    return image


def redstone_panel(incoming: bool) -> Image.Image:
    image = load_reference(incoming)
    draw = cavity(image, incoming)
    direction_latches(image, incoming, REDSTONE, REDSTONE_LIGHT)

    # A faceted ruby contact below a protective steel mesh.  It reads as a
    # redstone component, but never becomes a dust/torch pictogram.
    center_x = 17 if incoming else 16
    rect(draw, center_x - 3, 13, center_x + 3, 18, BRASS_DARK)
    rect(draw, center_x - 2, 12, center_x + 2, 19, BLACK_BROWN)
    rect(draw, center_x - 2, 13, center_x + 2, 18, REDSTONE_DARK)
    rect(draw, center_x - 1, 13, center_x + 2, 17, REDSTONE)
    rect(draw, center_x, 13, center_x + 2, 14, REDSTONE_LIGHT)
    rect(draw, center_x - 1, 18, center_x + 2, 18, DEEP_BROWN)
    pixel(image, center_x - 2, 13, BRASS_PALE)
    pixel(image, center_x + 2, 17, REDSTONE_LIGHT if not incoming else BRASS_LIGHT)
    bolt(image, 14 if incoming else 19, 11)
    bolt(image, 14 if incoming else 19, 20)
    return image


def unload_panel() -> Image.Image:
    # UNLOAD is a control state rather than a directional port.  Use a retained
    # kinetic-output casing, but fit it with a closed shutter and amber latch.
    image = load_reference(False)
    draw = ImageDraw.Draw(image)
    rect(draw, 8, 8, 23, 23, BROWN)
    rect(draw, 9, 9, 22, 22, BRASS_DARK)
    rect(draw, 10, 10, 21, 21, INK)
    rect(draw, 11, 11, 20, 20, STEEL_DARK)
    rect(draw, 11, 11, 20, 11, STEEL_LIGHT)
    rect(draw, 11, 20, 20, 20, BLACK_BROWN)
    rect(draw, 11, 11, 11, 20, STEEL_LIGHT)
    rect(draw, 20, 11, 20, 20, BLACK_BROWN)
    for y in (13, 16, 19):
        rect(draw, 12, y, 19, y, STEEL)
        pixel(image, 12, y, STEEL_LIGHT)
        pixel(image, 19, y, BLACK_BROWN)
    rect(draw, 15, 14, 16, 18, AMBER)
    pixel(image, 15, 14, AMBER_LIGHT)
    pixel(image, 16, 18, BRASS_DARK)
    for x, y in ((11, 11), (20, 11), (11, 20), (20, 20)):
        bolt(image, x, y)
    return image


def write_preview(images: list[tuple[str, Image.Image]]) -> None:
    """Build-only nearest-neighbour review sheet; never packed into the mod."""
    scale = 6
    preview = Image.new("RGBA", (len(images) * SIZE * scale, SIZE * scale), (36, 27, 19, 255))
    for index, (_, image) in enumerate(images):
        enlarged = image.resize((SIZE * scale, SIZE * scale), Image.Resampling.NEAREST)
        preview.alpha_composite(enlarged, (index * SIZE * scale, 0))
    preview_path = ROOT / "build/port-panels-preview.png"
    preview_path.parent.mkdir(parents=True, exist_ok=True)
    preview.save(preview_path)
    print(f"wrote {preview_path}")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    images = [
        ("kinetic input reference", load_reference(True)),
        ("kinetic output reference", load_reference(False)),
        ("construction_block_item_input.png", item_panel(True)),
        ("construction_block_item_output.png", item_panel(False)),
        ("construction_block_fluid_input.png", fluid_panel(True)),
        ("construction_block_fluid_output.png", fluid_panel(False)),
        ("construction_block_redstone_input.png", redstone_panel(True)),
        ("construction_block_redstone_output.png", redstone_panel(False)),
        ("construction_block_unload.png", unload_panel()),
    ]
    for name, image in images[2:]:
        image.save(OUT / name)
        print(f"wrote {name}")
    if "--preview" in sys.argv:
        write_preview(images)


if __name__ == "__main__":
    main()
