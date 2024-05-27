from app import logger, app
from app.extensions import dcn, pox_runner
#from celery_worker import run_pox_controller

if __name__ == "__main__":
    try:
        # Running the Flask app
        app.run(debug=True)
    except Exception as e:
        # Logging error if an exception occurs
        logger.exception("Failed to start Flask App: %s", e)

