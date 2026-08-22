#!/usr/bin/env python3
"""Generate PNG/JPEG prototype resources from checked-in vector sources.

Requires Pillow and CairoSVG. The default hotseat set uses project-authored
universal icons for music, Bluetooth, navigation, video and the app drawer.
A limited set of exact application icons extracted from Snow is kept alongside
those defaults for optional manual substitution later.
"""

from __future__ import annotations

import argparse
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "theme" / "src" / "main" / "res" / "mipmap-mdpi-v4"
LOCAL_ICONS = ROOT / "design" / "icons"
SNOW_ICONS = ROOT / "third_party" / "snow" / "icons"
WHITE = "#FFFFFF"
BLACK = "#000000"
ACCENT = "#FF6B57"
SECONDARY = "#D7B7AA"
WARM_ORANGE = "#FF9F43"
WARM_BROWN = "#8A4F3D"
TOP_STRIP_HEIGHT = 64
TOP_STRIP_TOP = 55
TOP_STRIP_BOTTOM = TOP_STRIP_TOP + TOP_STRIP_HEIGHT - 1
SIDEBAR_WIDTH = 81
CONTENT_LEFT = 93
RADIO_WIDTH = 286
MUSIC_WIDTH = 690
DATE_WIDTH = 178
DATE_LEFT = CONTENT_LEFT + RADIO_WIDTH + MUSIC_WIDTH
CONTENT_RIGHT = DATE_LEFT + DATE_WIDTH
MAP_TOP = TOP_STRIP_BOTTOM + 1
MAP_WIDTH = 1154
MAP_HEIGHT = 583


def font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype("DejaVuSans.ttf", size=size)
    except OSError:
        return ImageFont.load_default()


def render_svg(path: Path, size: int = 160, colour: str | None = None) -> Image.Image:
    import cairosvg

    svg = path.read_text(encoding="utf-8")
    if colour is not None:
        svg = svg.replace("#FFFFFF", colour).replace("#ffffff", colour).replace("#FFFFFFFF", colour)
    png = cairosvg.svg2png(
        bytestring=svg.encode("utf-8"),
        output_width=size,
        output_height=size,
    )
    image = Image.open(BytesIO(png)).convert("RGBA")
    image.load()
    return image


def generic_music() -> Image.Image:
    return render_svg(LOCAL_ICONS / "music.svg")


def generic_bluetooth() -> Image.Image:
    return render_svg(LOCAL_ICONS / "bluetooth.svg")


def generic_navigation() -> Image.Image:
    return render_svg(LOCAL_ICONS / "navigation.svg")


def generic_video() -> Image.Image:
    return render_svg(LOCAL_ICONS / "video.svg")


def apps() -> Image.Image:
    return render_svg(LOCAL_ICONS / "apps.svg")


def exact_auxio() -> Image.Image:
    return render_svg(SNOW_ICONS / "auxio.svg")


def exact_bluetooth() -> Image.Image:
    return render_svg(SNOW_ICONS / "bluetooth.svg")


def exact_navigation() -> Image.Image:
    return render_svg(SNOW_ICONS / "organicmaps.svg")


def exact_video() -> Image.Image:
    return render_svg(SNOW_ICONS / "video.svg")


def previous(colour: str) -> Image.Image:
    return render_svg(LOCAL_ICONS / "previous.svg", colour=colour)


def next_icon(colour: str) -> Image.Image:
    return render_svg(LOCAL_ICONS / "next.svg", colour=colour)


def play(colour: str) -> Image.Image:
    return render_svg(LOCAL_ICONS / "play.svg", colour=colour)


def pause(colour: str) -> Image.Image:
    return render_svg(LOCAL_ICONS / "pause.svg", colour=colour)


def radio_previous(colour: str) -> Image.Image:
    image = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
    ImageDraw.Draw(image).line(
        ((98, 40), (62, 80), (98, 120)), fill=colour, width=14, joint="curve"
    )
    return image


