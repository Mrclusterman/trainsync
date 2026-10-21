from fastapi import FastAPI

from app import db
from app.routers import health, me, plan

app = FastAPI(title="TrainSync", version="0.1.0")
app.include_router(plan.router)
app.include_router(health.router)
app.include_router(me.router)
db.init()


@app.get("/health")
def healthcheck():
    return {"status": "ok"}
