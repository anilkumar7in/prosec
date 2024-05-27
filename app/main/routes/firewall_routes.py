from flask import request, jsonify, current_app
from app.main.routes import routes_bp
from app import db, logger
from app.main.models.firewall_model import FirewallRuleModel
from app.main.models.grouping_model import GroupModel, GroupIPModel
#from app.controller.pox.an_fw_controller import FirewallController

# Error handler for all exceptions
@routes_bp.errorhandler(Exception)
def handle_error(error):
    logger.exception("An error occurred: %s", error)
    return jsonify({'error': 'An internal server error occurred'}), 500


# Function to fetch group IPs by group name
def get_group_ips(group_name):
    group = GroupModel.query.filter_by(name=group_name).first()
    if group:
        group_ips = GroupIPModel.query.filter_by(group_id=group.id).all()
        return [ip.ip_address for ip in group_ips]
    return []


@routes_bp.route('/prosec/api/firewall/rules', methods=['GET'])
def get_firewall_rules():
    rules = FirewallRuleModel.query.all()
    return jsonify({
        "rules": [
            {
                "id": rule.id,
                "priority": rule.priority,
                "dl_type": rule.dl_type,
                "nw_proto": rule.nw_proto,
                "tp_src": rule.tp_src,
                "tp_dst": rule.tp_dst,
                "nw_src": rule.nw_src,
                "nw_dst": rule.nw_dst,
                "action": rule.action,
            }
            for rule in rules
        ]
    })


@routes_bp.route('/prosec/api/firewall/rules/<int:rule_id>', methods=['DELETE'])
def delete_firewall_rule(rule_id):
    try:
        # Find the rule by ID
        rule = FirewallRuleModel.query.get(rule_id)
        if not rule:
            return jsonify({"error": "Firewall rule not found"}), 404

        # Delete the rule
        db.session.delete(rule)
        db.session.commit()

        return jsonify({"message": "Firewall rule deleted successfully"}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@routes_bp.route('/prosec/api/firewall/rules', methods=['POST'])
def create_firewall_rule():
    try:
        data = request.get_json()

        # Validate input data
        required_fields = ['action', 'dl_type', 'nw_proto', 'tp_src', 'tp_dst', 'nw_src', 'nw_dst', 'priority']
        for field in required_fields:
            if field not in data:
                return jsonify({"error": f"'{field}' is required"}), 400

        # Create a new FirewallRuleModel object
        new_rule = FirewallRuleModel(
            action=data['action'],
            dl_type=data['dl_type'],
            nw_proto=data['nw_proto'],
            tp_src=data['tp_src'],
            tp_dst=data['tp_dst'],
            nw_src=data['nw_src'],
            nw_dst=data['nw_dst'],
            priority=data['priority']
        )

        # Save the new rule to the database
        db.session.add(new_rule)
        db.session.commit()

        return jsonify({"message": "Firewall rule created successfully", "rule": data}), 201

    except Exception as e:
        return jsonify({"error": str(e)}), 500

'''
@routes_bp.route('/prosec/api/firewall/realized-rules', methods=['GET'])
def get_realized_firewall_rules():
    try:
        controller = core.FirewallController
        controller.get_flow_stats()

        # Trigger POX to get flow stats
        #requests.get('http://localhost:8080/get_flow_stats')
        
        # Wait for a short time to allow POX to process the request and log the results
        sleep(2)
        
        # Read the flow stats from the POX log (for simplicity)
        with open('logs/controller.log', 'r') as f:
            logs = f.readlines()

        # Filter the flow stats logs
        flow_stats = [log for log in logs if 'Flow:' in log]

        # Format the flow stats
        formatted_stats = format_flow_stats(flow_stats)

        return jsonify({"realized_rules": formatted_stats}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

def format_flow_stats(flow_stats):
    formatted_stats = []
    for flow in flow_stats:
        flow_data = eval(flow.split("Flow: ")[1])
        rule = {
            "priority": flow_data["priority"],
            "dl_type": flow_data["dl_type"],
            "nw_proto": flow_data["nw_proto"],
            "tp_src": flow_data["tp_src"],
            "tp_dst": flow_data["tp_dst"],
            "nw_src": flow_data["nw_src"],
            "nw_dst": flow_data["nw_dst"],
            "action": "allow" if any("OUTPUT" in action for action in flow_data["actions"]) else "deny"
        }
        formatted_stats.append(rule)
    return formatted_stats

'''
# Other route functions with error handling...

