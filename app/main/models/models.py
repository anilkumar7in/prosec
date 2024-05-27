from app import db
from sqlalchemy import event
#import datetime

class DiscoverOSEventModel(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    event = db.Column(db.String(50), nullable=False) #os_added or os_removed
    os_type = db.Column(db.String(255), nullable=False)
    arp = db.Column(db.String(30), nullable=False)
    ipv4 = db.Column(db.String(15), nullable=False)  # Assuming IPv4 addresses

    def __repr__(self):
        return f"<DiscoverOSEventModel(id={self.id}, event='{self.event}', os_type='{self.os_type}', arp='{self.arp}', ipv4='{self.ipv4}')>"


class ServiceModel(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    url = db.Column(db.String(255), nullable=False)
    service_name = db.Column(db.String(100), nullable=False)

    def __repr__(self):
        return f"<ServiceModel(id={self.id}, url='{self.url}', service_name='{self.service_name}')>"

