import { useEffect, useMemo, useState, type ReactNode } from "react"
import { WebApp } from "trainsync-shared"
import type { DayView, LiftHint, PlanItem, Reason, TodayScreen, Tone } from "./types"
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

/** Si la pantalla es de ordenador (el mismo corte que styles.css). */
function useWide() {
  const query = "(min-width: 900px)"
  const [wide, setWide] = useState(() => matchMedia(query).matches)
  useEffect(() => {
    const m = matchMedia(query)
    const on = () => setWide(m.matches)
    m.addEventListener("change", on)
    return () => m.removeEventListener("change", on)
  }, [])
  return wide
}

/** Ejecuta una acción del núcleo y vuelve a pintar; si falla, enseña su texto (como el aviso de las apps). */
type Act = (f: () => void, success?: string) => void

// Los SVG de iOS son plantillas en negro: como máscara toman el color del texto
const Icon = ({ src, label, size = 20 }: { src: string; label?: string; size?: number }) => (
  <span className="icon" role={label ? "img" : undefined} aria-label={label} aria-hidden={label ? undefined : true}
    style={{ width: size, height: size, maskImage: `url("${src}")`, WebkitMaskImage: `url("${src}")` }} />
)

/** Como minutesText de las apps: 4′, 30″ o 2′30″. */
const minutesText = (m: number) =>
  m < 1 ? `${Math.trunc(m * 60)}″` : m % 1 === 0 ? `${m}′` : `${Math.trunc(m)}′${String(Math.trunc((m % 1) * 60)).padStart(2, "0")}″`

/** Motivo con su etiqueta de evidencia: se toca para ver qué significa. */
function ReasonRow({ core, reason, tone = "caution" }: { core: WebApp; reason: Reason; tone?: string }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="reason">
      <span className={`bar ${tone}`} />
      <div>
        <p>{reason.text}</p>
        {reason.evidence && (
          <button className="tag" aria-expanded={open} onClick={() => setOpen(!open)}>{reason.evidence}</button>
        )}
        {open && reason.evidence && <p className="muted small">{core.evidenceMeaning(reason.evidence)}</p>}
      </div>
    </div>
  )
}

/** Sin reloj vinculado: sueño, energía y agujetas de 1 (mal) a 5 (bien) ajustan la sesión de hoy. */
function ReadinessCard({ core, act }: { core: WebApp; act: Act }) {
  const [answers, setAnswers] = useState({ sleep: 3, energy: 3, soreness: 3 })
  const questions = [["sleep", "Sueño"], ["energy", "Energía"], ["soreness", "Agujetas o molestias"]] as const
  return (
    <section className="module">
      <h3>¿Cómo estás hoy?</h3>
      <p className="muted">Sin reloj vinculado, tus respuestas ajustan la sesión de hoy: si dormiste mal o estás cargado, será más suave.</p>
      {questions.map(([key, label]) => (
        <fieldset key={key}>
          <legend>{label}</legend>
          <div className="chips">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} className="chip" aria-pressed={answers[key] === n}
                onClick={() => setAnswers({ ...answers, [key]: n })}>{n === 1 ? "1 mal" : n === 5 ? "5 bien" : n}</button>
            ))}
          </div>
        </fieldset>
      ))}
      <button className="primary" onClick={() => act(() => core.answerReadiness(answers.sleep, answers.energy, answers.soreness))}>
        Ajustar hoy
      </button>
    </section>
  )
}

/** Cada ejercicio de fuerza: lo apuntado la última vez, la sugerencia de hoy y apuntar peso y repeticiones. */
function LiftRow({ core, date, line, act }: { core: WebApp; date: string; line: string; act: Act }) {
  const json = core.liftJson(line)
  const hint: LiftHint | null = json ? JSON.parse(json) : null
  const [open, setOpen] = useState(false)
  const [weight, setWeight] = useState(hint?.weight ?? "")
  const [reps, setReps] = useState("")
  if (!hint) return null
  return (
    <div className="lift">
      <strong>{hint.exercise}</strong>
      {hint.last && <p className="muted small">{hint.last}</p>}
      {hint.suggestion && <p className="ok-text small">{hint.suggestion}</p>}
      {!open
        ? <button className="text" onClick={() => setOpen(true)}>Apuntar</button>
        : (
          <form className="row" onSubmit={(e) => { e.preventDefault(); act(() => core.logLift(date, line, weight, reps), "Apuntado"); setOpen(false) }}>
            <label>kg<input inputMode="decimal" value={weight} onChange={(e) => setWeight(e.target.value)} /></label>
            <label>reps<input inputMode="numeric" value={reps} onChange={(e) => setReps(e.target.value.replace(/\D/g, ""))} /></label>
            <button className="text" type="submit">Guardar</button>
          </form>
        )}
    </div>
  )
}

function SessionCard({ core, item, done, children }: { core: WebApp; item: PlanItem; done: boolean; children?: ReactNode }) {
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
      {children}
    </section>
  )
}

