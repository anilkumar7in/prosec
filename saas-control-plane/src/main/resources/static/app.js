const PROTOBUF = 'application/x-protobuf';
const API_KEY_HEADER = 'X-Prosec-Api-Key';
const SITE_TOKEN_HEADER = 'X-Prosec-Site-Token';
const utf8 = new TextEncoder();
const textDecoder = new TextDecoder();

// In-memory session state (never persisted).
const state = {
  tenantId: '',
  siteId: '',
  siteToken: '',
  objectId: ''
};

/* ========================= protobuf encoding ========================= */

function bytes(parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(length);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}

function varint(value) {
  let n = BigInt(value);
  const out = [];
  while (n > 127n) {
    out.push(Number((n & 127n) | 128n));
    n >>= 7n;
  }
  out.push(Number(n));
  return Uint8Array.from(out);
}

function key(field, wireType) {
  return varint((field << 3) | wireType);
}

function stringField(field, value) {
  if (!value) return new Uint8Array();
  const payload = utf8.encode(value);
  return bytes([key(field, 2), varint(payload.length), payload]);
}

function bytesField(field, payload) {
  return bytes([key(field, 2), varint(payload.length), payload]);
}

function numberField(field, value) {
  if (!value) return new Uint8Array();
  return bytes([key(field, 0), varint(value)]);
}

function nestedField(field, payload) {
  return bytes([key(field, 2), varint(payload.length), payload]);
}

function mapEntry(field, entryKey, entryValue) {
  const payload = bytes([stringField(1, entryKey), stringField(2, entryValue)]);
  return nestedField(field, payload);
}

/* ========================= protobuf decoding ========================= */

function readVarint(view, offset) {
  let shift = 0n;
  let result = 0n;
  let pos = offset;
  while (pos < view.length) {
    const byte = BigInt(view[pos++]);
    result |= (byte & 127n) << shift;
    if ((byte & 128n) === 0n) break;
    shift += 7n;
  }
  return [Number(result), pos];
}

function parseMessage(view) {
  const fields = new Map();
  let offset = 0;
  while (offset < view.length) {
    const [tag, next] = readVarint(view, offset);
    offset = next;
    const field = tag >> 3;
    const wire = tag & 7;
    let value;
    if (wire === 0) {
      [value, offset] = readVarint(view, offset);
    } else if (wire === 2) {
      const [length, start] = readVarint(view, offset);
      offset = start;
      value = view.slice(offset, offset + length);
      offset += length;
    } else if (wire === 5) {
      value = view.slice(offset, offset + 4);
      offset += 4;
    } else if (wire === 1) {
      value = view.slice(offset, offset + 8);
      offset += 8;
    } else {
      throw new Error(`Unsupported protobuf wire type ${wire}`);
    }
    if (!fields.has(field)) fields.set(field, []);
    fields.get(field).push(value);
  }
  return fields;
}

function fieldStr(fields, no) {
  const values = fields.get(no);
  return values ? textDecoder.decode(values[0]) : '';
}

function fieldStrs(fields, no) {
  return (fields.get(no) || []).map((value) => textDecoder.decode(value));
}

function fieldNum(fields, no) {
  const values = fields.get(no);
  return values ? Number(values[0]) : 0;
}

function fieldMsg(fields, no) {
  const values = fields.get(no);
  return values ? parseMessage(values[0]) : null;
}

function fieldMsgs(fields, no) {
  return (fields.get(no) || []).map((value) => parseMessage(value));
}

/* ========================= request encoders ========================= */

function createTenantRequest(displayName) {
  return bytes([stringField(1, displayName)]);
}

function connectSiteRequest(tenantId, displayName, region) {
  return bytes([stringField(1, tenantId), stringField(2, displayName), stringField(3, region)]);
}

function heartbeatRequest(tenantId, siteId) {
  return bytes([stringField(1, tenantId), stringField(2, siteId)]);
}

function upsertContainerRequest(s) {
  const workload = bytes([
    stringField(4, s.clusterName),
    stringField(5, s.namespace),
    stringField(6, s.podName),
    stringField(7, s.containerName),
    stringField(8, s.image),
    stringField(9, 'sha256:ui-demo'),
    numberField(10, 1),
    mapEntry(11, 'app', s.containerName),
    mapEntry(11, 'namespace', s.namespace),
    stringField(12, s.ipAddress)
  ]);
  return bytes([stringField(1, s.tenantId), stringField(2, s.siteId), nestedField(3, workload)]);
}

