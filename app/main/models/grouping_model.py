from app import db
from sqlalchemy import event, ForeignKey
#from sqlalchemy import event, Integer, String, Column, ForeignKey

class GroupModel(db.Model):
    __tablename__ = 'groups'
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(64), unique=True, nullable=False)
    #system_defined = db.Column(db.Boolean, default=False, nullable=False) 
    ips = db.relationship('GroupIPModel', backref='group', lazy=True, cascade='all, delete-orphan')
 
    def __repr__(self):
        return f"<GroupModel {self.id} {self.name}>"

class GroupIPModel(db.Model):
    __tablename__ = 'group_ips'
    id = db.Column(db.Integer, primary_key=True)
    group_id = db.Column(db.Integer, ForeignKey('groups.id'), nullable=False)
    ip_address = db.Column(db.String(64), nullable=False)

    def __repr__(self):
        return f"<GroupIPModel {self.id} {self.group_id} {self.ip_address}>"

