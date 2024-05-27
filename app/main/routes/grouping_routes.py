# app/main/routes.py

from flask import Blueprint, request, jsonify
from app import db, logger
from app.main.routes import routes_bp

from app.main.models.grouping_model import GroupModel, GroupIPModel

# Error handler for all exceptions
@routes_bp.errorhandler(Exception)
def handle_error(error):
    logger.exception("An error occurred: %s", error)
    return jsonify({'error': 'An internal server error occurred'}), 500

# Route to create a new group
@routes_bp.route('/prosec/api/groups', methods=['POST'])
def create_group():
    data = request.get_json()

    # Extract group data from request
    group_name = data.get('name')

    if not group_name:
        logger.error("Group name is required")
        return jsonify({'error': 'Group name is required'}), 400

    # Check if group with the same name already exists
    existing_group = GroupModel.query.filter_by(name=group_name).first()
    if existing_group:
        logger.error("Group with the same name already exists: %s", group_name)
        return jsonify({'error': 'Group with the same name already exists'}), 400

    # Create a new group
    new_group = GroupModel(name=group_name)
    logger.info("Creating a new group: %s", group_name)

    # Add the new group to the database
    db.session.add(new_group)
    db.session.commit()
    logger.info("Group created successfully: %s", group_name)

    return jsonify({'message': 'Group created successfully', 'group_id': new_group.id}), 201

# Route to add an IP address to a group
@routes_bp.route('/prosec/api/groups/<int:group_id>/ip', methods=['POST'])
def add_ip_to_group(group_id):
    data = request.get_json()

    # Extract IP data from request
    ip_address = data.get('ip_address')

    if not ip_address:
        logger.error("IP address is required")
        return jsonify({'error': 'IP address is required'}), 400

    # Check if group exists
    group = GroupModel.query.get(group_id)
    if not group:
        logger.error("Group does not exist: %s", group_id)
        return jsonify({'error': 'Group does not exist'}), 404

    # Check if IP address is already in the group
    existing_ip = GroupIPModel.query.filter_by(group_id=group_id, ip_address=ip_address).first()
    if existing_ip:
        logger.error("IP address already exists in the group: %s", ip_address)
        return jsonify({'error': 'IP address already exists in the group'}), 400

    # Add IP address to the group
    new_ip = GroupIPModel(group_id=group_id, ip_address=ip_address)
    logger.info("Adding IP address %s to group %s", ip_address, group.name)

    # Add the new IP address to the database
    db.session.add(new_ip)
    db.session.commit()
    logger.info("IP address added to group successfully: %s", ip_address)

    return jsonify({'message': 'IP address added to group successfully', 'ip_address': new_ip.ip_address}), 201


@routes_bp.route('/prosec/api/groups/<int:group_id>/ip', methods=['DELETE'])
def remove_ip_from_group(group_id):
    data = request.get_json()

    # Extract IP data from request
    ip_address = data.get('ip_address')

    if not ip_address:
        return jsonify({'error': 'IP address is required'}), 400

    # Check if group exists
    group = GroupModel.query.get(group_id)
    if not group:
        return jsonify({'error': 'Group does not exist'}), 404

    # Check if IP address exists in the group
    existing_ip = GroupIPModel.query.filter_by(group_id=group_id, ip_address=ip_address).first()
    if not existing_ip:
        return jsonify({'error': 'IP address not found in the group'}), 404

    # Delete the IP address from the group
    db.session.delete(existing_ip)
    db.session.commit()

    return jsonify({'message': 'IP address removed from group successfully', 'ip_address': ip_address}), 200


# Route to retrieve all groups
@routes_bp.route('/prosec/api/groups', methods=['GET'])
def get_all_groups():
    groups = GroupModel.query.all()
    group_data = []

    for group in groups:
        ips = [ip.ip_address for ip in group.ips]
        group_data.append({
            'group_id': group.id,
            'group_name': group.name,
            'ips': ips
        })

    return jsonify({'groups': group_data})


# Route to retrieve IP addresses for a specific group
@routes_bp.route('/prosec/api/groups/<int:group_id>', methods=['GET'])
def get_group_ips(group_id):
    group = GroupModel.query.get(group_id)
    if not group:
        return jsonify({'error': 'Group not found'}), 404

    ips = [ip.ip_address for ip in group.ips]
    return jsonify({'group_id': group_id, 'group_name': group.name, 'ips': ips})


# Route to delete a group
@routes_bp.route('/prosec/api/groups/<int:group_id>', methods=['DELETE'])
def delete_group(group_id):
    group = GroupModel.query.get(group_id)
    if not group:
        return jsonify({'error': 'Group not found'}), 404

    # Delete the group from the database
    db.session.delete(group)
    db.session.commit()

    logger.info("Group deleted successfully: %s", group.name)

    return jsonify({'message': 'Group deleted successfully'}), 200


