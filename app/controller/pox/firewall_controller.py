from pox.core import core
import pox.openflow.libopenflow_01 as of
import requests
from pox.lib.addresses import IPAddr
import json
import sqlite3
import time
import os
import traceback
from threading import Thread
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from pox.lib.revent import EventMixin
import logging

log = core.getLogger()
# Configure logging to file
logging.basicConfig(filename='/home/ubuntu/prosec/logs/controller.log', level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')

# Add a handler to capture console messages
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.DEBUG)  # Set the desired console log level
log.addHandler(console_handler)

# Set the logging level to capture messages of desired severity
log.setLevel(logging.DEBUG)  # Set the desired logging level

db_path = "/home/ubuntu/prosec/instance/app.db"

class DBChangeHandler(FileSystemEventHandler):
    def __init__(self, controller):
        self.controller = controller

    def on_modified(self, event):
        if event.src_path.endswith(db_path):
            log.info("Detected change in database")
            self.controller.handle_db_change()

class FirewallController(EventMixin):
    def __init__(self):
        super(FirewallController, self).__init__()
        self.listenTo(core.openflow)        
        log.info("FirewallController initialized and listeners added.")
        self.rule_url = "http://localhost:5000/prosec/api/firewall/rules"
        self.group_url = "http://localhost:5000/prosec/api/groups"
        self.rules, self.groups = self.fetch_rules_and_groups()
        self.connections = {}
        self.flow_stats = []

        # Connect to the SQLite database
        self.db_conn = sqlite3.connect(db_path, check_same_thread=False)
        self.db_cur = self.db_conn.cursor()

        # Start file monitoring for the database file
        self.observer = Observer()
        self.event_handler = DBChangeHandler(self)
        self.observer.schedule(self.event_handler, \
             path=os.path.dirname(db_path), recursive=False)

        self.observer.start()

    def fetch_rules_and_groups(self):
        try:
            rules_data = self.fetch(self.rule_url)
            groups_data = self.fetch(self.group_url)
            rules = rules_data.get("rules", [])
            groups = self.convert_groups_to_dict(groups_data.get("groups", []))
            log.info("Fetched firewall rules: %s", rules)
            log.info("Fetched groups: %s", groups)
            return rules, groups
        except Exception as e:
            log.error("Error fetching firewall rules and groups: %s", e)
            traceback.print_exc()  # Print traceback for detailed error information
            return [], {}

    def fetch(self, url):
        response = requests.get(url)
        if response.status_code == 200:
            return response.json()
        else:
            log.error("Failed to fetch data from %s: %s", url, response.status_code)
            return {}

    def convert_groups_to_dict(self, groups):
        groups_dict = {}
        for group in groups:
            groups_dict[str(group['group_id'])] = group['ips']
        return groups_dict

    def _handle_ConnectionUp(self, event):
        log.info("Switch %s has connected", event.dpid)
        self.install_rules(event.connection)
        self.connections[event.dpid] = event.connection

    def _handle_ConnectionDown(self, event):
        log.info("Switch %s has disconnected", event.dpid)
        if event.dpid in self.connections:
            del self.connections[event.dpid]

    def install_rules(self, connection):
        for rule in self.rules:
            log.info("Processing rule: %s", rule)
            src_ips = self.expand_groups_if_needed(rule.get("nw_src"))
            dst_ips = self.expand_groups_if_needed(rule.get("nw_dst"))

            if not src_ips:
                src_ips = [None]
            if not dst_ips:
                dst_ips = [None]

            for src_ip in src_ips:
                for dst_ip in dst_ips:
                    rule_copy = rule.copy()
                    if src_ip:
                        rule_copy["nw_src"] = src_ip
                    if dst_ip:
                        rule_copy["nw_dst"] = dst_ip
                    self.install_rule(connection, rule_copy)

    def expand_groups_if_needed(self, field):
        if field and field.startswith("group:"):
            group_id = field.split(":")[1]
            log.info("Expanding group: %s", group_id)
            if group_id in self.groups:
                return self.groups[group_id]
            else:
                log.error("Group %s not found in groups", group_id)
                return []
        return [field] if field else []

    def handle_db_change(self):
        try:
            log.info("Handling database change...")
            # Fetch changes from the rules and groups tables
            new_rules = self.fetch(self.rule_url).get("rules", [])
            group_changes = self.fetch(self.group_url).get("groups", [])
            log.info("Fetched group changes: %s", group_changes)

            self.handle_new_rules(new_rules)
            self.handle_group_changes(group_changes)
        except Exception as e:
            log.error("Error handling database change: %s", e)
            traceback.print_exc()  # Print traceback for detailed error information

    def handle_new_rules(self, new_rules):
        self.rules = new_rules
        for connection in self.connections.values():
            self.install_rules(connection)

    def handle_group_changes(self, group_changes):
        self.groups = self.convert_groups_to_dict(group_changes)
        for connection in self.connections.values():
            self.install_rules(connection)

    def install_rule(self, connection, rule):
        flow_mod = of.ofp_flow_mod()
        flow_mod.match = self.create_match(rule)

        if rule["action"] == "allow":
            flow_mod.actions.append(of.ofp_action_output(port=of.OFPP_NORMAL))

        # Set the priority of the flow_mod
        flow_mod.priority = rule.get("priority", 0)

        connection.send(flow_mod)
        log.info("Installed rule: %s", rule)


    def create_match(self, rule):
        try:
            match = of.ofp_match()

            # Set Ethernet type
            if "dl_type" in rule and rule["dl_type"] != "any":
                match.dl_type = int(rule["dl_type"], 0)

            # Set network protocol
            if "nw_proto" in rule and rule["nw_proto"] != "any":
                match.nw_proto = self.convert_proto_to_int(rule["nw_proto"])

            # Set source and destination transport ports
            if "tp_src" in rule and rule["tp_src"] != "any":
                match.tp_src = int(rule["tp_src"])
            if "tp_dst" in rule and rule["tp_dst"] != "any":
                match.tp_dst = int(rule["tp_dst"])

            # Set source IP address
            if "nw_src" in rule and rule["nw_src"] != "any":
                match.nw_src = IPAddr(rule["nw_src"])

            # Set destination IP address
            if "nw_dst" in rule and rule["nw_dst"] != "any":
                try:
                    if rule["nw_dst"].startswith("group:"):
                        dst_ips = self.expand_groups(rule["nw_dst"])
                        if dst_ips:
                            match.nw_dst = IPAddr(dst_ips[0])
                    else:
                        match.nw_dst = IPAddr(rule["nw_dst"])
                except Exception as e:
                    log.error("Invalid IP address string for nw_dst: %s", e)

            return match
        except Exception as e:
            log.exception("Error creating match: %s", e)
            return None

    def convert_proto_to_int(self, proto):
        if proto == "any":
            return None
        elif proto.lower() == "icmp":
            return 1
        elif proto.lower() == "udp":
            return 17
        elif proto.lower() == "tcp":
            return 6
        else:
            return int(proto)

    def get_flow_stats(self):
        self.flow_stats = []
        for connection in self.connections.values():
            connection.send(of.ofp_stats_request(body=of.ofp_flow_stats_request()))

    def _handle_FlowStatsReceived(self, event):
        for flow in event.stats:
            self.flow_stats.append({
                "priority": flow.priority,
                "dl_type": flow.match.dl_type,
                "nw_proto": flow.match.nw_proto,
                "tp_src": flow.match.tp_src,
                "tp_dst": flow.match.tp_dst,
                "nw_src": str(flow.match.nw_src),
                "nw_dst": str(flow.match.nw_dst),
                "actions": [str(action) for action in flow.actions]
            })
            log.info("Flow: %s", flow)

    def get_formatted_flow_stats(self):
        formatted_stats = []
        for flow in self.flow_stats:
            rule = {
                "priority": flow["priority"],
                "dl_type": flow["dl_type"],
                "nw_proto": flow["nw_proto"],
                "tp_src": flow["tp_src"],
                "tp_dst": flow["tp_dst"],
                "nw_src": flow["nw_src"],
                "nw_dst": flow["nw_dst"],
                "action": "allow" if any("OUTPUT" in action for action in flow["actions"]) else "deny"
            }
            formatted_stats.append(rule)
        return formatted_stats


