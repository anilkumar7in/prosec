import os
import logging

class Config:
    LOG_DIR = 'logs'
    LOG_FILE = os.path.join(LOG_DIR, 'app.log')
    LOG_LEVEL = logging.DEBUG

    SQLALCHEMY_DATABASE_URI = 'sqlite:///app.db'
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # Other configurations...
    CELERY_BROKER_URL = 'redis://localhost:6379/0'  # Example: Redis as broker
    CELERY_RESULT_BACKEND = 'redis://localhost:6379/0'

    @staticmethod
    def init_app(app):
        if not os.path.exists(Config.LOG_DIR):
            os.makedirs(Config.LOG_DIR)

        # Configure logging
        app.logger.handlers = []
        app.logger.setLevel(logging.INFO)
        formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
        
        file_handler = logging.FileHandler(Config.LOG_FILE)
        file_handler.setFormatter(formatter)
        file_handler.setLevel(logging.INFO)

        app.logger.addHandler(file_handler)

