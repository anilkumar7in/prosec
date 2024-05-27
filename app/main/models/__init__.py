from flask import Blueprint

models_bp = Blueprint('models', __name__)

from app.main.models import models, firewall_model, grouping_model

