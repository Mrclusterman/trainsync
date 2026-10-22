"""Cuenta de intervals.icu vinculada por cada usuario (clave cifrada, app/crypto.py). Nunca la del servidor."""
from cryptography.exceptions import InvalidTag
from fastapi import Depends, HTTPException
from sqlalchemy.orm import Session

from app import auth, crypto, db
from app.services.intervals_client import IntervalsClient

make_client = IntervalsClient  # se sustituye en los tests


def linked_client(user: db.User, session: Session) -> IntervalsClient | None:
    link = session.get(db.IntervalsLink, user.id)
    if link is None:
        return None
    try:
        return make_client(link.athlete_id, crypto.decrypt(link.api_key_encrypted))
    except InvalidTag:  # ENCRYPTION_KEY cambiada en el servidor: la clave guardada ya no se puede leer; hay que revincular
        return None


def user_client(user: db.User = Depends(auth.current_user), session: Session = Depends(db.get_session)) -> IntervalsClient:
    """Dependencia: el cliente de intervals.icu del usuario con sesión; 409 si no lo ha vinculado."""
    client = linked_client(user, session)
    if client is None:
        raise HTTPException(409, "Vincula tu cuenta de intervals.icu en tu perfil para usar el plan de carrera")
    return client
