import logging
import sys
from pox.core import core

#importing controller classes
from arp_controller import ArpController
from firewall_controller import FirewallController

# Initialize logging
log = core.getLogger()
log.setLevel(logging.INFO)  # Set logging level as needed
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch = logging.StreamHandler()
ch.setFormatter(formatter)
log.addHandler(ch)

# Start the core components
def launch():
    try:
        core.registerNew(ArpController)
    except Exception as e:
        log.exception("An error occurred while registering ArpController: %s", e)
    
    try:
        core.registerNew(FirewallController)
    except Exception as e:
        log.exception("An error occurred while registering LoadBalancerController: %s", e)


