package keywhiz.service.resources.automation.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.List;
import keywhiz.IntegrationTestRule;
import keywhiz.KeywhizService;
import keywhiz.TestClients;
import keywhiz.api.automation.v2.CreateGroupRequestV2;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.automation.v2.ModifyGroupsRequestV2;
import keywhiz.api.automation.v2.SecretDetailResponseV2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static keywhiz.TestClients.clientRequest;
import static keywhiz.client.KeywhizClient.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class SecretResourceTest {
  private static final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
  private static final Encoder encoder = Base64.getEncoder();
  private static final Decoder decoder = Base64.getDecoder();

  OkHttpClient mutualSslClient;

  @ClassRule public static final RuleChain chain = IntegrationTestRule.rule();

  @Before public void setUp() {
    mutualSslClient = TestClients.mutualSslClient();
  }

  @Test public void createSecret_successUnVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret1")
        .content(encoder.encodeToString("supa secret".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).isEqualTo("/automation/v2/secrets/secret1");
  }

  @Test public void createSecret_duplicateUnVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret2")
        .content(encoder.encodeToString("supa secret2".getBytes(UTF_8)))
        .description("desc")
        .build();
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(409);
  }

  @Test public void createSecret_successVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret3")
        .content(encoder.encodeToString("supa secre3".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build();
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    URI location = URI.create(httpResponse.header(LOCATION));
    assertThat(location.getPath()).matches("/automation/v2/secrets/secret3/[a-z0-9]{16}");
  }

  @Test public void createSecret_duplicateVersioned() throws Exception {
    CreateSecretRequestV2 request = CreateSecretRequestV2.builder()
        .name("secret4")
        .content(encoder.encodeToString("supa secre4".getBytes(UTF_8)))
        .build();

    // First secret
    Response httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    String path = URI.create(httpResponse.header(LOCATION)).getPath();
    assertThat(path).matches("/automation/v2/secrets/secret4/[a-z0-9]{16}");

    // Duplicate secret w/ different version
    httpResponse = create(request);
    assertThat(httpResponse.code()).isEqualTo(201);
    String path2 = URI.create(httpResponse.header(LOCATION)).getPath();
    assertThat(path2).matches("/automation/v2/secrets/secret4/[a-z0-9]{16}").isNotEqualTo(path);
  }

  @Ignore
  @Test public void modifySecretSeries_notFound() throws Exception {
    // TODO: need request object
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(null));
    Request post = clientRequest("/automation/v2/secrets/non-existent").post(body).build();
    Response httpResponse = mutualSslClient.newCall(post).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Ignore
  @Test public void modifySecretSeries_success() throws Exception {
    // secret5
    // TODO: check different metadata, name, location
  }

  @Test public void secretInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretInfo_success() throws Exception {
    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret6")
        .content(encoder.encodeToString("supa secret6".getBytes(UTF_8)))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());

    SecretDetailResponseV2 response = lookup("secret6");
    assertThat(response.name()).isEqualTo("secret6");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.description()).isEqualTo("desc");
    assertThat(response.type()).isEqualTo("password");

    // These values are left out for a series lookup as they pertain to a specific secret.
    assertThat(response.content()).isEmpty();
    assertThat(response.size().longValue()).isZero();
    assertThat(response.metadata()).isEmpty();
  }

  @Test public void secretGroupsListing_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent/groups").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretGroupsListing_success() throws Exception {
    createGroup("group7a");
    createGroup("group7b");

    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret7")
        .content(encoder.encodeToString("supa secret7".getBytes(UTF_8)))
        .groups("group7a", "group7b")
        .build());

    assertThat(groupsListing("secret7")).containsOnly("group7a", "group7b");
  }

  @Test public void modifySecretGroups_notFound() throws Exception {
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder().build();
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest("/automation/v2/secrets/non-existent/groups").put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void modifySecretGroups_success() throws Exception {
    // Create sample secret and groups
    createGroup("group8a");
    createGroup("group8b");
    createGroup("group8c");
    create(CreateSecretRequestV2.builder()
        .name("secret8")
        .content(encoder.encodeToString("supa secret8".getBytes(UTF_8)))
        .groups("group8a", "group8b")
        .build());

    // Modify secret
    ModifyGroupsRequestV2 request = ModifyGroupsRequestV2.builder()
        .addGroups("group8c", "non-existent1")
        .removeGroups("group8a", "non-existent2")
        .build();
    List<String> groups = modifyGroups("secret8", request);
    assertThat(groups).containsOnly("group8b", "group8c");
  }

  @Test public void secretVersionInfo_notFound() throws Exception {
    Request get = clientRequest("/automation/v2/secrets/non-existent/version").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretVersionInfo_versionNotFound() throws Exception {
    create(CreateSecretRequestV2.builder()
        .name("secret9")
        .content(encoder.encodeToString("supa secret9".getBytes(UTF_8)))
        .build());

    Request get = clientRequest("/automation/v2/secrets/secret9/blah-version").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(404);
  }

  @Test public void secretVersionInfo_successVersioned() throws Exception {
    // Sample secret
    byte[] secret = "supa secret10".getBytes(UTF_8);
    Response httpResponse = create(CreateSecretRequestV2.builder()
        .name("secret10")
        .content(encoder.encodeToString(secret))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());
    URI location = URI.create(httpResponse.header(LOCATION));
    String version = Iterables.getLast(Splitter.on('/').split(location.getPath()));

    SecretDetailResponseV2 response = versionLookup("secret10", version);
    assertThat(response.name()).isEqualTo("secret10");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.description()).isEqualTo("desc");
    assertThat(response.type()).isEqualTo("password");

    assertThat(decoder.decode(response.content())).isEqualTo(secret);
    assertThat(response.size().longValue()).isEqualTo(secret.length);
    assertThat(response.metadata()).containsOnly(entry("owner", "root"), entry("mode", "0440"));
  }

  @Test public void secretVersionInfo_successUnVersioned() throws Exception {
    // Sample secret
    byte[] secret = "supa secret11".getBytes(UTF_8);
    create(CreateSecretRequestV2.builder()
        .name("secret11")
        .content(encoder.encodeToString(secret))
        .description("desc")
        .metadata(ImmutableMap.of("owner", "root", "mode", "0440"))
        .type("password")
        .build());

    SecretDetailResponseV2 response = versionLookup("secret11", "");
    assertThat(response.name()).isEqualTo("secret11");
    assertThat(response.createdBy()).isEqualTo("client");
    assertThat(response.description()).isEqualTo("desc");
    assertThat(response.type()).isEqualTo("password");

    assertThat(decoder.decode(response.content())).isEqualTo(secret);
    assertThat(response.size().longValue()).isEqualTo(secret.length);
    assertThat(response.metadata()).containsOnly(entry("owner", "root"), entry("mode", "0440"));
  }

  @Test public void deleteSecretSeries_notFound() throws Exception {
    assertThat(deleteSeries("non-existent").code()).isEqualTo(404);
  }

  @Test public void deleteSecretSeries_success() throws Exception {
    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret12")
        .content(encoder.encodeToString("supa secret12".getBytes(UTF_8)))
        .build());

    // Delete works
    assertThat(deleteSeries("secret12").code()).isEqualTo(204);

    // Subsequent deletes can't find the secret series
    assertThat(deleteSeries("secret12").code()).isEqualTo(404);
  }

  @Test public void deleteSecretVersion_notFound() throws Exception {
    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret13")
        .content(encoder.encodeToString("supa secret13".getBytes(UTF_8)))
        .build());

    assertThat(deleteSecretVersion("secret13", "non-existent").code()).isEqualTo(404);
  }

  @Test public void deleteSecretVersion_success() throws Exception {
    // Sample secret
    Response httpResponse = create(CreateSecretRequestV2.builder()
        .name("secret14")
        .content(encoder.encodeToString("supa secret14".getBytes(UTF_8)))
        .build());
    URI location = URI.create(httpResponse.header(LOCATION));
    String version = Iterables.getLast(Splitter.on('/').split(location.getPath()));

    // Delete works
    assertThat(deleteSecretVersion("secret14", version).code()).isEqualTo(204);

    // Subsequent deletes can't find the secret version
    assertThat(deleteSecretVersion("secret14", version).code()).isEqualTo(404);
  }

  @Test public void deleteSecretVersion_successUnVersioned() throws Exception {
    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret15")
        .content(encoder.encodeToString("supa secret15".getBytes(UTF_8)))
        .build());

    // Delete works
    assertThat(deleteSecretVersion("secret15", "").code()).isEqualTo(204);

    // Subsequent deletes can't find the secret version
    assertThat(deleteSecretVersion("secret15", "").code()).isEqualTo(404);
  }

  @Test public void secretListing_success() throws Exception {
    // Listing without secret16
    assertThat(listing()).doesNotContain("secret16");

    // Sample secret
    create(CreateSecretRequestV2.builder()
        .name("secret16")
        .content(encoder.encodeToString("supa secret16".getBytes(UTF_8)))
        .build());

    // Listing with secret16
    assertThat(listing()).contains("secret16");
  }

  private Response createGroup(String name) throws IOException {
    GroupResourceTest groupResourceTest = new GroupResourceTest();
    groupResourceTest.mutualSslClient = mutualSslClient;
    return groupResourceTest.create(CreateGroupRequestV2.builder().name(name).build());
  }

  Response create(CreateSecretRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request post = clientRequest("/automation/v2/secrets").post(body).build();
    return mutualSslClient.newCall(post).execute();
  }

  List<String> listing() throws IOException {
    Request get = clientRequest("/automation/v2/secrets").get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  SecretDetailResponseV2 lookup(String name) throws IOException {
    Request get = clientRequest("/automation/v2/secrets/" + name).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SecretDetailResponseV2.class);
  }

  SecretDetailResponseV2 versionLookup(String name, String version) throws IOException {
    Request get = clientRequest(format("/automation/v2/secrets/%s/%s", name, version)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), SecretDetailResponseV2.class);
  }

  List<String> groupsListing(String name) throws IOException {
    Request get = clientRequest(format("/automation/v2/secrets/%s/groups", name)).get().build();
    Response httpResponse = mutualSslClient.newCall(get).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  List<String> modifyGroups(String name, ModifyGroupsRequestV2 request) throws IOException {
    RequestBody body = RequestBody.create(JSON, mapper.writeValueAsString(request));
    Request put = clientRequest(format("/automation/v2/secrets/%s/groups", name)).put(body).build();
    Response httpResponse = mutualSslClient.newCall(put).execute();
    assertThat(httpResponse.code()).isEqualTo(200);
    return mapper.readValue(httpResponse.body().byteStream(), new TypeReference<List<String>>(){});
  }

  Response deleteSeries(String name) throws IOException {
    Request delete = clientRequest("/automation/v2/secrets/" + name).delete().build();
    return mutualSslClient.newCall(delete).execute();
  }

  Response deleteSecretVersion(String name, String version) throws IOException {
    Request delete = clientRequest(format("/automation/v2/secrets/%s/%s", name, version)).delete().build();
    return mutualSslClient.newCall(delete).execute();
  }
}
