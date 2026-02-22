"""
MineX Branding Script
Aplica logos e nome MineX no fonte do PojavLauncher antes de compilar.
"""
from PIL import Image
import os, shutil, re

POJAV_DIR  = "PojavLauncher"
ASSETS_DIR = "MineX-assets/assets"

MIPMAP_SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

def trocar_icones():
    print("🎨 Trocando ícones...")
    icon       = Image.open(f"{ASSETS_DIR}/icon.png").convert("RGBA")
    icon_round = Image.open(f"{ASSETS_DIR}/icon_round.png").convert("RGBA")

    # Procurar pasta res dentro do projeto Android
    res_dirs = []
    for root, dirs, files in os.walk(POJAV_DIR):
        if root.endswith("/res") or root.endswith("\\res"):
            res_dirs.append(root)

    for res_base in res_dirs:
        for folder, size in MIPMAP_SIZES.items():
            path = os.path.join(res_base, folder)
            if not os.path.exists(path):
                continue

            # ic_launcher
            for fname in ["ic_launcher.png", "ic_launcher.webp"]:
                fp = os.path.join(path, fname)
                if os.path.exists(fp):
                    icon.resize((size, size), Image.LANCZOS).save(
                        fp.replace(".webp", ".png")
                    )
                    if fp.endswith(".webp"):
                        os.remove(fp)

            # ic_launcher_round
            for fname in ["ic_launcher_round.png", "ic_launcher_round.webp"]:
                fp = os.path.join(path, fname)
                if os.path.exists(fp):
                    icon_round.resize((size, size), Image.LANCZOS).save(
                        fp.replace(".webp", ".png")
                    )
                    if fp.endswith(".webp"):
                        os.remove(fp)

            # foreground (adaptive icon)
            for fname in ["ic_launcher_foreground.png", "ic_launcher_foreground.webp"]:
                fp = os.path.join(path, fname)
                if os.path.exists(fp):
                    fg_size = int(size * 1.5)
                    icon.resize((fg_size, fg_size), Image.LANCZOS).save(
                        fp.replace(".webp", ".png")
                    )
                    if fp.endswith(".webp"):
                        os.remove(fp)

            print(f"  ✓ {folder} ({size}px)")

def trocar_assets_launcher():
    print("📁 Trocando assets do launcher...")
    # Procurar assets dentro do projeto
    for root, dirs, files in os.walk(POJAV_DIR):
        for fname in files:
            fpath = os.path.join(root, fname)
            if fname == "pojavlauncher.png":
                img = Image.open(f"{ASSETS_DIR}/icon.png").resize((512, 512), Image.LANCZOS)
                img.save(fpath)
                print(f"  ✓ {fpath}")
            elif fname == "pojavtext.png":
                img = Image.open(f"{ASSETS_DIR}/logo_banner.png").resize((512, 128), Image.LANCZOS)
                img.save(fpath)
                print(f"  ✓ {fpath}")

def trocar_nome_app():
    print("📝 Trocando nome do app para MineX...")
    # Procurar strings.xml
    for root, dirs, files in os.walk(POJAV_DIR):
        for fname in files:
            if fname == "strings.xml":
                fpath = os.path.join(root, fname)
                with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()

                # Substituir nome do app
                content = re.sub(
                    r'(<string name="app_name">)[^<]*(</string>)',
                    r'\1MineX\2',
                    content
                )
                content = re.sub(
                    r'(<string name="app_name_short">)[^<]*(</string>)',
                    r'\1MineX\2',
                    content
                )

                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"  ✓ {fpath}")

if __name__ == "__main__":
    print("⛏ Aplicando branding MineX...\n")
    trocar_icones()
    trocar_assets_launcher()
    trocar_nome_app()
    print("\n✅ Branding aplicado! Pronto para compilar.")