function listInventoryRequest(tenantId, siteId, namespace) {
  return bytes([stringField(1, tenantId), stringField(2, siteId), stringField(3, namespace)]);
}

function createGlobalObjectRequest(displayName) {
  return bytes([numberField(1, 1), stringField(2, displayName), bytesField(3, utf8.encode('policy-version:1'))]);
}

function grantRequest(s) {
  const grantParts = [
    stringField(1, s.objectId),
    stringField(2, s.tenantId),
    stringField(3, s.userId),
    stringField(4, 'SECURITY_ADMIN'),
    stringField(5, 'read')
  ];
  if (s.action && s.action !== 'read') {
    grantParts.push(stringField(5, s.action));
  }
  return nestedField(1, bytes(grantParts));
}

function listGrantsRequest(tenantId) {
  return bytes([stringField(1, tenantId)]);
}

function revokeGrantRequest(tenantId, objectId) {
  return bytes([stringField(1, tenantId), stringField(2, objectId)]);
}

function evaluateRequest(s) {
  const parts = [
    stringField(1, s.objectId),
    stringField(2, s.tenantId),
    stringField(3, s.userId),
    stringField(4, s.action)
  ];
  for (const role of s.roles || []) {
    parts.push(stringField(5, role));
  }
  return bytes(parts);
}

function listEventsRequest(tenantId, eventType, afterSeq, limit) {
  return bytes([
    stringField(1, tenantId),
    stringField(2, eventType),
    numberField(3, afterSeq),
    numberField(4, limit)
  ]);
}

/* ========================= response decoders ========================= */

function parseTenantResponse(view) {
  const tenant = fieldMsg(parseMessage(view), 1);
  return { id: tenant ? fieldStr(tenant, 1) : '' };
}

function parseSiteResponse(view) {
  const fields = parseMessage(view);
  const site = fieldMsg(fields, 1);
  const credential = fieldMsg(fields, 2);
  return {
    id: site ? fieldStr(site, 1) : '',
    token: credential ? fieldStr(credential, 2) : ''
  };
}

function parseGlobalObjectResponse(view) {
  const object = fieldMsg(parseMessage(view), 1);
  return {
    id: object ? fieldStr(object, 1) : '',
    displayName: object ? fieldStr(object, 3) : '',
    version: object ? fieldNum(object, 6) : 0
  };
}

function parseAccessDecision(view) {
  const fields = parseMessage(view);
  return { effect: fieldNum(fields, 1), reason: fieldStr(fields, 2) };
}

function parseInventoryResponse(view) {
  return fieldMsgs(parseMessage(view), 1).map((workload) => ({
    siteId: fieldStr(workload, 3),
    cluster: fieldStr(workload, 4),
    namespace: fieldStr(workload, 5),
    pod: fieldStr(workload, 6),
    container: fieldStr(workload, 7),
    image: fieldStr(workload, 8),
    ips: fieldStrs(workload, 12),
    observedAt: fieldNum(workload, 13)
  }));
}

function parseGrantList(view) {
  return fieldMsgs(parseMessage(view), 1).map((grant) => ({
    objectId: fieldStr(grant, 1),
    tenantId: fieldStr(grant, 2),
    users: fieldStrs(grant, 3),
    roles: fieldStrs(grant, 4),
    actions: fieldStrs(grant, 5)
  }));
}

function parseEventList(view) {
  const fields = parseMessage(view);
  return {
    latestSeq: fieldNum(fields, 2),
    events: fieldMsgs(fields, 1).map((event) => ({
      seq: fieldNum(event, 1),
      tenantId: fieldStr(event, 2),
      type: fieldStr(event, 3),
      actor: fieldStr(event, 4),
      objectId: fieldStr(event, 5),
      detail: fieldStr(event, 6),
      at: fieldNum(event, 7)
    }))
  };
}

/* ========================= transport ========================= */

function apiKey() {
  return document.getElementById('apiKey').value.trim();
}

async function postProto(path, body, extraHeaders) {
  const headers = { 'Content-Type': PROTOBUF, Accept: PROTOBUF, ...extraHeaders };
  if (!headers[SITE_TOKEN_HEADER]) {
    headers[API_KEY_HEADER] = apiKey();
  }
  const response = await fetch(path, { method: 'POST', headers, body });
  const buffer = new Uint8Array(await response.arrayBuffer());
  if (!response.ok) {
    let reason = `HTTP ${response.status}`;
    try {
      const decision = parseAccessDecision(buffer);
      if (decision.reason) reason = `${reason}: ${decision.reason}`;
    } catch (ignored) {
      // Non-protobuf error body; keep the status text.
    }
    throw new Error(reason);
  }
  return buffer;
}

