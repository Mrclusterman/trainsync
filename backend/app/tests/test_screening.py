"""Algoritmo de cribado del ACSM (Riebe et al. 2015/2017): las seis ramas más el paso de otras condiciones."""
import pytest

from app.services.screening import Screening, screen


@pytest.mark.parametrize("active, disease, symptoms, clearance, cap", [
    (False, False, False, "none", "light_moderate"),  # inactivo sano: empezar ligero-moderado y progresar
    (False, True, False, "recommended", "light_moderate"),  # inactivo con enfermedad: consulta antes de empezar
    (False, False, True, "recommended", "light_moderate"),  # inactivo con síntomas
    (False, True, True, "recommended", "light_moderate"),
    (True, False, False, "none", "vigorous"),  # activo sano: seguir y progresar
    (True, True, False, "vigorous_only", "moderate"),  # activo con enfermedad sin síntomas: consulta solo para vigoroso
    (True, False, True, "stop", "light_moderate"),  # activo con síntomas: parar y consultar
    (True, True, True, "stop", "light_moderate"),
])
def test_acsm_branches(active, disease, symptoms, clearance, cap):
    r = screen(Screening(active=active, known_disease=disease, symptoms=symptoms))
    assert (r.clearance, r.intensity_cap) == (clearance, cap) and r.message


def test_other_conditions_recommend_clearance():
    r = screen(Screening(active=True, known_disease=False, symptoms=False, other_conditions=True))
    assert r.clearance == "recommended" and r.intensity_cap == "light_moderate"
