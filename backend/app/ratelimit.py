"""Límite de intentos en el login y en los emails de la cuenta (fuerza bruta y envío masivo).

Por email y no por IP: la web llama a la API desde su servidor, así que todas las peticiones llegan con la misma IP.
shortcut: en memoria y por proceso; con varias instancias de la API, pasarlo a Redis o a la base de datos.
"""
import time
from collections import defaultdict, deque

from fastapi import HTTPException

_hits: dict[str, deque[float]] = defaultdict(deque)


def check(key: str, limit: int, window_s: int) -> None:
    """Cuenta un intento; 429 si ya hay `limit` en la ventana."""
    now, hits = time.monotonic(), _hits[key]
    while hits and hits[0] <= now - window_s:
        hits.popleft()
    if len(hits) >= limit:
        wait = int(hits[0] + window_s - now) + 1
        raise HTTPException(429, f"Demasiados intentos: vuelve a probar en {ceil_minutes(wait)} min",
                            headers={"Retry-After": str(wait)})
    hits.append(now)


def ceil_minutes(seconds: int) -> int:
    return -(-seconds // 60)


def reset() -> None:
    _hits.clear()
