# This is a Sample 3rd party Firewall Service which can be registered with this App to get a callback notification from app about the 
# data change (basically when a new os/mac/ip is detected and then it just responds with 200 Ok. This is just for the demonstration example to
# show how a 3rd party firewall service can also be attached to this app.

import json
import asyncio
import logging
from flask import Flask, request, jsonify
from queue import Queue
from rich.pretty import pretty_repr

fire_app = Flask(__name__)

# Configure logging
logging.basicConfig(filename='fire_app.log', level=logging.INFO)
logger = logging.getLogger(__name__)

# Create a queue to store incoming notifications
notification_queue = Queue()

# Define a route to receive notification events
@fire_app.route('/fw_service/notify', methods=['POST'])
def handle_notification():
    data = request.json
    if data:
        # Log the received notification event
        logger.info(f"Notification Event Received:\n{json.dumps(data, indent=4)}")

        # Put the notification into the queue for asynchronous processing
        try:
            notification_queue.put(data)
            logger.info(f"Notification event queued successfully:{notification_queue.get()}")
            return jsonify({'message': 'Notification event received and queued successfully'}), 200
        except Exception as e:
            logger.error(f"Error while queuing notification: {str(e)}")
            return jsonify({'error': 'Internal server error'}), 500
    else:
        error_msg = 'No data received in the notification event'
        logger.error(error_msg)
        return jsonify({'error': error_msg}), 400

# Function to process notifications from the queue asynchronously
async def process_notifications():
    logger.info("Process notifications task started.")
    while True:
        # Retrieve notification from the queue
        host_ip, os_type = notification_queue.get()
        logger.info(f"Processing notification event from queue - Host IP: {host_ip}, OS Type: {os_type}")

        # Call function to punch rules on the SDN controller
        try:
            await punch_rules_to_sdn_controller(host_ip, os_type)
            # Mark notification as processed
            notification_queue.task_done()
            logger.info("Notification processed successfully and marked as done.")
        except Exception as e:
            logger.error(f"Error processing notification: {e}")


# Asynchronous function to punch rules on the SDN controller
async def punch_rules_to_sdn_controller(host_ip, os_type):
    # Implement logic to communicate with the SDN controller
    # and punch rules based on the received host IP and OS type
    # Example:
    # sdn_controller.punch_rules(host_ip, os_type)
    logger.info(f"Punching rules to SDN controller for host IP: {host_ip}, OS type: {os_type}")

async def main():
    fire_app.run(debug=True)
    logger.info("Main task started.")
    # Start processing notifications asynchronously
    task = asyncio.create_task(process_notifications())
    logger.info("Process notifications task created.")

    # Run the Flask app
    await task
    logger.info("Main task finished.")

if __name__ == '__main__':
    asyncio.run(main())

