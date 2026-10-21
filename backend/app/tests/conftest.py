import os
import tempfile

# Base de datos temporal para toda la sesión de tests: se fija antes de importar app.db
os.environ["DATABASE_URL"] = "sqlite:///" + os.path.join(tempfile.mkdtemp(prefix="trainsync-test-"), "test.db")
