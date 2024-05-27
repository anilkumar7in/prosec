# app/main/__init__.py
from app import db
from app.main.models.models import DiscoverOSEventModel
from celery_worker import notify_services, add_ip_to_group_task, delete_ip_from_group_task
from sqlalchemy import event

# Define the event listener
@event.listens_for(DiscoverOSEventModel, 'after_insert')
def after_event_insert(mapper, connection, target):
    # Call the notify_services Celery task to send notifications
    notify_services.apply_async(args=[{
        'event_id': target.id,
        'event': target.event,
        'ipv4': target.ipv4,
        'os_type': target.os_type,
        'arp': target.arp
    }])
    os_type = target.os_type.lower()
    group_name = f"{os_type}_group"
    if group_name in ["windows_group", "linux_group"]:
        ip_address = target.ipv4
        # Call the add_ip_to_group_task Celery task
        add_ip_to_group_task.apply_async(args=[group_name, ip_address])

# Define the event listener for after delete
@event.listens_for(DiscoverOSEventModel, 'after_delete')
def after_event_delete(mapper, connection, target):
    os_type = target.os_type.lower()
    group_name = f"{os_type}_group"

    if group_name in ["windows_group", "linux_group"]:
        ip_address = target.ipv4
        # Call the delete_ip_from_group_task Celery task
        delete_ip_from_group_task.apply_async(args=[group_name, ip_address])

