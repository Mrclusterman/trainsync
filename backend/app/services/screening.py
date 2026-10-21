"""Cribado previo al ejercicio: algoritmo del ACSM (Riebe et al., Med Sci Sports Exerc 2015;47(11):2473–2479;
ACSM's Guidelines for Exercise Testing and Prescription, 2017), en lugar del PAR-Q+.

El PAR-Q+ no puede incorporarse a una app sin permiso escrito de la PAR-Q+ Collaboration (eparmedx.com). El algoritmo
del ACSM es un procedimiento de decisión publicado; las preguntas de la app están redactadas con palabras propias.
Definiciones (Riebe 2017, reproducidas en la guía de cribado de Leeds Beckett University, 2020):
- Activo: ejercicio planificado de al menos 30 min a intensidad moderada, ≥ 3 días/semana, en los últimos 3 meses.
- Moderada: 40–59 % de la FC de reserva, 3–5,9 MET, RPE 12–13 (Borg 6–20). Vigorosa: ≥ 60 % FCR, ≥ 6 MET, RPE ≥ 14.
"""
from dataclasses import dataclass

# Signos o síntomas que sugieren enfermedad cardiovascular, metabólica o renal (lista del ACSM, redactada aquí)
SYMPTOMS = (
    "Dolor o molestia en el pecho, el cuello, la mandíbula o los brazos que pueda deberse a falta de riego",
    "Falta de aire en reposo o con un esfuerzo ligero",
    "Mareos o desmayos",
    "Falta de aire al estar tumbado o que te despierta por la noche",
    "Tobillos hinchados",
    "Palpitaciones o latidos muy rápidos o irregulares que te resultan desagradables",
    "Dolor o calambres en las piernas al caminar distancias cortas",
    "Un soplo en el corazón conocido",
    "Cansancio o falta de aire fuera de lo normal con tus actividades de siempre",
)
DISEASES = ("Enfermedad del corazón o de los vasos sanguíneos", "Diabetes tipo 1 o tipo 2", "Enfermedad del riñón")


@dataclass(frozen=True)
class Screening:
    active: bool  # cumple la definición de activo
    known_disease: bool  # enfermedad cardiovascular, metabólica o renal diagnosticada
    symptoms: bool  # algún signo o síntoma de SYMPTOMS
    other_conditions: bool = False  # lesión, problema de huesos o articulaciones, embarazo


@dataclass(frozen=True)
class Result:
    clearance: str  # none / recommended / vigorous_only / stop
    intensity_cap: str  # light_moderate / moderate / vigorous: lo más alto que puede proponer el plan
    message: str


def screen(s: Screening) -> Result:
    if s.symptoms and s.active:
        return Result("stop", "light_moderate",
                      "Deja de hacer ejercicio y consulta a tu médico antes de volver: tienes algún síntoma que hay que valorar.")
    if s.symptoms or (s.known_disease and not s.active):
        return Result("recommended", "light_moderate",
                      "Te recomendamos consultar a tu médico antes de empezar. Hasta entonces, el plan se queda en "
                      "intensidad ligera a moderada.")
    if s.known_disease:  # activo, sin síntomas
        return Result("vigorous_only", "moderate",
                      "Puedes seguir con intensidad moderada. Antes de hacer ejercicio vigoroso, consulta a tu médico.")
    if s.other_conditions:  # paso adicional de la guía de Leeds Beckett (lesiones, embarazo)
        return Result("recommended", "light_moderate",
                      "Por lo que nos cuentas, consulta a un profesional sanitario antes de subir la intensidad.")
    if s.active:
        return Result("none", "vigorous", "Puedes seguir entrenando y progresar de forma gradual.")
    return Result("none", "light_moderate", "Puedes empezar con intensidad ligera a moderada y progresar poco a poco.")
