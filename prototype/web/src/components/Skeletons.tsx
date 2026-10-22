// Skeletons en todos los estados de carga, nunca spinners (diseño)
export function DashboardSkeleton() {
  return (
    <div aria-busy="true" aria-label="Cargando tu día" className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton h-32" />)}
      </div>
      <div className="skeleton h-72" />
      <div className="skeleton h-40" />
    </div>
  );
}

export function PlanSkeleton() {
  return (
    <div aria-busy="true" aria-label="Cargando el plan" className="space-y-4">
      <div className="flex gap-2 overflow-hidden">
        {[0, 1, 2, 3, 4].map((i) => <div key={i} className="skeleton h-36 w-24 shrink-0" />)}
      </div>
      <div className="skeleton h-28" />
    </div>
  );
}