/* ========================= view switching ========================= */

document.querySelectorAll('.nav-item').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.nav-item').forEach((b) => b.classList.remove('active'));
    button.classList.add('active');
    document.querySelectorAll('.view').forEach((v) => v.classList.remove('active'));
    document.getElementById(`view-${button.dataset.view}`).classList.add('active');
    if (button.dataset.view === 'inventory' && state.tenantId) {
      document.getElementById('invTenant').value ||= state.tenantId;
    }
    if (button.dataset.view === 'access' && state.tenantId) {
      document.getElementById('accTenant').value ||= state.tenantId;
      document.getElementById('testObject').value ||= state.objectId;
    }
    if (button.dataset.view === 'events' && state.tenantId) {
      document.getElementById('evtTenant').value ||= state.tenantId;
    }
  });
});

/* ========================= helpers ========================= */

function value(id) {
  return document.getElementById(id).value.trim();
}

function setText(id, text) {
  document.getElementById(id).textContent = text;
}

function log(message) {
  const item = document.createElement('li');
  item.textContent = message;
  document.getElementById('log').prepend(item);
}

function cell(text) {
  const td = document.createElement('td');
  td.textContent = text;
  return td;
}

function emptyRow(tbody, columns, message) {
  tbody.replaceChildren();
  const tr = document.createElement('tr');
  const td = cell(message);
  td.colSpan = columns;
  td.className = 'muted';
  tr.appendChild(td);
  tbody.appendChild(tr);
}

function formatTime(epochMs) {
  return epochMs ? new Date(epochMs).toLocaleString() : '';
}

/* ========================= control flow ========================= */

async function runFlow() {
  const button = document.getElementById('runFlow');
  button.disabled = true;
  button.textContent = 'Running...';
  try {
    const tenant = parseTenantResponse(await postProto('/v1/tenants', createTenantRequest(value('tenantName'))));
    state.tenantId = tenant.id;
    setText('tenantId', tenant.id);
    log(`Created tenant ${tenant.id}`);

    const site = parseSiteResponse(
      await postProto('/v1/sites/connect', connectSiteRequest(tenant.id, value('siteName'), value('region'))));
    state.siteId = site.id;
    state.siteToken = site.token;
    setText('siteId', site.id);
    setText('credentialState', site.token ? `Issued (${site.token.slice(0, 18)}...)` : 'Not issued');
    log(`Connected site ${site.id} — credential issued (kept in memory only)`);

    await postProto('/v1/sites/heartbeat', heartbeatRequest(tenant.id, site.id),
      { [SITE_TOKEN_HEADER]: state.siteToken });
    log('Heartbeat authenticated with site credential');

    await postProto('/v1/containers/inventory:upsert', upsertContainerRequest({
      tenantId: tenant.id,
      siteId: site.id,
      clusterName: value('clusterName'),
      namespace: value('namespace'),
      podName: value('podName'),
      containerName: value('containerName'),
      image: value('image'),
      ipAddress: value('ipAddress')
    }), { [SITE_TOKEN_HEADER]: state.siteToken });
    setText('containerState', '1 workload published');
    log('Published container inventory (site credential)');

    const object = parseGlobalObjectResponse(
      await postProto('/v1/global-objects', createGlobalObjectRequest(value('objectName'))));
    state.objectId = object.id;
    setText('objectId', object.id);
    setText('objectState', `${object.displayName} (v${object.version})`);
    log(`Created global object ${object.id} v${object.version}`);

    const grantState = { tenantId: tenant.id, objectId: object.id, userId: value('userId'), action: value('action') };
    await postProto('/v1/ou-rbac/grants', grantRequest(grantState));
    log('Created OU-RBAC object grant');

    const decision = parseAccessDecision(
      await postProto('/v1/ou-rbac/decisions', evaluateRequest({ ...grantState, roles: [] })));
    setText('accessState', decision.effect === 2 ? 'Allowed' : 'Denied');
    setText('decisionReason', decision.reason);
    log(`Access decision: ${decision.reason}`);
  } catch (error) {
    log(`Error: ${error.message}`);
    setText('accessState', 'Error');
  } finally {
    button.disabled = false;
    button.textContent = 'Run MVP Flow';
  }
}

/* ========================= inventory view ========================= */

