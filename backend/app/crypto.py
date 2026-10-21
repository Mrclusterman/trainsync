"""Cifrado de credenciales de terceros en la base de datos: AES-256-GCM (RNF-03).

ENCRYPTION_KEY = 32 bytes en base64 (genera una con: python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())").
Sin ella no se pueden guardar credenciales: una clave aleatoria por proceso las dejaría ilegibles al reiniciar.
"""
import base64
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from fastapi import HTTPException


def _key() -> bytes:
    raw = os.getenv("ENCRYPTION_KEY")
    if not raw:
        raise HTTPException(503, "Falta ENCRYPTION_KEY en el servidor: no se pueden guardar credenciales")
    key = base64.b64decode(raw)
    if len(key) != 32:
        raise HTTPException(503, "ENCRYPTION_KEY debe ser de 32 bytes (AES-256)")
    return key


def encrypt(plain: str) -> str:
    nonce = os.urandom(12)
    return base64.b64encode(nonce + AESGCM(_key()).encrypt(nonce, plain.encode(), None)).decode()


def decrypt(token: str) -> str:
    raw = base64.b64decode(token)
    return AESGCM(_key()).decrypt(raw[:12], raw[12:], None).decode()
