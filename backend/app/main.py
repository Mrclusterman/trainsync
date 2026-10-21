from fastapi import FastAPI

from app.routers import health, plan

app = FastAPI(title="TrainSync", version="0.1.0")
app.include_router(plan.router)
app.include_router(health.router)


@app.get("/health")
def health():
    return {"status": "ok"}
