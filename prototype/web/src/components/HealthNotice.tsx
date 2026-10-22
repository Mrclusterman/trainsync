// Aviso legal y de salud: sustituye al cuestionario de cribado (decisión del 2026-10-21, docs/CUMPLIMIENTO.md)
export function HealthNotice() {
  return (
    <aside aria-label="Aviso de salud" className="rounded-module border-l-2 border-alert bg-surface-1 p-4 text-sm">
      <p className="font-ui font-medium">Antes de empezar</p>
      <p className="mt-1 text-secondary">
        TrainSync no es un servicio médico ni sustituye el consejo de un profesional sanitario. Consulta a tu médico
        antes de empezar o de subir la intensidad si tienes una enfermedad del corazón, metabólica (como la diabetes) o
        del riñón, si estás embarazada o tienes una lesión. Si al entrenar notas dolor en el pecho, falta de aire con poco
        esfuerzo, mareos o palpitaciones, para y consulta. Al guardar tu perfil confirmas que entrenas bajo tu
        responsabilidad.
      </p>
    </aside>
  );
}
