import { Suspense } from "react";
import { PlanStrip } from "@/components/PlanStrip";
import { PlanSkeleton } from "@/components/Skeletons";
import { getPlan } from "@/lib/api";
import { PHASE_LABEL, shortDate } from "@/lib/format";

async function PlanView() {
  const plan = await getPlan(14);
  return (
    <>
      <p className="font-ui text-sm text-secondary">
        {plan.goal ? `${plan.goal.name} · ${shortDate(plan.goal.date)} · ` : "Sin objetivo · "}
        {PHASE_LABEL[plan.days[0].phase]}
      </p>
      <div className="mt-4"><PlanStrip days={plan.days} /></div>
    </>
  );
}

export default function PlanPage() {
  return (
    <div className="pt-4">
      <h1 className="font-ui text-2xl font-bold">Plan de 14 días</h1>
      <div className="mt-2">
        <Suspense fallback={<PlanSkeleton />}><PlanView /></Suspense>
      </div>
    </div>
  );
}
