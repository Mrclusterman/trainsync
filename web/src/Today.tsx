import { useMemo, useState } from "react"
import { WebApp } from "trainsync-shared"
import type { PlanItem, Reason, TodayScreen, Tone } from "./types"
import running from "../../iosApp/iosApp/Assets.xcassets/sport_running.imageset/sport_running.svg"
import cycling from "../../iosApp/iosApp/Assets.xcassets/sport_cycling.imageset/sport_cycling.svg"
import swimming from "../../iosApp/iosApp/Assets.xcassets/sport_swimming.imageset/sport_swimming.svg"
import walking from "../../iosApp/iosApp/Assets.xcassets/sport_walking.imageset/sport_walking.svg"
import strength from "../../iosApp/iosApp/Assets.xcassets/sport_strength.imageset/sport_strength.svg"
import stateOk from "../../iosApp/iosApp/Assets.xcassets/state_ok.imageset/state_ok.svg"
import stateCaution from "../../iosApp/iosApp/Assets.xcassets/state_caution.imageset/state_caution.svg"
import stateRest from "../../iosApp/iosApp/Assets.xcassets/state_rest.imageset/state_rest.svg"
import check from "../../iosApp/iosApp/Assets.xcassets/check.imageset/check.svg"

const SPORT_ICON: Record<string, string> = { running, cycling, swimming_pool: swimming, swimming_open: swimming, walking }
const STATE: Record<Tone, [string, string]> = { OK: ["ok", stateOk], CAUTION: ["caution", stateCaution], REST: ["rest", stateRest] }

// Los SVG de iOS son plantillas en negro: como máscara toman el color del texto
const Icon = ({ src, label, size = 20 }: { src: string; label?: string; size?: number }) => (
  <span className="icon" role={label ? "img" : undefined} aria-label={label} aria-hidden={label ? undefined : true}
    style={{ width: size, height: size, maskImage: `url("${src}")`, WebkitMaskImage: `url("${src}")` }} />
)

/** Como minutesText de las apps: 4′, 30″ o 2′30″. */
const minutesText = (m: number) =>
  m < 1 ? `${Math.trunc(m * 60)}″` : m % 1 === 0 ? `${m}′` : `${Math.trunc(m)}′${String(Math.trunc((m % 1) * 60)).padStart(2, "0")}″`

const ReasonRow = ({ reason, tone = "caution" }: { reason: Reason; tone?: string }) => (
  <div className="reason">
    <span className={`bar ${tone}`} />
    <p>{reason.text}{reason.evidence && <span className="evidence"> · {reason.evidence}</span>}</p>
  </div>
)

function SessionCard({ core, item, done, onDone }: { core: WebApp; item: PlanItem; done: boolean; onDone?: () => void }) {
  const label = core.sportLabel(item.sport)
  return (
    <section className={`module${done ? " done" : ""}`}>
      <div className="row">
        <span className="badge"><Icon src={SPORT_ICON[item.sport] ?? strength} label={label} /></span>
        <span className="sport">{item.chosen ? `${label} · elegida por ti` : label}</span>
        {done && <span className="hecha"><Icon src={check} size={18} />Hecha</span>}
        <span className="mono big">{item.durationMin}</span><span className="mono muted">&nbsp;min</span>
      </div>
      <h2>{item.title}</h2>
      <p className="muted">{item.hint}</p>
      {item.steps.length > 0 && (
        <ul className="steps">
          {item.steps.map((s, i) => (
            <li key={i}>
              <span className="zone" style={{ background: `var(--${s.intensityZone.toLowerCase()}, var(--chip))` }}>{s.intensityZone}</span>
              <span className="step">
                <strong>{s.name.charAt(0).toUpperCase() + s.name.slice(1)}</strong>
                {s.target && <span className="mono muted small">{s.target}</span>}
              </span>
              <span className="mono muted">{s.reps > 1 ? `${s.reps}×` : ""}{s.meters != null ? `${s.meters} m` : minutesText(s.minutes)}</span>
            </li>
          ))}
        </ul>
      )}
      {item.exercises.map((e) => <p key={e}>{e}</p>)}
      {onDone && <button className="primary" onClick={onDone}>La he hecho</button>}
    </section>
  )
}

export function Today() {
  const core = useMemo(() => new WebApp(), [])
  const [version, setVersion] = useState(0)
  const act = (f: () => void) => { f(); setVersion((v) => v + 1) }

  if (core.needsOnboarding) {
    return (
      <main>
        <h1>Hoy</h1>
        <section className="module">
          <p>La versión web aún no tiene bienvenida. Prueba Hoy con un perfil de ejemplo: guardado solo en este navegador.</p>
          <button className="primary" onClick={() => act(() => core.startDemo())}>Usar datos de prueba</button>
        </section>
      </main>
    )
  }

  const screen: TodayScreen = JSON.parse(core.todayJson())
  const day = screen.day
  const [tone, stateIcon] = STATE[screen.tone]
  return (
    <main data-version={version}>
      <header>
        <h1>Hoy</h1>
        <p className="muted">{screen.dateLabel}</p>
      </header>
      {screen.goalLine && <p className="muted">{screen.goalLine}</p>}
      <section className={`state ${tone}`}>
        <span className="state-icon"><Icon src={stateIcon} size={22} /></span>
        <div>
          <p className="state-title">{screen.stateTitle}</p>
          {screen.stateDetail.map((r, i) => <ReasonRow key={i} reason={r} tone={tone} />)}
        </div>
      </section>
      {day.items.map((item) => {
        const done = day.doneSports.includes(item.slot) || day.doneSports.includes(item.sport)
        return <SessionCard key={item.slot} core={core} item={item} done={done}
          onDone={done ? undefined : () => act(() => core.markDone(day.date, item.sport))} />
      })}
      {day.adjustments.length > 0 && day.reasons.map((r, i) => <ReasonRow key={i} reason={r} tone="ok" />)}
      <section className="module">
        <div className="row">
          <h3>Esta semana</h3>
          <span className="mono muted">{screen.weekMinutes} de 150 min</span>
        </div>
        <ol className="week">
          {screen.weekDots.map((d) => (
            <li key={d.date} aria-label={`${d.letter}: ${d.done ? "hecho" : d.planned ? "planificado" : "libre"}`}>
              <span className={`dot${d.done ? " done" : ""}${d.isToday ? " today" : ""}`}>{d.planned && !d.done && <i />}</span>
              <span className="muted small" aria-hidden>{d.letter}</span>
            </li>
          ))}
        </ol>
        {screen.meetsWho
          ? <p className="ok-text"><Icon src={check} size={18} />Cumples la recomendación de la OMS para tu salud</p>
          : <p className="muted">{screen.strengthDays >= 2 ? `Fuerza: ${screen.strengthDays} días, mínimo cumplido` : `Fuerza: ${screen.strengthDays} de 2 días`}</p>}
        {screen.warnings.map((w) => <ReasonRow key={w} reason={{ text: w, evidence: null }} />)}
        {screen.info.map((t) => <p key={t} className="muted">{t}</p>)}
      </section>
    </main>
  )
}
