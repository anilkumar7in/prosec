from flask import Blueprint

routes_bp = Blueprint('routes', __name__)

from app.main.routes import routes, firewall_routes, grouping_routes
