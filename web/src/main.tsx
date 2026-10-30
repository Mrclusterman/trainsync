import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { Today } from "./Today"
import "./styles.css"

createRoot(document.getElementById("root")!).render(<StrictMode><Today /></StrictMode>)