def radio_next(colour: str) -> Image.Image:
    image = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
    ImageDraw.Draw(image).line(
        ((62, 40), (98, 80), (62, 120)), fill=colour, width=14, joint="curve"
    )
    return image


def paste_resized(base: Image.Image, overlay: Image.Image, box: tuple[int, int, int, int]) -> None:
    resized = overlay.resize((box[2] - box[0], box[3] - box[1]), Image.Resampling.LANCZOS)
    base.paste(resized, box[:2], resized)


def raster(name: str) -> Image.Image:
    image = Image.open(OUT / name).convert("RGBA")
    image.load()
    return image


def preview(*, existing_rasters: bool = False) -> Image.Image:
    image = Image.new("RGB", (1280, 720), BLACK)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 1279, 54), fill="#080706")
    draw.text((1210, 17), "12:22", fill=WHITE, font=font(18))

    # One continuous information strip. Conservative geometry leaves extra right-side reserve.
    draw.rectangle((CONTENT_LEFT, TOP_STRIP_TOP, CONTENT_RIGHT - 1, TOP_STRIP_BOTTOM), fill=BLACK, outline=WARM_BROWN, width=1)
    draw.line((CONTENT_LEFT + RADIO_WIDTH, TOP_STRIP_TOP, CONTENT_LEFT + RADIO_WIDTH, TOP_STRIP_BOTTOM), fill=WARM_BROWN, width=1)
    draw.line((DATE_LEFT, TOP_STRIP_TOP, DATE_LEFT, TOP_STRIP_BOTTOM), fill=WARM_BROWN, width=1)

    paste_resized(image, radio_previous(WHITE), (CONTENT_LEFT + 6, 63, CONTENT_LEFT + 60, 117))
    paste_resized(image, radio_next(WHITE), (CONTENT_LEFT + RADIO_WIDTH - 60, 63, CONTENT_LEFT + RADIO_WIDTH - 6, 117))
    draw.text((CONTENT_LEFT + 92, 75), "89.06", fill=WHITE, font=font(26))
    draw.text((CONTENT_LEFT + 166, 69), "FM", fill=ACCENT, font=font(12))

    music_left = CONTENT_LEFT + RADIO_WIDTH
    controls = (
        raster("btn_previous_icon.png") if existing_rasters else previous(WHITE),
        raster("btn_play_icon.png") if existing_rasters else play(WHITE),
        raster("btn_next_icon.png") if existing_rasters else next_icon(WHITE),
    )
    for icon, box in zip(controls, (
        (music_left + 484, 62, music_left + 540, 118),
        (music_left + 552, 62, music_left + 608, 118),
        (music_left + 620, 62, music_left + 676, 118),
    )):
        paste_resized(image, icon, box)
    draw.text((music_left + 18, 77), "Massive Attack – Teardrop   •", fill=WHITE, font=font(19))
    draw.text((DATE_LEFT + 41, 76), "22 AUG", fill=WHITE, font=font(22))

    draw.rectangle((CONTENT_LEFT, MAP_TOP, CONTENT_RIGHT - 1, 701), fill="#EEE8E4")
    roads = [
        [(CONTENT_LEFT, 650), (260, 520), (380, 570), (510, 420), (780, 390), (1020, 210), (CONTENT_RIGHT, 170)],
        [(170, MAP_TOP), (280, 230), (430, 230), (560, 350), (790, 520), (980, 460), (CONTENT_RIGHT, 620)],
        [(CONTENT_LEFT, 350), (380, 320), (470, 190), (700, 195), (910, 345), (CONTENT_RIGHT, 305)],
    ]
    for points in roads:
        draw.line(points, fill="#D3C2BA", width=16, joint="curve")
        draw.line(points, fill=WHITE, width=8, joint="curve")
    draw.line(roads[0], fill=ACCENT, width=8, joint="curve")
    for box in ((180, 145, 300, 210), (350, 130, 520, 202), (615, 152, 760, 210),
                (260, 285, 415, 370), (560, 265, 695, 341), (770, 295, 945, 365),
                (180, 450, 320, 545), (520, 505, 705, 585)):
        draw.rounded_rectangle(box, radius=6, fill="#C9A99B")
    draw.ellipse((713, 380, 757, 424), fill=WHITE, outline=ACCENT, width=5)
    draw.polygon(((735, 386), (747, 414), (735, 407), (723, 414)), fill=ACCENT)

    draw.rectangle((0, 55, SIDEBAR_WIDTH - 1, 719), fill=BLACK)
    hotseat = (
        [raster(name) for name in ("music.png", "bt.png", "navi.png", "video.png", "apps.png")]
        if existing_rasters
        else [generic_music(), generic_bluetooth(), generic_navigation(), generic_video(), apps()]
    )
    positions = ((10, 77, 70, 137), (10, 181, 70, 241), (10, 285, 70, 345), (10, 389, 70, 449), (4, 628, 76, 700))
    for icon, box in zip(hotseat, positions):
        paste_resized(image, icon, box)
    return image