/** Las sesiones de hoy con sus acciones: hecha, cambiar de deporte, apuntar fuerza, hoy no puedo (DayContent de Android). */
function DayContent({ core, day, act }: { core: WebApp; day: DayView; act: Act }) {
  const [openSlot, setOpenSlot] = useState<string | null>(null)
  const changeable = core.canChangeDay(day.date)
  return (
    <>
      {day.items.length === 0 && changeable && (
        <section className="module">
          <button className="secondary" onClick={() => act(() => core.preferTrain(day.date))}>Prefiero entrenar hoy</button>
        </section>
      )}
      <div className="sessions">
      {day.items.map((item) => {
        const done = day.doneSports.includes(item.slot) || day.doneSports.includes(item.sport)
        const open = openSlot === item.slot
        return (
          <SessionCard key={item.slot} core={core} item={item} done={done}>
            <div className="row">
              {!done && <button className="primary grow" onClick={() => act(() => core.markDone(day.date, item.sport))}>La he hecho</button>}
              {!done && !day.isPast && item.alternatives.length > 0 && (
                <button className="secondary" aria-expanded={open} onClick={() => setOpenSlot(open ? null : item.slot)}>{open ? "Cerrar" : "Cambiar"}</button>
              )}
            </div>
            {open && item.alternatives.map((alt) => (
              <div className="row" key={alt.sport}>
                <span className="badge small-badge"><Icon src={SPORT_ICON[alt.sport] ?? strength} label={core.sportLabel(alt.sport)} size={18} /></span>
                <span className="grow">{alt.title} · {alt.durationMin}′</span>
                <button className="text" onClick={() => { act(() => core.chooseAlternative(day.date, item.slot, alt.sport)); setOpenSlot(null) }}>Haré esta</button>
              </div>
            ))}
            {item.sport === "strength" && item.exercises.map((line) => <LiftRow key={line} core={core} date={day.date} line={line} act={act} />)}
            {item.chosen && <button className="text" onClick={() => act(() => core.clearChoice(day.date))}>Volver a la planificada</button>}
          </SessionCard>
        )
      })}
      </div>
      {day.adjustments.length > 0 && day.reasons.map((r, i) => <ReasonRow key={i} core={core} reason={r} tone="ok" />)}
      {changeable && day.items.length > 0 && (
        <button className="text muted" onClick={() => act(() => core.cannotTrain(day.date))}>Hoy no puedo entrenar</button>
      )}
    </>
  )
}

export function Today() {
  const core = useMemo(() => new WebApp(), [])
  const wide = useWide()
  const [, setVersion] = useState(0)
  const [message, setMessage] = useState<string | null>(null)
  const act: Act = (f, success) => {
    try {
      f()
      setMessage(success ?? null)
    } catch (e) {
      setMessage(e instanceof Error && e.message ? e.message : "No se pudo completar")
    }
    setVersion((v) => v + 1)
  }
  const status = <p className="status" role="status">{message}</p>

  if (core.needsOnboarding) {
    return (
      <main>
        <h1>Hoy</h1>
        <section className="module">
          <p>La versión web aún no tiene bienvenida. Prueba Hoy con un perfil de ejemplo: guardado solo en este navegador.</p>
          <button className="primary" onClick={() => act(() => core.startDemo())}>Usar datos de prueba</button>
        </section>
        {status}
      </main>
    )
  }

  const screen: TodayScreen = JSON.parse(core.todayJson())
  const [tone, stateIcon] = STATE[screen.tone]
  return (
    <main className="wide">
      <header className="page-head">
        <div>
          <h1>Hoy</h1>
          <p className="muted">{screen.dateLabel}</p>
        </div>
        {screen.goalLine && <p className="muted goal">{screen.goalLine}</p>}
      </header>
      <div className="layout">
      <div className="col">
      {screen.goalFinished && (
        <section className="module">
          <h3>Tu carrera ya pasó</h3>
          <p className="muted">¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días. En la web aún no se puede elegir objetivo: hazlo desde la app.</p>
        </section>
      )}
      <section className={`state ${tone}`}>
        <span className="state-icon"><Icon src={stateIcon} size={22} /></span>
        <div>
          <p className="state-title">{screen.stateTitle}</p>
          {screen.stateDetail.map((r, i) => <ReasonRow key={i} core={core} reason={r} tone={tone} />)}
        </div>
      </section>
      {screen.askReadiness && !wide && <ReadinessCard core={core} act={act} />}
      <DayContent core={core} day={screen.day} act={act} />
      </div>
      {/* En ordenador la semana va al lado, fija al desplazar; en el móvil, debajo como en las apps */}
      <aside className="col side">
      {screen.askReadiness && wide && <ReadinessCard core={core} act={act} />}
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
        {screen.warnings.map((w) => <ReasonRow key={w} core={core} reason={{ text: w, evidence: null }} />)}
        {screen.info.map((t) => <p key={t} className="muted">{t}</p>)}
      </section>
      </aside>
      </div>
      {status}
    </main>
  )
}
