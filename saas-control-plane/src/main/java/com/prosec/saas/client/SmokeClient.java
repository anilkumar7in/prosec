package com.prosec.saas.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.prosec.saas.proto.AccessDecisionResponse;
import com.prosec.saas.proto.ConnectSiteRequest;
import com.prosec.saas.proto.ContainerInventoryResponse;
import com.prosec.saas.proto.ContainerRuntime;
import com.prosec.saas.proto.ContainerWorkload;
import com.prosec.saas.proto.CreateGlobalObjectRequest;
import com.prosec.saas.proto.CreateTenantRequest;
import com.prosec.saas.proto.EvaluateAccessRequest;
import com.prosec.saas.proto.EventListResponse;
import com.prosec.saas.proto.GlobalObjectResponse;
import com.prosec.saas.proto.GlobalObjectVersionListResponse;
import com.prosec.saas.proto.GrantObjectAccessRequest;
import com.prosec.saas.proto.ListEventsRequest;
import com.prosec.saas.proto.ListObjectGrantsRequest;
import com.prosec.saas.proto.ObjectGrant;
import com.prosec.saas.proto.ObjectGrantListResponse;
import com.prosec.saas.proto.ObjectGrantResponse;
import com.prosec.saas.proto.ObjectType;
import com.prosec.saas.proto.RevokeObjectAccessRequest;
import com.prosec.saas.proto.SiteHeartbeatRequest;
import com.prosec.saas.proto.SiteResponse;
import com.prosec.saas.proto.TenantResponse;
import com.prosec.saas.proto.UpdateGlobalObjectRequest;
import com.prosec.saas.proto.UpsertContainerInventoryRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class SmokeClient {

    private static final String PROTOBUF = "application/x-protobuf";
    private static final String API_KEY_HEADER = "X-Prosec-Api-Key";
    private static final String SITE_TOKEN_HEADER = "X-Prosec-Site-Token";

    private final URI baseUri;
    private final String adminApiKey;
    private final HttpClient httpClient;

    public SmokeClient(String baseUrl, String adminApiKey) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.adminApiKey = adminApiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String adminApiKey = args.length > 1 ? args[1] : "dev-admin-key";
        SmokeClient client = new SmokeClient(baseUrl, adminApiKey);
        client.run();
    }

    private void run() throws Exception {
        TenantResponse tenant = post(
                "/v1/tenants",
                CreateTenantRequest.newBuilder().setDisplayName("Acme Security Lab").build(),
                TenantResponse.parser(),
                adminHeaders());
        String tenantId = tenant.getTenant().getId();
        System.out.println("tenant=" + tenantId);

        SiteResponse site = post(
                "/v1/sites/connect",
                ConnectSiteRequest.newBuilder()
                        .setTenantId(tenantId)
                        .setDisplayName("primary-k8s-site")
                        .setRegion("us-east")
                        .build(),
                SiteResponse.parser(),
                adminHeaders());
        String siteId = site.getSite().getId();
        String siteToken = site.getCredential().getToken();
        System.out.println("site=" + siteId + " credential-issued=" + !siteToken.isBlank());

        post(
                "/v1/sites/heartbeat",
                SiteHeartbeatRequest.newBuilder().setTenantId(tenantId).setSiteId(siteId).build(),
                SiteResponse.parser(),
                Map.of(SITE_TOKEN_HEADER, siteToken));
        System.out.println("heartbeat=authenticated");

        ContainerWorkload workload = ContainerWorkload.newBuilder()
                .setClusterName("prod-cluster-1")
                .setNamespace("payments")
                .setPodName("checkout-api-7f7c9c")
                .setContainerName("checkout-api")
                .setImage("registry.example.com/checkout-api:1.0.0")
                .setImageDigest("sha256:local-demo")
                .setRuntime(ContainerRuntime.CONTAINERD)
                .putLabels("app", "checkout")
                .putLabels("tier", "api")
                .addIpAddresses("10.42.1.25")
                .build();

        post(
                "/v1/containers/inventory:upsert",
                UpsertContainerInventoryRequest.newBuilder()
                        .setTenantId(tenantId)
                        .setSiteId(siteId)
                        .addContainers(workload)
                        .build(),
                ContainerInventoryResponse.parser(),
                Map.of(SITE_TOKEN_HEADER, siteToken));
        System.out.println("container-inventory=published (site credential)");

        GlobalObjectResponse object = post(
                "/v1/global-objects",
                CreateGlobalObjectRequest.newBuilder()
                        .setObjectType(ObjectType.GLOBAL_POLICY)
                        .setDisplayName("default-container-baseline")
                        .setProtobufPayload(ByteString.copyFromUtf8("policy-version:1"))
                        .build(),
                GlobalObjectResponse.parser(),
                adminHeaders());
        String objectId = object.getObject().getId();
        System.out.println("global-object=" + objectId + " v" + object.getObject().getVersion());

        GlobalObjectResponse updated = post(
                "/v1/global-objects/" + objectId + ":update",
                UpdateGlobalObjectRequest.newBuilder()
                        .setObjectId(objectId)
                        .setProtobufPayload(ByteString.copyFromUtf8("policy-version:2"))
                        .build(),
                GlobalObjectResponse.parser(),
                adminHeaders());
        System.out.println("object-updated=v" + updated.getObject().getVersion());

        GlobalObjectVersionListResponse versions = get(
                "/v1/global-objects/" + objectId + "/versions",
                GlobalObjectVersionListResponse.parser(),
                adminHeaders());
        System.out.println("object-versions=" + versions.getVersionsCount());

        post(
                "/v1/ou-rbac/grants",
                GrantObjectAccessRequest.newBuilder()
                        .setGrant(ObjectGrant.newBuilder()
                                .setTenantId(tenantId)
                                .setObjectId(objectId)
                                .addUserIds("admin@acme.test")
                                .addRoles("SECURITY_ADMIN")
                                .addActions("read")
                                .addActions("apply")
                                .build())
                        .build(),
                ObjectGrantResponse.parser(),
                adminHeaders());
        System.out.println("object-grant=created");

        AccessDecisionResponse userDecision = post(
                "/v1/ou-rbac/decisions",
                EvaluateAccessRequest.newBuilder()
                        .setTenantId(tenantId)
                        .setObjectId(objectId)
                        .setUserId("admin@acme.test")
                        .setAction("apply")
                        .build(),
                AccessDecisionResponse.parser(),
                adminHeaders());
        System.out.println("decision-by-user=" + userDecision.getEffect());

        AccessDecisionResponse roleDecision = post(
                "/v1/ou-rbac/decisions",
                EvaluateAccessRequest.newBuilder()
                        .setTenantId(tenantId)
                        .setObjectId(objectId)
                        .setUserId("analyst@acme.test")
                        .addRoles("SECURITY_ADMIN")
                        .setAction("read")
                        .build(),
                AccessDecisionResponse.parser(),
                adminHeaders());
        System.out.println("decision-by-role=" + roleDecision.getEffect());

        ObjectGrantListResponse grants = post(
                "/v1/ou-rbac/grants:list",
                ListObjectGrantsRequest.newBuilder().setTenantId(tenantId).build(),
                ObjectGrantListResponse.parser(),
                adminHeaders());
        System.out.println("grants-listed=" + grants.getGrantsCount());

        post(
                "/v1/ou-rbac/grants:revoke",
                RevokeObjectAccessRequest.newBuilder().setTenantId(tenantId).setObjectId(objectId).build(),
                ObjectGrantResponse.parser(),
                adminHeaders());
        AccessDecisionResponse afterRevoke = post(
                "/v1/ou-rbac/decisions",
                EvaluateAccessRequest.newBuilder()
                        .setTenantId(tenantId)
                        .setObjectId(objectId)
                        .setUserId("admin@acme.test")
                        .setAction("apply")
                        .build(),
                AccessDecisionResponse.parser(),
                adminHeaders());
        System.out.println("decision-after-revoke=" + afterRevoke.getEffect());

        EventListResponse events = post(
                "/v1/events:list",
                ListEventsRequest.newBuilder().setTenantId(tenantId).setLimit(50).build(),
                EventListResponse.parser(),
                adminHeaders());
        System.out.println("audit-events=" + events.getEventsCount() + " latest-seq=" + events.getLatestSeq());

        int unauthorized = statusOf(
                "/v1/tenants",
                CreateTenantRequest.newBuilder().setDisplayName("intruder").build(),
                Map.of(API_KEY_HEADER, "wrong-key"));
        System.out.println("wrong-admin-key-status=" + unauthorized);
    }

    private Map<String, String> adminHeaders() {
        return Map.of(API_KEY_HEADER, adminApiKey);
    }

    private <T extends Message> T post(
            String path, Message request, com.google.protobuf.Parser<T> parser, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpResponse<byte[]> response = send(path, request, headers);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + path);
        }
        return parser.parseFrom(response.body());
    }

    private <T extends Message> T get(
            String path, com.google.protobuf.Parser<T> parser, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", PROTOBUF)
                .GET();
        headers.forEach(builder::header);
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + path);
        }
        return parser.parseFrom(response.body());
    }

    private int statusOf(String path, Message request, Map<String, String> headers)
            throws IOException, InterruptedException {
        return send(path, request, headers).statusCode();
    }

    private HttpResponse<byte[]> send(String path, Message request, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", PROTOBUF)
                .header("Accept", PROTOBUF)
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.toByteArray()));
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
