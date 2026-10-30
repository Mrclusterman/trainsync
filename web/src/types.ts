// Forma del JSON de TodayScreen (shared/.../app/Screens.kt y Planner.kt): solo los campos que pinta la web
export type Tone = "OK" | "CAUTION" | "REST"
export interface Reason { text: string; evidence: string | null }
export interface Step { name: string; reps: number; minutes: number; intensityZone: string; target: string | null; meters: number | null }
export interface PlanItem { sport: string; title: string; hint: string; durationMin: number; steps: Step[]; exercises: string[]; chosen: boolean; slot: string
  alternatives: PlanItem[] }
export interface DayView { date: string; isPast: boolean; items: PlanItem[]; reasons: Reason[]; adjustments: Reason[]; doneSports: string[] }
export interface WeekDot { letter: string; date: string; isToday: boolean; planned: boolean; done: boolean }
export interface TodayScreen {
  dateLabel: string; goalLine: string | null; stateTitle: string; stateDetail: Reason[]; tone: Tone; day: DayView
  meetsWho: boolean; weekMinutes: number; strengthDays: number; weekDots: WeekDot[]; warnings: string[]; info: string[]
  askReadiness: boolean; goalFinished: boolean
}
/** WebApp.liftJson */
export interface LiftHint { exercise: string; last: string | null; suggestion: string | null; weight: string }
