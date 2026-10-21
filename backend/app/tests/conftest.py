import os
import tempfile

# Base de datos temporal para toda la sesión de tests: se fija antes de importar app.db
os.environ["DATABASE_URL"] = "sqlite:///" + os.path.join(tempfile.mkdtemp(prefix="trainsync-test-"), "test.db")


import pytest  # noqa: E402


@pytest.fixture(autouse=True)
def _fresh_rate_limits():
    from app import ratelimit
    ratelimit.reset()
