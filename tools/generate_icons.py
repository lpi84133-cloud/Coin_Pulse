#!/usr/bin/env python3
"""Generate adaptive + legacy launcher icons from Icon.png."""
import os
from PIL import Image

SRC = "/Users/vic/Desktop/kotlin_android/Coin_Pulse/assets/Coin_Pulse_additional_assets/Icon.png"
RES = "/Users/vic/Desktop/kotlin_android/Coin_Pulse/app/src/main/res"

# density -> adaptive foreground size (108dp), legacy launcher size (48dp)
DENS = {
    "mdpi": (108, 48),
    "hdpi": (162, 72),
    "xhdpi": (216, 96),
    "xxhdpi": (324, 144),
    "xxxhdpi": (432, 192),
}

BG = (26, 20, 61)  # dark violet, matches art background


def main():
    icon = Image.open(SRC).convert("RGBA")
    for d, (adp, leg) in DENS.items():
        folder = os.path.join(RES, f"mipmap-{d}")
        os.makedirs(folder, exist_ok=True)

        # Adaptive foreground: transparent canvas, icon at ~64% centered (safe zone)
        fg = Image.new("RGBA", (adp, adp), (0, 0, 0, 0))
        inner = int(adp * 0.64)
        ic = icon.resize((inner, inner), Image.LANCZOS)
        off = (adp - inner) // 2
        fg.alpha_composite(ic, (off, off))
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"))

        # Legacy square + round: icon on solid bg
        base = Image.new("RGBA", (leg, leg), BG + (255,))
        ic2 = icon.resize((leg, leg), Image.LANCZOS)
        base.alpha_composite(ic2)
        base.convert("RGB").save(os.path.join(folder, "ic_launcher.png"))

        # Round: circular mask
        from PIL import ImageDraw
        mask = Image.new("L", (leg, leg), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, leg, leg), fill=255)
        rnd = Image.new("RGBA", (leg, leg), (0, 0, 0, 0))
        rnd.paste(base.convert("RGBA"), (0, 0), mask)
        rnd.save(os.path.join(folder, "ic_launcher_round.png"))
        print(f"{d}: adaptive {adp}, legacy {leg}")

    # Play Store 512 icon
    play = icon.resize((512, 512), Image.LANCZOS).convert("RGB")
    os.makedirs(os.path.join(RES, "..", "..", "..", "store"), exist_ok=True)
    play.save("/Users/vic/Desktop/kotlin_android/Coin_Pulse/store_icon_512.png")
    print("Done.")


if __name__ == "__main__":
    main()
