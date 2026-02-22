<div align="center">

# ⛏ MineX
### Minecraft Java Launcher for Android

<img src="app-files/assets/pojavlauncher.png" width="120" height="120" style="border-radius:50%"/>

[![Build APK](https://github.com/SEU_USUARIO/MineX/actions/workflows/build.yml/badge.svg)](https://github.com/SEU_USUARIO/MineX/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Platform](https://img.shields.io/badge/Platform-Android-green)
![Minecraft](https://img.shields.io/badge/Minecraft-Java%20Edition-orange)

</div>

---

## 📱 Sobre o MineX

**MineX** é um launcher customizado baseado no PojavLauncher que permite jogar **Minecraft: Java Edition** diretamente no seu dispositivo Android, sem precisar de PC!

### ✨ Funcionalidades
- 🎮 Jogue Minecraft Java Edition no Android
- 📦 Suporte a mods, modpacks e shaders
- 🔧 Compatível com múltiplas versões do Minecraft (1.0 até a mais recente)
- ⚡ Suporte a Java 8, 11, 17 e 21
- 🌍 Multiplayer com servidores Java Edition
- 🎨 Interface customizada MineX

### 📋 Requisitos
- Android 8.0 (Oreo) ou superior
- ARMv8 (arm64-v8a) recomendado
- Mínimo 2GB de RAM (4GB+ recomendado)
- Conta Minecraft: Java Edition (original ou offline)

---

## 🚀 Como Instalar

### Via GitHub Releases (recomendado)
1. Vá em [Releases](https://github.com/SEU_USUARIO/MineX/releases)
2. Baixe o arquivo `MineX-vX.X.apk`
3. Habilite "Fontes desconhecidas" no Android
4. Instale o APK

### Via GitHub Actions
1. Vá em [Actions](https://github.com/SEU_USUARIO/MineX/actions)
2. Clique no último build bem-sucedido
3. Baixe o artifact `MineX-APK`

---

## 🔨 Como Compilar

O projeto usa GitHub Actions para compilação automática. A cada push na branch `main`, um APK é gerado automaticamente.

### Compilar localmente
```bash
# Clone o repositório
git clone https://github.com/SEU_USUARIO/MineX.git
cd MineX

# Execute o script de build
chmod +x build.sh
./build.sh
```

---

## 📁 Estrutura do Projeto

```
MineX/
├── .github/
│   └── workflows/
│       └── build.yml       # GitHub Actions - Build automático
├── app-files/              # Arquivos do APK
│   ├── assets/             # Assets do launcher
│   ├── res/                # Recursos (ícones, layouts)
│   ├── lib/                # Bibliotecas nativas
│   └── AndroidManifest.xml
├── build.sh                # Script de build local
└── README.md
```

---

## ⚙️ GitHub Actions

O workflow `.github/workflows/build.yml` faz automaticamente:
1. 📥 Checkout do código
2. 🔧 Setup do ambiente Python
3. 📦 Empacota os arquivos em APK
4. 🔐 Assina o APK com certificado de debug
5. 📤 Publica como artifact e cria Release

---

## 📄 Licença

Este projeto é licenciado sob **GNU GPL v3** - veja o arquivo [LICENSE](LICENSE) para detalhes.

Baseado no [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) por Tran Khanh Duy.

> ⚠️ MineX não é afiliado com Minecraft, Mojang ou Microsoft.

---

<div align="center">

Feito com ❤️ | ⛏ MineX Team

</div>
