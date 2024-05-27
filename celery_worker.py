from celery import Celery
from app import app, db, logger
from app.main.models.models import ServiceModel
import requests
import logging
from app.main.models.grouping_model import GroupModel, GroupIPModel

celery = Celery(app.name, broker=app.config['CELERY_BROKER_URL'])
celery.conf.update(app.config)

@celery.task
def notify_services(event_data):
    with app.app_context():
        services = ServiceModel.query.all()
        for service in services:
            payload = {
                'event_id': event_data['event_id'],
                'event': event_data['event'],
                'ipv4': event_data['ipv4'],
                'os_type': event_data['os_type'],
                'arp': event_data['arp']
            }
            url = service.url
            try:
                response = requests.post(url, json=payload)
                if response.status_code == 200:
                    logger.info(f"Service {url} is notified about {event_data} successfully")
                else:
                    logger.info(f"Failed to send notification to {url}. Status code: {response.status_code}")
            except Exception as e:
                logger.info(f"Error sending notification to {url}: {e}")

@celery.task
def add_ip_to_group_task(group_name, ip_address):
    with app.app_context():
        try:
            group = GroupModel.query.filter_by(name=group_name).first()
            if not group:
                logger.error(f"Group {group_name} does not exist")
                return

            existing_ip = GroupIPModel.query.filter_by(group_id=group.id, ip_address=ip_address).first()
            if existing_ip:
                logger.info(f"IP address {ip_address} already exists in the group {group_name}")
                return

            new_ip = GroupIPModel(group_id=group.id, ip_address=ip_address)
            db.session.add(new_ip)
            db.session.commit()
            logger.info(f"IP address {ip_address} added to group {group_name} successfully")
        except Exception as e:
            logger.error(f"Error adding IP address {ip_address} to group {group_name}: {e}")
            db.session.rollback()

@celery.task
def delete_ip_from_group_task(group_name, ip_address):
    with app.app_context():
        try:
            group = GroupModel.query.filter_by(name=group_name).first()
            if not group:
                logger.error(f"Group {group_name} does not exist")
                return

            existing_ip = GroupIPModel.query.filter_by(group_id=group.id, ip_address=ip_address).first()
            if not existing_ip:
                logger.info(f"IP address {ip_address} does not exist in the group {group_name}")
                return

            db.session.delete(existing_ip)
            db.session.commit()
            logger.info(f"IP address {ip_address} removed from group {group_name} successfully")
        except Exception as e:
            logger.error(f"Error removing IP address {ip_address} from group {group_name}: {e}")
            db.session.rollback()

