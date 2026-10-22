"""Emails de la cuenta (verificar el email, recuperar la contraseña) por SMTP con la biblioteca estándar.

Sin SMTP_HOST (desarrollo) no se envía nada: el email se escribe en el log del servidor. No dejarlo así al desplegar,
porque el log tendría enlaces válidos para entrar en cuentas.
"""
import logging
import os
import smtplib
from email.message import EmailMessage

log = logging.getLogger("trainsync.mail")
APP_URL = os.getenv("APP_URL", "http://localhost:3000")


def send(to: str, subject: str, body: str) -> None:
    host = os.getenv("SMTP_HOST")
    if not host:
        log.warning("SMTP_HOST sin configurar: email no enviado a %s\n%s\n\n%s", to, subject, body)
        return
    msg = EmailMessage()
    msg["From"], msg["To"], msg["Subject"] = os.getenv("MAIL_FROM", "TrainSync <no-reply@localhost>"), to, subject
    msg.set_content(body)
    try:
        with smtplib.SMTP(host, int(os.getenv("SMTP_PORT", "587")), timeout=10) as smtp:
            smtp.starttls()
            if os.getenv("SMTP_USER"):
                smtp.login(os.environ["SMTP_USER"], os.getenv("SMTP_PASSWORD", ""))
            smtp.send_message(msg)
    except (OSError, smtplib.SMTPException):  # va en segundo plano: el fallo no llega al usuario, queda en el log
        log.exception("No se pudo enviar el email a %s", to)


def verify_email(to: str, token: str) -> None:
    send(to, "Confirma tu email en TrainSync",
         f"Confirma que este email es tuyo:\n\n{APP_URL}/verificar?token={token}\n\n"
         "El enlace caduca en 48 horas. Si no has creado una cuenta en TrainSync, ignora este mensaje.")


def reset_password(to: str, token: str) -> None:
    send(to, "Cambia tu contraseña de TrainSync",
         f"Para elegir una contraseña nueva:\n\n{APP_URL}/entrar/nueva-clave?token={token}\n\n"
         "El enlace caduca en 1 hora y deja de valer en cuanto cambies la contraseña. "
         "Si no lo has pedido tú, ignora este mensaje: tu contraseña sigue igual.")
