from fastapi import FastAPI

from app.routers import plan

app = FastAPI(title="TrainSync", version="0.1.0")
app.include_router(plan.router)


@app.get("/health")
def health():
    return {"status": "ok"}
