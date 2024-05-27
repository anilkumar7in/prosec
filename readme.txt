1. Modules and structure of the App.

prosec/
│
├── app/
│   ├── __init__.py
│   ├── main/
│   │   ├── __init__.py
│   │   ├── routes/
│   │   │    ├── __init__.py 
│   │   │    ├── common_routes.py
│   │   │    ├── fireall_routes.py
│   │   │    └── grouping_routes.py
│   │   │
│   │   └── models/
│   │   │    ├── __init__.py
│   │   │    ├── common_model.py
│   │   │    ├── grouping_model.py
│   │   │    └── firewall_model.py
│   │   │
│   ├── controller/
│   │   ├── __init__.py
│   │   ├── pox/
│   │   │   ├── main_controller.py
│   │   │   ├── firewall_controller.py
│   │   │   ├── arp_controller.py
│   │   │
│   │   └── ryu/
│   │   │
│   ├── extensions/
│   │   ├── __init__.py
│   │   └── dcn.py
│   │   │
│   └── utils/
│       ├── __init__.py
│       └── os_scanner.py
│
├── celery_worker.py
├── config.py
├── requirements.txt
├── run.py
└── logs/

2. Running the Application
   a. Start the Celery (For distributed task queue for handling asynchronous tasks) with command:
      "ubuntu@ubuntu-mgmt:~$ celery -A celery_worker.c^Cery worker --loglevel=INFO&"

   b. Start the main app with gunicorn (Gunicorn (Green Unicorn) is a Python WSGI HTTP server for UNIX)
      It forks multiple worker processes to handle requests. 
      "ubuntu@ubuntu-mgmt:~$ gunicorn -w 1 -b 0.0.0.0:5001 run:app&

   c. There are 2 ways the controller can be run, one through the integration with flask __init__.py and other with ./pox.py independently. 
      The command line for both from the pox directory is:
      "./pox.py main_controller"

3. APIs:

----------------------------------------------------
a. Events:
    To get all events:
    GET 192.168.233.130:5000/prosec/api/events
   Response:
   {
     "events": [
        {
            "arp": "be:e2:2d:e4:c5:57",
            "event": "os_discovered",
            "event_id": 1,
            "ipv4": "10.0.0.2",
            "os_type": "Windows"
        },
    }

----------------------------------------------------
b. To delete specific event:
    DELETE 192.168.233.130:5000/prosec/api/events/<event_id>

----------------------------------------------------
c. To delete all events:
    DELETE 192.168.233.130:5000/prosec/api/events/all

----------------------------------------------------
c. Third party Registration for any callbacks when an event occur:


----------------------------------------------------
d. Firewall APIs: Get all rules:
   GET 192.168.233.130:5000/prosec/api/firewall/rules

----------------------------------------------------
e. Specific Rule:
   GET 192.168.233.130:5000/prosec/api/firewall/rules/<int:rule_id>
   Response:
   {
    "rules": [
        {
            "action": "allow",
            "dl_type": "0x0806",
            "id": 1,
            "nw_dst": "any",
            "nw_proto": "any",
            "nw_src": "any",
            "priority": 1000,
            "tp_dst": "any",
            "tp_src": "any"
        }
   }


----------------------------------------------------
d. Create Firewall Rule:
   POST 192.168.233.130:5000/prosec/api/firewall/rules
   {
    "rules": [
        {
            "action": "allow",
            "dl_type": "0x0806",
            "nw_dst": "any",
            "nw_proto": "any",
            "nw_src": "any",
            "priority": 1000,
            "tp_dst": "any",
            "tp_src": "any"
        }
   } 

----------------------------------------------------
e. Delete a specific Firewall Rule:
   DELETE 192.168.233.130:5000/prosec/api/firewall/rules/1

----------------------------------------------------
f. Groups APIs:
   GET all groups:
   GET 192.168.233.130:5000/prosec/api/groups
   RESPONSE:
   {
    "groups": [
        {
            "group_id": 1,
            "group_name": "windows_group",
            "ips": [
                "10.0.0.1",
                "10.0.0.3",
                "10.0.0.6",
                "10.0.0.7",
                "10.0.0.8",
                "10.0.0.9",
                "10.0.0.2"
            ]
        },
        {
            "group_id": 2,
            "group_name": "linux_group",
            "ips": []
        }
     ]
   }  

----------------------------------------------------

g. Get Specific Group:
   GET 192.168.233.130:5000/prosec/api/groups/1
   RESPONSE:
   {
    "group_id": 1,
    "group_name": "windows_group",
    "ips": [
        "10.0.0.1",
        "10.0.0.3",
        "10.0.0.6",
        "10.0.0.7",
        "10.0.0.8",
        "10.0.0.9",
        "10.0.0.2"
     ]
   }

----------------------------------------------------

h. delete specific group:
   DELETE 192.168.233.130:5000/prosec/api/groups/1
----------------------------------------------------

i. CREATE a group:
   POST 192.168.233.130:5000/prosec/api/groups
   BODY:
   {
    "group_name": "windows_group",
    "ips": []
   }

----------------------------------------------------
j. delete an IP from group:
   DELETE  192.168.233.130:5000/prosec/api/groups/ips
   BODY:
   {"ip_address":"10.0.0.2"}


----------------------------------------------------
