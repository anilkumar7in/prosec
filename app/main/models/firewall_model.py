from app import db
#from sqlalchemy import event, Integer, String, Column, ForeignKey
from sqlalchemy import event
#import datetime

class FirewallRuleModel(db.Model):
    __tablename__ = 'firewall_rules'
    id = db.Column(db.Integer, primary_key=True)
    dl_type = db.Column(db.String(64), nullable=True)
    nw_proto = db.Column(db.String(64), nullable=True)
    tp_src = db.Column(db.String(64), nullable=True)
    tp_dst = db.Column(db.String(64), nullable=True)
    nw_src = db.Column(db.String(64), nullable=True)
    nw_dst = db.Column(db.String(64), nullable=True)
    action = db.Column(db.String(64), nullable=False, default="deny")
    priority = db.Column(db.Integer, nullable=False, default=0) 
    
    def __repr__(self):
        return f"<FirewallRuleModel {self.id} {self.action}>"

