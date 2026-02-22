"""
MineX Branding Script
- Troca ícones
- Troca nome do app em strings.xml
- Substitui textos visíveis (about, labels)
- NÃO renomeia pastas, módulos ou package names (isso quebraria o Gradle)
"""
from PIL import Image
import os, re

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

    for root, dirs, files in os.walk(POJAV_DIR):
        for folder, size in MIPMAP_SIZES.items():
            if not root.endswith(folder):
                continue
            for fname in ["ic_launcher.png", "ic_launcher.webp"]:
                fp = os.path.join(root, fname)
                if os.path.exists(fp):
                    out = fp.replace(".webp", ".png")
                    icon.resize((size, size), Image.LANCZOS).save(out)
                    if fp.endswith(".webp") and fp != out:
                        os.remove(fp)

            for fname in ["ic_launcher_round.png", "ic_launcher_round.webp"]:
                fp = os.path.join(root, fname)
                if os.path.exists(fp):
                    out = fp.replace(".webp", ".png")
                    icon_round.resize((size, size), Image.LANCZOS).save(out)
                    if fp.endswith(".webp") and fp != out:
                        os.remove(fp)

            for fname in ["ic_launcher_foreground.png", "ic_launcher_foreground.webp"]:
                fp = os.path.join(root, fname)
                if os.path.exists(fp):
                    fg_size = int(size * 1.5)
                    out = fp.replace(".webp", ".png")
                    icon.resize((fg_size, fg_size), Image.LANCZOS).save(out)
                    if fp.endswith(".webp") and fp != out:
                        os.remove(fp)

            print(f"  ✓ {folder} ({size}px)")

def trocar_assets_launcher():
    print("📁 Trocando assets do launcher...")
    for root, dirs, files in os.walk(POJAV_DIR):
        for fname in files:
            fpath = os.path.join(root, fname)
            if fname == "pojavlauncher.png":
                img = Image.open(f"{ASSETS_DIR}/icon.png").resize((512, 512), Image.LANCZOS)
                img.save(fpath)
                print(f"  ✓ {fname}")
            elif fname == "pojavtext.png":
                img = Image.open(f"{ASSETS_DIR}/logo_banner.png").resize((512, 128), Image.LANCZOS)
                img.save(fpath)
                print(f"  ✓ {fname}")

def trocar_nome_strings():
    """Troca só o app_name e app_name_short no strings.xml — nada mais."""
    print("📝 Trocando nome do app em strings.xml...")
    count = 0
    for root, dirs, files in os.walk(POJAV_DIR):
        for fname in files:
            if fname != "strings.xml":
                continue
            fpath = os.path.join(root, fname)
            try:
                with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                original = content
                content = re.sub(
                    r'(<string name="app_name">)[^<]*(</string>)',
                    r'\1MineX\2', content
                )
                content = re.sub(
                    r'(<string name="app_name_short">)[^<]*(</string>)',
                    r'\1MineX\2', content
                )
                if content != original:
                    with open(fpath, "w", encoding="utf-8") as f:
                        f.write(content)
                    count += 1
            except Exception as e:
                print(f"  ⚠️ Erro em {fpath}: {e}")
    print(f"  ✓ {count} strings.xml atualizados")

def trocar_textos_visiveis():
    """
    Troca textos visíveis ao usuário (about, labels de UI).
    NÃO toca em: nomes de módulos, package names, paths de assets,
    nomes de classes, imports, build.gradle, settings.gradle.
    """
    print("📄 Trocando textos visíveis...")

    SAFE_FILES = {"about_en.txt", "about_zh.txt"}

    REPLACEMENTS = [
        ("PojavLauncher", "MineX"),
        ("Pojav Launcher", "MineX"),
        ("pojav launcher", "minex"),
    ]

    count = 0
    for root, dirs, files in os.walk(POJAV_DIR):
        for fname in files:
            if fname not in SAFE_FILES:
                continue
            fpath = os.path.join(root, fname)
            try:
                with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                original = content
                for old, new in REPLACEMENTS:
                    content = content.replace(old, new)
                if content != original:
                    with open(fpath, "w", encoding="utf-8") as f:
                        f.write(content)
                    count += 1
            except Exception:
                pass
    print(f"  ✓ {count} arquivos de texto atualizados")

if __name__ == "__main__":
    print("⛏ Aplicando branding MineX...\n")
    trocar_icones()
    trocar_assets_launcher()
    trocar_nome_strings()
    trocar_textos_visiveis()
    print("\n✅ Branding aplicado! Pronto para compilar.")