def save_png(image: Image.Image, name: str, size: tuple[int, int] | None = None) -> None:
    if size is not None:
        image = image.resize(size, Image.Resampling.LANCZOS)
    image.save(OUT / name, format="PNG", optimize=True)


def write_previews(image: Image.Image) -> None:
    image.resize((614, 360), Image.Resampling.LANCZOS).save(
        OUT / "icon_local_theme_details_public_02.jpg", format="JPEG", quality=92, optimize=True
    )
    image.resize((400, 234), Image.Resampling.LANCZOS).save(
        OUT / "icon_local_theme_details_public_01.jpg", format="JPEG", quality=92, optimize=True
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--preview-only",
        action="store_true",
        help="refresh catalogue previews from checked-in rasters without CairoSVG",
    )
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.preview_only:
        write_previews(preview(existing_rasters=True))
        print("SUCCESS: refreshed catalogue previews from checked-in raster resources")
        return 0
    save_png(Image.new("RGB", (1280, 750), BLACK), "page_bg_01.png")
    save_png(Image.new("RGB", (1280, 750), BLACK), "apps_bg_01.png")
    for name, size in (("time_bg.png", (178, TOP_STRIP_HEIGHT)), ("radio_bg.png", (286, TOP_STRIP_HEIGHT)), ("media_bg.png", (690, TOP_STRIP_HEIGHT))):
        image = Image.new("RGB", size, BLACK)
        ImageDraw.Draw(image).rectangle((0, 0, size[0] - 1, size[1] - 1), outline=WARM_BROWN, width=1)
        save_png(image, name)
    save_png(Image.new("RGBA", (1, 1), (0, 0, 0, 0)), "media_img_bg.png")
    for name, maker, size in (
        ("music.png", generic_music, (60, 60)), ("bt.png", generic_bluetooth, (60, 60)),
        ("navi.png", generic_navigation, (60, 60)), ("video.png", generic_video, (60, 60)),
        ("apps.png", apps, (98, 98)),
        ("alt_music_auxio.png", exact_auxio, (60, 60)), ("alt_bt_snow.png", exact_bluetooth, (60, 60)),
        ("alt_navi_organicmaps.png", exact_navigation, (60, 60)), ("alt_video_snow.png", exact_video, (60, 60)),
    ):
        save_png(maker(), name, size)
    for stem, maker in (
        ("btn_previous_icon", previous), ("btn_play_icon", play), ("btn_next_icon", next_icon),
        ("btn_stop_icon", pause), ("btn_bt_pp_icon", play), ("btn_radio_pp_icon", play),
    ):
        save_png(maker(WHITE), f"{stem}.png")
        save_png(maker(ACCENT), f"{stem}_press.png")
    write_previews(preview())
    print("SUCCESS: generated compatibility resources from checked-in vector sources")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
