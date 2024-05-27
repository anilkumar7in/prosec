# app/utils/os_scanner.py
from app import logger
import requests
import nmap

def scan_os(ip_address):
    nm = nmap.PortScanner()
    nm.scan(ip_address, arguments='-O')
    if ip_address in nm.all_hosts():
        os_type = nm[ip_address]['osmatch'][0]['name']
        logger.info("OS detected for IP %s: %s", ip_address, os_type)
        #temp
        os_type = "Windows"
        return os_type
    else:
        logger.warning("No OS detected for IP %s", ip_address)
        return None


def send_os_discovery_event(event_api_url, event, src_ip, src_mac, os_type):
    payload = {
        "event": event,
        "ipv4": src_ip,
        "arp": src_mac,
        "os_type": os_type
    }
    try:
        if event == 'os_detected':
            response = requests.post(event_api_url, json=payload)
        if event == 'os_deleted':
            response = requests.delete(event_api_url, json=payload)
        response.raise_for_status()
        logger.info("API call to event endpoint successful")
    except Exception as e:
        logger.error("Failed to make API call to event endpoint: %s", e)

