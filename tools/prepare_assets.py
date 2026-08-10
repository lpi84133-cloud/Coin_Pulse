#!/usr/bin/env python3
"""Slice *_Set sheets into individual sprites and copy needed assets into app.

Segmentation: objects sit on a transparent sheet separated by horizontal gaps.
We sum alpha per column, find contiguous non-empty column runs, and crop each
run (also trimming vertically) into a standalone sprite.
"""
import os
from PIL import Image

SRC = "/Users/vic/Desktop/kotlin_android/Coin_Pulse/assets"
GAME = os.path.join(SRC, "Coin_Pulse_gameplay_assets")
ADD = os.path.join(SRC, "Coin_Pulse_additional_assets")
SND = os.path.join(SRC, "Coin_Pulse_sounds_assets")

OUT_ASSETS = "/Users/vic/Desktop/kotlin_android/Coin_Pulse/app/src/main/assets"
OUT_SPRITES = os.path.join(OUT_ASSETS, "sprites")
OUT_BG = os.path.join(OUT_ASSETS, "bg")
OUT_SND = "/Users/vic/Desktop/kotlin_android/Coin_Pulse/app/src/main/res/raw"

ALPHA_THRESHOLD = 24     # treat pixels with alpha above this as content
MIN_RUN = 12             # ignore column runs narrower than this (noise)
MAX_OBJ = 256            # max sprite dimension (downscale)

# Sheets to slice: filename -> output folder name
SHEETS = {
    "Small_Energy_Enemy_Set_asset.webp": "enemy_small",
    "Medium_Energy_Enemy_Set_asset.webp": "enemy_medium",
    "Fast_Energy_Enemy_Set_asset.webp": "enemy_fast",
    "Golden_Coin_Set_asset.webp": "coin",
    "Magic_Star_Set_asset.webp": "star",
    "Golden_Bell_Set_asset.webp": "bell",
    "Fantasy_Fruit_Set_asset.webp": "fruit",
    "Purple_Crystal_Set_asset.webp": "crystal",
    "Number_Symbol_Set_asset.webp": "number",
    "Power_Up_Set_asset.webp": "powerup",
}

# Single objects: filename -> output basename
SINGLES = {
    "Main_Golden_Coin_asset.webp": "player_coin",
    "Elite_Energy_Guardian_asset.webp": "guardian_elite",
    "Final_Guardian_asset.webp": "guardian_final",
    "Enemy_Spawn_Portal_asset.webp": "portal",
}

# Backgrounds: filename -> output basename (max width 1280)
BGS = {
    "Main_Energy_Arena_Background_asset.webp": "arena_main",
    "Deep_Energy_Zone_Background_asset.webp": "arena_deep",
    "Final_Energy_Zone_Background_asset.webp": "arena_final",
}

# Sounds copied to res/raw with lowercase names (res names: a-z0-9_)
SOUNDS = {
    "Button_Click_asset.mp3": "sfx_button.mp3",
    "Pulse_Activation_asset.mp3": "sfx_pulse.mp3",
    "Chain_Reaction_asset.mp3": "sfx_chain.mp3",
    "Coin_Collect_asset.mp3": "sfx_coin.mp3",
    "Crystal_Activation_asset.mp3": "sfx_crystal.mp3",
    "Reward_Collect_asset.mp3": "sfx_reward.mp3",
    "Level_Complete_asset.mp3": "sfx_victory.mp3",
    "Defeat_Sound_asset.mp3": "sfx_defeat.mp3",
    "Menu_Open_asset.mp3": "sfx_menu_open.mp3",
    "Pulse_Upgrade_asset.mp3": "sfx_upgrade.mp3",
}


def content_columns(img):
    a = img.split()[-1]
    w, h = img.size
    px = a.load()
    cols = []
    for x in range(w):
        s = 0
        for y in range(0, h, 3):  # sample every 3rd row for speed
            if px[x, y] > ALPHA_THRESHOLD:
                s += 1
        cols.append(s)
    return cols


def runs_from_cols(cols):
    runs = []
    start = None
    for x, v in enumerate(cols):
        if v > 0 and start is None:
            start = x
        elif v == 0 and start is not None:
            if x - start >= MIN_RUN:
                runs.append((start, x))
            start = None
    if start is not None and len(cols) - start >= MIN_RUN:
        runs.append((start, len(cols)))
    return runs


def trim_bbox(img):
    bbox = img.split()[-1].getbbox()
    return img.crop(bbox) if bbox else img


def downscale(img, maxd):
    w, h = img.size
    if max(w, h) <= maxd:
        return img
    scale = maxd / max(w, h)
    return img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)


def slice_sheet(path, outdir, maxd=MAX_OBJ):
    img = Image.open(path).convert("RGBA")
    cols = content_columns(img)
    runs = runs_from_cols(cols)
    os.makedirs(outdir, exist_ok=True)
    n = 0
    for (x0, x1) in runs:
        sub = img.crop((x0, 0, x1, img.height))
        sub = trim_bbox(sub)
        if sub.width < MIN_RUN or sub.height < MIN_RUN:
            continue
        sub = downscale(sub, maxd)
        sub.save(os.path.join(outdir, f"{n}.png"))
        n += 1
    print(f"  {os.path.basename(path)} -> {n} sprites")
    return n


def save_single(path, outdir, name, maxd):
    img = Image.open(path).convert("RGBA")
    img = trim_bbox(img)
    img = downscale(img, maxd)
    os.makedirs(outdir, exist_ok=True)
    img.save(os.path.join(outdir, f"{name}.png"))
    print(f"  single {name}: {img.size}")


def main():
    os.makedirs(OUT_SPRITES, exist_ok=True)
    os.makedirs(OUT_BG, exist_ok=True)
    os.makedirs(OUT_SND, exist_ok=True)

    print("Slicing sheets:")
    for fn, name in SHEETS.items():
        p = os.path.join(GAME, fn)
        if os.path.exists(p):
            slice_sheet(p, os.path.join(OUT_SPRITES, name))

    print("Singles:")
    for fn, name in SINGLES.items():
        p = os.path.join(GAME, fn)
        if os.path.exists(p):
            save_single(p, OUT_SPRITES, name, 256)

    print("Backgrounds:")
    for fn, name in BGS.items():
        p = os.path.join(GAME, fn)
        if os.path.exists(p):
            img = Image.open(p).convert("RGB")
            img = downscale(img, 1280)
            img.save(os.path.join(OUT_BG, f"{name}.jpg"), quality=86)
            print(f"  bg {name}: {img.size}")

    # Game name logo (keep alpha)
    logo = os.path.join(ADD, "Game_Name.webp")
    if os.path.exists(logo):
        img = Image.open(logo).convert("RGBA")
        img = downscale(trim_bbox(img), 512)
        img.save(os.path.join(OUT_SPRITES, "logo.png"))
        print(f"  logo: {img.size}")

    print("Sounds:")
    import shutil
    for fn, name in SOUNDS.items():
        p = os.path.join(SND, fn)
        if os.path.exists(p):
            shutil.copyfile(p, os.path.join(OUT_SND, name))
            print(f"  sfx {name}")

    print("Done.")


if __name__ == "__main__":
    main()
