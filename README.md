<div align="center">

# ⛏ MineX
### Minecraft Java Launcher for Android

[![Build MineX APK](https://github.com/masterotaku487-arch/MineX-/actions/workflows/build.yml/badge.svg)](https://github.com/masterotaku487-arch/MineX-/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Minecraft](https://img.shields.io/badge/Minecraft-Java%20Edition-orange)

</div>

---

## 📱 Sobre o MineX

**MineX** é um launcher que permite jogar **Minecraft: Java Edition** no Android, baseado no [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).

### ✨ Funcionalidades
- 🎮 Minecraft Java Edition no Android
- 📦 Suporte a mods, modpacks e shaders
- 🔧 Todas as versões do Minecraft
- ⚡ Java 8, 11, 17 e 21
- 🌍 Multiplayer com servidores Java Edition

### 📋 Requisitos
- Android 8.0+
- ARMv8 (arm64-v8a) recomendado
- 2GB+ de RAM (4GB+ recomendado)

---

## 🚀 Download

Acesse a aba [**Releases**](../../releases) e baixe o `MineX.apk` mais recente.

---

## ⚙️ Como o Build Funciona

A cada push na `main`, o GitHub Actions:

```
1. Clona o fonte do PojavLauncher (v3_openjdk)
2. Aplica os logos e nome MineX  ← apply_branding.py
3. Compila com Gradle (Java/Kotlin real)
4. Publica o APK em Releases
```

## 📁 Estrutura do Repo

```
MineX/
├── .github/workflows/build.yml  ← Pipeline automático
├── assets/                      ← Logos do MineX
│   ├── icon.png
│   ├── icon_round.png
│   └── logo_banner.png
├── apply_branding.py            ← Troca logos e nome
└── README.md
```

---

> ⚠️ MineX não é afiliado com Minecraft, Mojang ou Microsoft.  
> Baseado no [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) — GNU GPL v3.
