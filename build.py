#!/usr/bin/env python3
"""
MineX APK Builder
Empacota e assina o APK automaticamente.
"""

import zipfile, os, hashlib, base64, datetime
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.serialization import pkcs7

APP_FILES = "app-files"
OUTPUT_DIR = "output"
OUTPUT_APK = os.path.join(OUTPUT_DIR, "MineX.apk")

def gerar_certificado():
    print("🔐 Gerando certificado de assinatura...")
    private_key = rsa.generate_private_key(
        public_exponent=65537, key_size=2048, backend=default_backend()
    )
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "MineX"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "MineX Team"),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(private_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.datetime.now(datetime.timezone.utc))
        .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=10000))
        .sign(private_key, hashes.SHA256(), default_backend())
    )
    return private_key, cert

def calcular_manifest(zin):
    print("📋 Calculando MANIFEST.MF...")
    lines = ["Manifest-Version: 1.0\r\nCreated-By: MineX Builder\r\n\r\n"]
    for name in sorted(zin.namelist()):
        if name.startswith("META-INF/"):
            continue
        data = zin.read(name)
        digest = base64.b64encode(hashlib.sha256(data).digest()).decode()
        lines.append(f"Name: {name}\r\nSHA-256-Digest: {digest}\r\n\r\n")
    return "".join(lines).encode()

def calcular_sf(manifest_content):
    print("📄 Calculando CERT.SF...")
    main_digest = base64.b64encode(hashlib.sha256(manifest_content).digest()).decode()
    lines = [f"Signature-Version: 1.0\r\nCreated-By: MineX Builder\r\nSHA-256-Digest-Manifest: {main_digest}\r\n\r\n"]
    manifest_str = manifest_content.decode()
    sections = manifest_str.split("\r\n\r\n")
    for section in sections[1:]:
        if section.strip():
            section_bytes = (section + "\r\n\r\n").encode()
            digest = base64.b64encode(hashlib.sha256(section_bytes).digest()).decode()
            first_line = section.split("\r\n")[0]
            name = first_line.replace("Name: ", "")
            lines.append(f"Name: {name}\r\nSHA-256-Digest: {digest}\r\n\r\n")
    return "".join(lines).encode()

def build():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    tmp_zip = os.path.join(OUTPUT_DIR, "_tmp_unsigned.zip")

    # 1. Empacotar arquivos
    print(f"📦 Empacotando {APP_FILES}/ ...")
    file_count = 0
    with zipfile.ZipFile(tmp_zip, "w", zipfile.ZIP_DEFLATED, allowZip64=False) as zout:
        for root, dirs, files in os.walk(APP_FILES):
            # Excluir META-INF antiga
            dirs[:] = [d for d in dirs if d != "META-INF"]
            for file in files:
                filepath = os.path.join(root, file)
                arcname = os.path.relpath(filepath, APP_FILES)
                zout.write(filepath, arcname)
                file_count += 1
    print(f"  ✓ {file_count} arquivos empacotados")

    # 2. Gerar certificado
    private_key, cert = gerar_certificado()
    cert_der = cert.public_bytes(serialization.Encoding.DER)

    # 3. Calcular hashes
    with zipfile.ZipFile(tmp_zip, "r") as zin:
        manifest_content = calcular_manifest(zin)
        sf_content = calcular_sf(manifest_content)

        # 4. Assinar
        print("✍️  Assinando APK...")
        pkcs7_der = (
            pkcs7.PKCS7SignatureBuilder()
            .set_data(sf_content)
            .add_signer(cert, private_key, hashes.SHA256())
            .sign(serialization.Encoding.DER, [pkcs7.PKCS7Options.DetachedSignature])
        )

        # 5. Montar APK final
        print(f"💾 Salvando em {OUTPUT_APK}...")
        with zipfile.ZipFile(OUTPUT_APK, "w", zipfile.ZIP_DEFLATED, allowZip64=False) as zout:
            for item in zin.infolist():
                zout.writestr(item, zin.read(item.filename))
            zout.writestr("META-INF/MANIFEST.MF", manifest_content)
            zout.writestr("META-INF/CERT.SF", sf_content)
            zout.writestr("META-INF/CERT.RSA", pkcs7_der)

    os.remove(tmp_zip)
    size_mb = os.path.getsize(OUTPUT_APK) / 1024 / 1024
    print(f"\n✅ MineX.apk gerado com sucesso! ({size_mb:.1f} MB)")
    print(f"📍 Local: {os.path.abspath(OUTPUT_APK)}")

if __name__ == "__main__":
    build()
