/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.service.daos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoFixtures;
import keywhiz.service.daos.SecretDAO.SecretDAOFactory;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class SecretDAOTest {
  @Inject DSLContext jooqContext;
  @Inject ObjectMapper objectMapper;
  @Inject SecretDAOFactory secretDAOFactory;

  final static ContentCryptographer cryptographer = CryptoFixtures.contentCryptographer();
  final static ApiDate date = ApiDate.now();
  ImmutableMap<String, String> emptyMetadata = ImmutableMap.of();

  SecretSeries series1 = SecretSeries.of(1, "secret1", "desc1", date, "creator", date, "updater", null, null);
  String content = "c2VjcmV0MQ==";
  String encryptedContent = cryptographer.encryptionKeyDerivedFrom(series1.name()).encrypt(content);
  SecretContent content1 = SecretContent.of(101, 1, encryptedContent, date, "creator", date, "updater", emptyMetadata);
  SecretSeriesAndContent secret1 = SecretSeriesAndContent.of(series1, content1);

  SecretSeries series2 = SecretSeries.of(2, "secret2", "desc2", date, "creator", date, "updater", null, null);
  SecretContent content2 = SecretContent.of(102, 2, encryptedContent, date, "creator", date, "updater", emptyMetadata);
  SecretSeriesAndContent secret2 = SecretSeriesAndContent.of(series2, content2);

  SecretDAO secretDAO;

  @Before
  public void setUp() throws Exception {
    jooqContext.insertInto(SECRETS)
        .set(SECRETS.ID, secret1.series().id())
        .set(SECRETS.NAME, secret1.series().name())
        .set(SECRETS.DESCRIPTION, secret1.series().description())
        .set(SECRETS.CREATEDBY, secret1.series().createdBy())
        .set(SECRETS.CREATEDAT, secret1.series().createdAt().toEpochSecond())
        .set(SECRETS.UPDATEDBY, secret1.series().updatedBy())
        .set(SECRETS.UPDATEDAT, secret1.series().updatedAt().toEpochSecond())
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.SECRETID, secret1.series().id())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secret1.content().encryptedContent())
        .set(SECRETS_CONTENT.CREATEDBY, secret1.content().createdBy())
        .set(SECRETS_CONTENT.CREATEDAT, secret1.content().createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secret1.content().updatedBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secret1.content().updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.METADATA,
            objectMapper.writeValueAsString(secret1.content().metadata()))
        .execute();

    jooqContext.insertInto(SECRETS)
        .set(SECRETS.ID, secret2.series().id())
        .set(SECRETS.NAME, secret2.series().name())
        .set(SECRETS.DESCRIPTION, secret2.series().description())
        .set(SECRETS.CREATEDBY, secret2.series().createdBy())
        .set(SECRETS.CREATEDAT, secret2.series().createdAt().toEpochSecond())
        .set(SECRETS.UPDATEDBY, secret2.series().updatedBy())
        .set(SECRETS.UPDATEDAT, secret2.series().updatedAt().toEpochSecond())
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.SECRETID, secret2.series().id())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secret2.content().encryptedContent())
        .set(SECRETS_CONTENT.CREATEDBY, secret2.content().createdBy())
        .set(SECRETS_CONTENT.CREATEDAT, secret2.content().createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secret2.content().updatedBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secret2.content().updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.METADATA, objectMapper.writeValueAsString(secret2.content().metadata()))
        .execute();

    secretDAO = secretDAOFactory.readwrite();

    // Secrets created in the DB will have different id, updatedAt values.
    secret1 = secretDAO.getSecretByName(secret1.series().name()).get();
    secret2 = secretDAO.getSecretByName(secret2.series().name()).get();
  }

  @Test public void createSecret() {
    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    String name = "newSecret";
    String content = "c2VjcmV0MQ==";
    String encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    long newId = secretDAO.createSecret(name, encryptedContent, "creator",
        ImmutableMap.of(), 0, "", null, ImmutableMap.of());
    SecretSeriesAndContent newSecret = secretDAO.getSecretById(newId).get();

    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore + 1);
    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore + 1);

    newSecret = secretDAO.getSecretByName(newSecret.series().name()).get();
    assertThat(secretDAO.getSecrets()).containsOnly(secret1, secret2, newSecret);
  }

  @Test public void createTwoVersionsOfASecret() {
    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    String name = "newSecret";
    String content = "c2VjcmV0MQ==";
    String encryptedContent1 = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    long id = secretDAO.createSecret(name, encryptedContent1, "creator", ImmutableMap.of(),
        0, "", null, ImmutableMap.of());
    SecretSeriesAndContent newSecret1 = secretDAO.getSecretById(id).get();

    content = "amFja2RvcnNrZXkK";
    String encryptedContent2 = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    id = secretDAO.createSecret(name, encryptedContent2, "creator", ImmutableMap.of(), 0, "",
        null, ImmutableMap.of());
    SecretSeriesAndContent newSecret2 = secretDAO.getSecretById(id).get();

    // Only one new secrets entry should be created - there should be 2 secrets_content entries though
    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore + 1);

    assertThat(newSecret1.content().encryptedContent()).isEqualTo(encryptedContent1);
    assertThat(newSecret2.content().encryptedContent()).isEqualTo(encryptedContent2);
    assertThat(secretDAO.getSecrets()).containsOnly(secret1, secret2, newSecret1, newSecret2);

    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore + 2);
  }

  @Test public void getSecretByName() {
    String name = secret1.series().name();
    assertThat(secretDAO.getSecretByName(name)).contains(secret1);
  }

  @Test public void getSecretById() {
    assertThat(secretDAO.getSecretById(secret2.series().id())).contains(secret2);
  }

  @Test public void getSecretById2() {
    SecretSeriesAndContent expected = secretDAO.getSecretByName(secret1.series().name())
        .orElseThrow(RuntimeException::new);
    List<SecretSeriesAndContent> actual = secretDAO.getSecretsById(expected.series().id());
    assertThat(actual).containsExactly(expected);
  }

  @Test public void getNonExistentSecret() {
    assertThat(secretDAO.getSecretByName("non-existent")).isEmpty();
    assertThat(secretDAO.getSecretsById(-1231)).isEmpty();
  }

  @Test public void getSecrets() {
    assertThat(secretDAO.getSecrets()).containsOnly(secret1, secret2);
  }

  @Test public void deleteSecretsByName() {
    secretDAO.createSecret("toBeDeleted_deleteSecretsByName", "encryptedShhh", "creator",
        ImmutableMap.of(), 0, "", null, null);

    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    secretDAO.deleteSecretsByName("toBeDeleted_deleteSecretsByName");

    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore - 1);
    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore - 1);
    assertThat(secretDAO.getSecretByName("toBeDeleted_deleteSecretsByName")).isEmpty();
  }

  @Test public void deleteSecretByName() {
    secretDAO.createSecret("shadow_deleteSecretByNameAndVersion", "encrypted1", "creator",
        ImmutableMap.of(), 0, "desc", null, null);
    secretDAO.createSecret("shadow_deleteSecretByNameAndVersion", "encrypted2", "creator",
        ImmutableMap.of(), 0, "desc", null, null);
    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    secretDAO.deleteSecretByName("shadow_deleteSecretByNameAndVersion");

    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore);
    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore - 1);
    assertThat(secretDAO.getSecretByName("shadow_deleteSecretByNameAndVersion"))
        .isEmpty();
  }

  @Test public void deleteSecretSeriesWhenEmpty() {
    secretDAO.createSecret("toBeDeleted_deleteSecretSeriesWhenEmpty", "encryptedOvaltine",
        "creator", ImmutableMap.of(), 0, "desc", null, null);

    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    secretDAO.deleteSecretByName("toBeDeleted_deleteSecretSeriesWhenEmpty");

    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore - 1);
    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore - 1);
    assertThat(secretDAO.getSecretByName("toBeDeleted_deleteSecretSeriesWhenEmpty")).isEmpty();
  }

  @Test(expected = DataAccessException.class)
  public void willNotCreateDuplicateVersionedSecret() throws Exception {
    ImmutableMap<String, String> emptyMap = ImmutableMap.of();
    secretDAO.createSecret("secret1", "encrypted1", "creator", emptyMap, 0, "", null, emptyMap);
    secretDAO.createSecret("secret1", "encrypted1", "creator", emptyMap, 0, "", null, emptyMap);
  }

  @Test(expected = DataAccessException.class)
  public void willNotCreateDuplicateSecret() throws Exception {
    ImmutableMap<String, String> emptyMap = ImmutableMap.of();
    secretDAO.createSecret("secret1", "encrypted1", "creator", emptyMap, 0, "", null, emptyMap);
    secretDAO.createSecret("secret1", "encrypted1", "creator", emptyMap, 0, "", null, emptyMap);
  }

  private int tableSize(Table table) {
    return jooqContext.fetchCount(table);
  }
}
