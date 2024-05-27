import logging
import os
from flask import Flask
from configs import Config
from flask_sqlalchemy import SQLAlchemy
from logging.handlers import RotatingFileHandler
import subprocess, threading

db = SQLAlchemy()

def run_pox_controller():
    def pox_thread():
        try:
            pox_path = '/home/ubuntu/prosec/app/controller/pox/'
            result = subprocess.run(['./pox.py', 'an_main'], cwd=pox_path, capture_output=True, text=True)
            logger.info("POX controller started successfully")

            with open('logs/controller.log', 'w') as f:
                f.write(result.stdout)
        except subprocess.CalledProcessError as e:
            logger.error(f"Error starting POX controller: {e}")
            with open('logs/controller.log', 'w') as f:
                f.write(e.stderr)
        except Exception as e:
            logger.error(f"Unexpected error starting POX controller: {e}")

    pox_thread = threading.Thread(target=pox_thread)
    pox_thread.start()
    logger.info("POX controller thread started")

    return pox_thread


def setup_logger():
    # Initialize logger as a global variable
    logger = logging.getLogger(__name__)
    logger.setLevel(Config.LOG_LEVEL)
    
    # Create logs directory if it doesn't exist
    if not os.path.exists(Config.LOG_DIR):
        os.makedirs(Config.LOG_DIR)

    # Create a rotating file handler
    open(Config.LOG_FILE, 'w').close()  # This clears the existing logfile or creates a new one
    file_handler = RotatingFileHandler(Config.LOG_FILE, maxBytes=10240, backupCount=10)
    file_handler.setLevel(Config.LOG_LEVEL)

    # Create a formatter and set it for the handler
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    file_handler.setFormatter(formatter)

    # Add the file handler to the logger
    logger.addHandler(file_handler)

    logger.info("Logger initialized successfully.")
    
    return logger
    

def create_app():
    app = Flask(__name__)
    
    app.config.from_object(Config)
    Config.init_app(app)
    
    #Initialize database
    db.init_app(app)

    
    #Register blueprints
    from app.main import main_bp
    from app.main.models import models_bp
    from app.main.routes import routes_bp

    app.register_blueprint(main_bp)
    app.register_blueprint(models_bp)
    app.register_blueprint(routes_bp)
    
    # Import models
    from app.main.models.grouping_model import GroupModel, GroupIPModel
    from app.main.models.firewall_model import FirewallRuleModel
    
    def initialize_defaults():
        with app.app_context():
            windows_group = GroupModel.query.filter_by(name='windows_group').first()
            linux_group = GroupModel.query.filter_by(name='linux_group').first()

            if not windows_group:
                new_group = GroupModel(name='windows_group')
                db.session.add(new_group)
                logger.info("Created windows_group")

            if not linux_group:
                new_group = GroupModel(name='linux_group')
                db.session.add(new_group)
                logger.info("Created linux_group")

            db.session.commit()

            windows_group_id = windows_group.id if windows_group else None
            linux_group_id = linux_group.id if linux_group else None

            arp_rule = FirewallRuleModel.query.filter_by(
                dl_type='0x0806', nw_proto='any', tp_src='any', tp_dst='any', nw_src='any', nw_dst='any', action='allow', priority=1000
                ).first()
            
            ssh_rule = FirewallRuleModel.query.filter_by(
                dl_type='any', nw_proto='tcp', tp_src='any', tp_dst='22', nw_src='any', nw_dst=f"group:{linux_group_id}", action='allow', priority=100
                ).first()

            rdp_rule = FirewallRuleModel.query.filter_by(
                dl_type='any', nw_proto='tcp', tp_src='any', tp_dst='3389', nw_src='any', nw_dst=f"group:{windows_group_id}", action='allow', priority=100
                ).first()

            default_rule = FirewallRuleModel.query.filter_by(
                dl_type='any', nw_proto='any', tp_src='any', tp_dst='any', nw_src='any', nw_dst='any', action='deny', priority=0
                ).first()
            
            if not arp_rule:
                arp_rule = FirewallRuleModel(
                    dl_type='0x0806', nw_proto='any', tp_src='any', tp_dst='any', nw_src='any', nw_dst='any', action='allow', priority=1000
                )
                db.session.add(arp_rule)
                logger.info("Created arp_policy")
            

            if not ssh_rule and linux_group_id:
                ssh_rule = FirewallRuleModel(
                    dl_type='any', nw_proto='tcp', tp_src='any', tp_dst='22', nw_src='any', nw_dst=f"group:{linux_group_id}", action='allow', priority=100
                ) 
                db.session.add(ssh_rule)
                logger.info("Created ssh_policy")

            if not rdp_rule and windows_group_id:
                rdp_rule = FirewallRuleModel(
                    dl_type='any', nw_proto='tcp', tp_src='any', tp_dst='3389', nw_src='any', nw_dst=f"group:{windows_group_id}", action='allow', priority=100
                )
                db.session.add(rdp_rule)
                logger.info("Created rdp_policy")

            if not default_rule:
                default_rule = FirewallRuleModel(
                    dl_type='any', nw_proto='any', tp_src='any', tp_dst='any', nw_src='any', nw_dst='any', action='deny', priority=0
                )
                db.session.add(default_rule)
                logger.info("Created default_policy")

            db.session.commit()


    def initialize_database():
        with app.app_context():
            db.create_all()
            initialize_defaults()

    
    initialize_database()
    #run_pox_controller()
    logger.info("Flask App initialized successfully.")
    return app


logger = setup_logger()
app = create_app()

