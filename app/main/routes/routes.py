from flask import request, jsonify, current_app
from app.main.routes import routes_bp
from app import db, logger
from app.main.models.models import DiscoverOSEventModel, ServiceModel

# Error handler for all exceptions
@routes_bp.errorhandler(Exception)
def handle_error(error):
    logger.exception("An error occurred: %s", error)
    return jsonify({'error': 'An internal server error occurred'}), 500

# Route to create an event
@routes_bp.route('/prosec/api/events', methods=['POST'])
def create_event():
    try:
        data = request.json

        # Extract data from the request
        event = data.get('event')
        os_type = data.get('os_type')
        ipv4 = data.get('ipv4')
        arp = data.get('arp')

        if not event or not os_type:
            logger.error('Both event and os_type are required')
            return jsonify({'error': 'Both event and os_type are required'}), 400

        # Create a new Event object
        event = DiscoverOSEventModel(event=event, ipv4=ipv4, os_type=os_type, arp=arp)
        db.session.add(event)
        db.session.commit()
        logger.info(f"Event created successfully: {event}")
        return jsonify({'message': 'Event created successfully', 'event_id': event.id}), 201

    except Exception as e:
        logger.exception("Error creating event: %s", e)
        return jsonify({'error': 'An error occurred while creating the event'}), 500

# Route to retrieve all events
@routes_bp.route('/prosec/api/events', methods=['GET'])
def get_events():
    try:
        # Retrieve all events from the database
        events = DiscoverOSEventModel.query.all()

        # Convert events to a list of dictionaries
        events_data = [{
            'event_id': event.id,
            'event': event.event,
            'ipv4': event.ipv4,
            'os_type': event.os_type,
            'arp': event.arp
        } for event in events]

        # Return the list of events as JSON
        return jsonify({'events': events_data}), 200

    except Exception as e:
        logger.exception("Error retrieving events: %s", e)
        return jsonify({'error': 'An error occurred while retrieving events'}), 500


# Route to retrieve a specific event by its ID
@routes_bp.route('/prosec/api/events/<int:event_id>', methods=['GET', 'DELETE'])
def manage_event(event_id):
    # Query the database to retrieve the event with the given ID
    event = DiscoverOSEventModel.query.get_or_404(event_id)
    if request.method =='GET':
        # Convert the event object to a dictionary for JSON serialization
        event_data = {
                'id': event.id,
                'event': event.event,
                'ipv4': event.ipv4,
                'os_type': event.os_type,
                'arp': event.arp
                }
        # Return the event data as JSON response
        return jsonify(event_data), 200

    if request.method == 'DELETE':
        db.session.delete(event)
        db.session.commit()
        logger.info(f"Event Deleted Successfully: {event}")
        # Return success message and HTTP status code
        return jsonify({'message': 'Event deleted successfully'}), 200

'''

# Route to delete an event
@routes_bp.route('/prosec/api/events/<int:event_id>', methods=['DELETE'])
def delete_event(event_id):
    try:
        # Find the event by ID
        event = DiscoverOSEventModel.query.get(event_id)
        if not event:
            logger.error(f"Event with ID {event_id} not found")
            return jsonify({'error': 'Event not found'}), 404

        # Delete the event from the database
        db.session.delete(event)
        db.session.commit()
        logger.info(f"Event with ID {event_id} deleted successfully")
        return jsonify({'message': 'Event deleted successfully'}), 200

    except Exception as e:
        logger.exception("Error deleting event: %s", e)
        return jsonify({'error': 'An error occurred while deleting the event'}), 500

# Route to delete all events
@routes_bp.route('/prosec/api/events/all', methods=['DELETE'])
def delete_all_events():
    try:
        # Delete all events from the database
        num_deleted = DiscoverOSEventModel.query.delete()
        db.session.commit()
        logger.info(f"Deleted {num_deleted} events successfully")
        return jsonify({'message': f"Deleted {num_deleted} events successfully"}), 200

    except Exception as e:
        logger.exception("Error deleting events: %s", e)
        db.session.rollback()
        return jsonify({'error': 'An error occurred while deleting events'}), 500


'''


# Other route functions with error handling...

# Route to manage services
@routes_bp.route('/prosec/api/services', methods=['GET', 'POST'])
def manage_services():
    if request.method == 'GET':
        # Retrieve all services from the database
        services = ServiceModel.query.all()

        # Convert the list of service objects to a list of dictionaries for JSON serialization
        services_data = []
        for service in services:
            service_data = {
                'id': service.id,
                'url': service.url,
                'service_name': service.service_name
            }
            services_data.append(service_data)

        # Return the list of services as a JSON response
        return jsonify(services_data), 200

    elif request.method == 'POST':
        # Create a new service based on the JSON data in the request
        data = request.json
        service = ServiceModel(url=data['url'], service_name=data['service_name'])

        # Add the new service to the database session and commit changes
        db.session.add(service)
        db.session.commit()

        # Return success message and HTTP status code
        logger.info(f"Service Created Successfully: {service}")
        return jsonify({'message': 'Service created successfully'}), 201

@routes_bp.route('/prosec/api/services/<int:service_id>', methods=['GET', 'PUT', 'DELETE'])
def manage_service(service_id):
    service = ServiceModel.query.get_or_404(service_id)

    if request.method == 'GET':
        # Convert the service object to a dictionary for JSON serialization
        service_data = {
            'id': service.id,
            'url': service.url,
            'service_name': service.service_name
        }
        # Return the service data as a JSON response
        return jsonify(service_data), 200


    if request.method == 'PUT':
        # Update the service based on the JSON data in the request
        data = request.json
        service.url = data['url']
        service.service_name = data['service_name']

        # Commit changes to the database
        db.session.commit()
        logger.info(f"Service Updated Successfully: {service}")

        # Return success message and HTTP status code
        return jsonify({'message': 'Service updated successfully'}), 200

    if request.method == 'DELETE':
        # Delete the service from the database
        db.session.delete(service)
        db.session.commit()
        logger.info(f"Service Deleted Successfully: {service}")

        # Return success message and HTTP status code
        return jsonify({'message': 'Service deleted successfully'}), 200

