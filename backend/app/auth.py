"""Contraseñas y sesiones (RNF-03).

Contraseñas con scrypt de la biblioteca estándar (recomendado por OWASP junto a bcrypt y Argon2) en lugar de bcrypt:
evita una dependencia nativa. Sesiones con JWT HS256; JWT_SECRET obligatorio en producción.
"""
import base64
import hashlib
import hmac
import os
import secrets
from datetime import timedelta

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app import db

# shortcut: sin JWT_SECRET se genera uno por proceso (las sesiones caducan al reiniciar); fijarlo antes de desplegar
SECRET = os.getenv("JWT_SECRET") or secrets.token_urlsafe(32)
TOKEN_DAYS = int(os.getenv("JWT_DAYS", "7"))
SCRYPT = {"n": 2**14, "r": 8, "p": 1}  # parámetros mínimos de OWASP para scrypt

bearer = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    key = hashlib.scrypt(password.encode(), salt=salt, **SCRYPT)
    return "scrypt$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(key).decode()


def verify_password(password: str, stored: str) -> bool:
    _, salt, key = stored.split("$")
    got = hashlib.scrypt(password.encode(), salt=base64.b64decode(salt), **SCRYPT)
    return hmac.compare_digest(got, base64.b64decode(key))


def create_token(user_id: str) -> str:
    return jwt.encode({"sub": user_id, "exp": db.now() + timedelta(days=TOKEN_DAYS)}, SECRET, algorithm="HS256")


def current_user(creds: HTTPAuthorizationCredentials | None = Depends(bearer),
                 session: Session = Depends(db.get_session)) -> db.User:
    if creds is None:
        raise HTTPException(401, "Falta el token de sesión")
    try:
        user_id = jwt.decode(creds.credentials, SECRET, algorithms=["HS256"])["sub"]
    except jwt.PyJWTError:
        raise HTTPException(401, "Sesión no válida o caducada")
    user = session.get(db.User, user_id)
    if user is None:
        raise HTTPException(401, "La cuenta ya no existe")
    return user
