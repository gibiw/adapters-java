package ru.testit.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.testit.client.api.AttachmentsApi;
import ru.testit.client.api.AutoTestsApi;
import ru.testit.client.api.TestRunsApi;
import ru.testit.client.invoker.ApiException;
import ru.testit.client.model.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class TmsApiClient implements ApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TmsApiClient.class);
    private static final String AUTH_PREFIX = "PrivateToken";
    private static final boolean INCLUDE_STEPS = true;
    private static final boolean INCLUDE_LABELS = true;

    private final TestRunsApi testRunsApi;
    private final AutoTestsApi autoTestsApi;
    private final AttachmentsApi attachmentsApi;

    private final ClientConfiguration clientConfiguration;

    public TmsApiClient(ClientConfiguration config) {
        ApiClientExtended apiClient = new ApiClientExtended();
        apiClient.setBasePath(config.getUrl());
        apiClient.setApiKeyPrefix(AUTH_PREFIX);
        apiClient.setApiKey(config.getPrivateToken());

        clientConfiguration = config;
        testRunsApi = new TestRunsApi(apiClient);
        autoTestsApi = new AutoTestsApi(apiClient);
        attachmentsApi = new AttachmentsApi(apiClient);
    }

    @Override
    public TestRunV2GetModel createTestRun() throws ApiException {

        TestRunV2PostShortModel model = new TestRunV2PostShortModel();
        model.setProjectId(UUID.fromString(clientConfiguration.getProjectId()));

        if (!Objects.equals(this.clientConfiguration.getTestRunName(), "null")) {
            model.setName(this.clientConfiguration.getTestRunName());
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Create new test run: {}", model);
        }

        TestRunV2GetModel response = testRunsApi.createEmpty(model);
        testRunsApi.startTestRun(response.getId());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("The test run created: {}", response);
        }

        return response;
    }

    @Override
    public TestRunV2GetModel getTestRun(String uuid) throws ApiException {
        return testRunsApi.getTestRunById(UUID.fromString(uuid));
    }

    @Override
    public void completeTestRun(String uuid) throws ApiException {
        testRunsApi.completeTestRun(UUID.fromString(uuid));
    }

    @Override
    public void updateAutoTest(AutoTestPutModel model) throws ApiException {
        autoTestsApi.updateAutoTest(model);
    }

    @Override
    public String createAutoTest(AutoTestPostModel model) throws ApiException {
        return autoTestsApi.createAutoTest(model).getId().toString();
    }

    @Override
    public AutoTestModel getAutoTestByExternalId(String externalId) throws ApiException {
        List<AutoTestModel> tests = autoTestsApi.getAllAutoTests(UUID.fromString(this.clientConfiguration.getProjectId()),
                externalId, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                INCLUDE_STEPS, INCLUDE_LABELS, null, null, null, null, null);

        if ((long) tests.size() == 0) {
            return null;
        }

        return tests.get(0);
    }

    @Override
    public void linkAutoTestToWorkItem(String id, String workItemId) throws ApiException {
        autoTestsApi.linkAutoTestToWorkItem(id, new WorkItemIdModel().id(workItemId));
    }

    @Override
    public void sendTestResults(String testRunUuid, List<AutoTestResultsForTestRunModel> models) throws ApiException {
        testRunsApi.setAutoTestResultsForTestRun(UUID.fromString(testRunUuid), models);
    }

    @Override
    public String addAttachment(String path) throws ApiException {
        File file = new File(path);
        AttachmentModel model = attachmentsApi.apiV2AttachmentsPost(file);

        return model.getId().toString();
    }

    public List<String> getTestFromTestRun(String testRunUuid, String configurationId) throws ApiException {
        TestRunV2GetModel model = testRunsApi.getTestRunById(UUID.fromString(testRunUuid));
        UUID configUUID = UUID.fromString(configurationId);

        if (model.getTestResults().size() == 0) {
            return new ArrayList<>();
        }

        return model.getTestResults().stream()
                .filter(result -> result.getConfigurationId().equals(configUUID))
                .map(result -> result.getAutoTest().getExternalId()).collect(Collectors.toList());
    }
}