async function refreshInventory() {
  const tbody = document.getElementById('inventoryRows');
  const tenantId = value('invTenant');
  if (!tenantId) {
    emptyRow(tbody, 6, 'A tenant ID is required.');
    return;
  }
  try {
    const workloads = parseInventoryResponse(
      await postProto('/v1/containers/inventory:list',
        listInventoryRequest(tenantId, value('invSite'), value('invNamespace'))));
    if (!workloads.length) {
      emptyRow(tbody, 6, 'No container workloads match this filter.');
      return;
    }
    tbody.replaceChildren();
    for (const w of workloads) {
      const tr = document.createElement('tr');
      tr.append(
        cell(w.namespace), cell(w.pod), cell(w.container),
        cell(w.image), cell(w.ips.join(', ')), cell(formatTime(w.observedAt)));
      tbody.appendChild(tr);
    }
  } catch (error) {
    emptyRow(tbody, 6, `Error: ${error.message}`);
  }
}

/* ========================= access view ========================= */

async function refreshGrants() {
  const tbody = document.getElementById('grantRows');
  const tenantId = value('accTenant');
  if (!tenantId) {
    emptyRow(tbody, 5, 'A tenant ID is required.');
    return;
  }
  try {
    const grants = parseGrantList(
      await postProto('/v1/ou-rbac/grants:list', listGrantsRequest(tenantId)));
    if (!grants.length) {
      emptyRow(tbody, 5, 'No grants exist for this tenant.');
      return;
    }
    tbody.replaceChildren();
    for (const grant of grants) {
      const tr = document.createElement('tr');
      tr.append(
        cell(grant.objectId), cell(grant.users.join(', ')),
        cell(grant.roles.join(', ')), cell(grant.actions.join(', ')));
      const actionTd = document.createElement('td');
      const revokeButton = document.createElement('button');
      revokeButton.type = 'button';
      revokeButton.className = 'danger';
      revokeButton.textContent = 'Revoke';
      revokeButton.addEventListener('click', async () => {
        revokeButton.disabled = true;
        try {
          await postProto('/v1/ou-rbac/grants:revoke', revokeGrantRequest(grant.tenantId, grant.objectId));
          await refreshGrants();
        } catch (error) {
          revokeButton.disabled = false;
          revokeButton.textContent = 'Failed';
        }
      });
      actionTd.appendChild(revokeButton);
      tr.appendChild(actionTd);
      tbody.appendChild(tr);
    }
  } catch (error) {
    emptyRow(tbody, 5, `Error: ${error.message}`);
  }
}

async function runDecision() {
  const result = document.getElementById('testResult');
  try {
    const roles = value('testRoles').split(',').map((r) => r.trim()).filter(Boolean);
    const decision = parseAccessDecision(
      await postProto('/v1/ou-rbac/decisions', evaluateRequest({
        tenantId: value('accTenant'),
        objectId: value('testObject'),
        userId: value('testUser'),
        action: value('testAction'),
        roles
      })));
    result.textContent = `${decision.effect === 2 ? 'ALLOWED' : 'DENIED'} — ${decision.reason}`;
  } catch (error) {
    result.textContent = `Error: ${error.message}`;
  }
}

/* ========================= events view ========================= */

async function refreshEvents() {
  const tbody = document.getElementById('eventRows');
  try {
    const limit = Math.min(Math.max(Number.parseInt(value('evtLimit'), 10) || 50, 1), 500);
    const list = parseEventList(
      await postProto('/v1/events:list',
        listEventsRequest(value('evtTenant'), value('evtType'), 0, limit)));
    if (!list.events.length) {
      emptyRow(tbody, 6, 'No events recorded yet.');
      return;
    }
    tbody.replaceChildren();
    for (const event of list.events) {
      const tr = document.createElement('tr');
      tr.append(
        cell(String(event.seq)), cell(formatTime(event.at)), cell(event.type),
        cell(event.actor), cell(event.objectId), cell(event.detail));
      tbody.appendChild(tr);
    }
  } catch (error) {
    emptyRow(tbody, 6, `Error: ${error.message}`);
  }
}

/* ========================= wiring ========================= */

document.getElementById('runFlow').addEventListener('click', runFlow);
document.getElementById('refreshInventory').addEventListener('click', refreshInventory);
document.getElementById('refreshGrants').addEventListener('click', refreshGrants);
document.getElementById('runDecision').addEventListener('click', runDecision);
document.getElementById('refreshEvents').addEventListener('click', refreshEvents);
