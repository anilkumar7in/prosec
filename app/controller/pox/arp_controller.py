from pox.core import core
import pox.openflow.libopenflow_01 as of
from pox.lib.packet.ethernet import ethernet
from pox.lib.packet.arp import arp
from pox.lib.packet.ipv4 import ipv4
from pox.lib.packet.icmp import icmp
from pox.lib.revent import EventMixin
import requests
import logging
import logging

# Get a reference to the log
log = core.getLogger()

# Configure logging to file
logging.basicConfig(filename='/home/ubuntu/prosec/logs/controller.log', level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(message)s')

# Add a handler to capture console messages
console_handler = logging.StreamHandler()
console_handler.setLevel(logging.DEBUG)  # Set the desired console log level
log.addHandler(console_handler)

# Set the logging level to capture messages of desired severity
log.setLevel(logging.DEBUG)  # Set the desired logging level

# Dictionaries to keep track of reported IP and MAC addresses
reported_ips = set()
reported_macs = set()

class ArpController(EventMixin):
    def __init__(self):
        super(ArpController, self).__init__()
        self.listenTo(core.openflow)
        self.mac_to_port = {}  # Store MAC to port mapping for each switch
        self.mac_to_ip = {}    # Store MAC to IP mapping
        log.info("ArpController initialized and listeners added.")

    def _handle_ConnectionUp(self, event):
        log.info("Switch %s has come up.", event.dpid)
        #self.log_mac_addresses(event.dpid)
        self.install_arp_rules(event.connection)

    def install_arp_rules(self, connection):
        arp_rule = of.ofp_flow_mod()
        arp_rule.match.dl_type = ethernet.ARP_TYPE
        arp_rule.priority = 10000
        arp_rule.actions.append(of.ofp_action_output(port=of.OFPP_CONTROLLER))
        connection.send(arp_rule)

    def _handle_PacketIn(self, event):
        try:
            packet = event.parsed
            if not packet.parsed:
                log.warning("Ignoring incomplete packet")
                return
            self.handle_packet(event, packet)
        except Exception as e:
            log.exception("Error handling PacketIn event: %s", e)

    def handle_packet(self, event, packet):
        if packet.type == ethernet.ARP_TYPE:
            arp_packet = packet.payload
            if isinstance(arp_packet, arp):
                self.handle_arp_packet(event, arp_packet)

        elif packet.type == ethernet.IP_TYPE:
            ip_packet = packet.payload
            if ip_packet.protocol == ipv4.ICMP_PROTOCOL:
                self.handle_icmp_packet(event, packet)

    def handle_arp_packet(self, event, arp_packet):
        try:
            arp_request = arp_packet.find('arp')
            if arp_request is not None and arp_request.opcode == arp_request.REQUEST:
                source_ip = arp_request.protosrc
                source_mac = arp_request.hwsrc

                # Learn the MAC to port mapping
                previous_port = self.mac_to_port.get((event.dpid, source_mac))
                self.mac_to_port[(event.dpid, source_mac)] = event.port
                self.mac_to_ip[source_mac] = source_ip

                if source_ip not in reported_ips and source_mac not in reported_macs:
                    log.info("Received ARP request with source IP %s and source MAC %s", source_ip, source_mac)
                    reported_ips.add(source_ip)
                    reported_macs.add(source_mac)
                    os_type = self.scan_os(source_ip)
                    self.send_event(source_ip, source_mac, os_type)

                # Forward the ARP packet to all ports except the input port
                self.flood_packet(event)

                # Log MAC addresses if there's a change
                if previous_port != event.port:
                    self.log_mac_addresses(event.dpid)

        except Exception as e:
            log.exception("Error handling ARP packet: %s", e)

    def handle_icmp_packet(self, event, packet):
        try:
            ip_packet = packet.payload
            dest_mac = packet.dst

            # Check if we have learned the destination MAC address
            if (event.dpid, dest_mac) in self.mac_to_port:
                out_port = self.mac_to_port[(event.dpid, dest_mac)]
                msg = of.ofp_packet_out()
                msg.data = event.ofp
                msg.actions.append(of.ofp_action_output(port=out_port))
                event.connection.send(msg)
            else:
                # Flood the packet if destination is unknown
                self.flood_packet(event)

        except Exception as e:
            log.exception("Error handling ICMP packet: %s", e)

    def flood_packet(self, event):
        try:
            msg = of.ofp_packet_out()
            msg.data = event.ofp
            msg.actions.append(of.ofp_action_output(port=of.OFPP_FLOOD))
            event.connection.send(msg)
        except Exception as e:
            log.exception("Error flooding packet: %s", e)

    def send_event(self, ip_address, mac_address, os_type):
        try:
            url = "http://localhost:5000/prosec/api/events"
            data = {
                'event': 'os_discovered',
                'os_type': os_type,
                'ipv4': str(ip_address),
                'arp': str(mac_address)
            }
            response = requests.post(url, json=data)
            if response.status_code == 201:
                log.info("Event sent successfully: %s", data)
            else:
                log.error("Failed to send event: %s", response.text)
        except Exception as e:
            log.error("Error sending event: %s", e)

    def scan_os(self, ip):
        # Placeholder method for OS detection
        os_type = "Windows"  # Example: replace with actual OS detection logic
        return os_type

    def log_mac_addresses(self, dpid):
        log.info("+-------------------+---------------+-----------------------+------------------+")
        log.info("| Switch            | Port          | MAC Address           | IP Address       |")
        log.info("+-------------------+---------------+-----------------------+------------------+")
        for (sw, mac), port in self.mac_to_port.items():
            if sw == dpid:
                ip = self.mac_to_ip.get(mac, "N/A")
                log.info("| %-17s | %-13s | %-21s | %-17s |", sw, port, mac, ip)
        log.info("+-------------------+---------------+-----------------------+------------------+")


