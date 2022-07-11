package ru.testit.writers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.testit.clients.ApiClient;
import ru.testit.invoker.ApiException;
import ru.testit.model.*;
import ru.testit.models.ClassContainer;
import ru.testit.models.MainContainer;
import ru.testit.models.TestResult;
import ru.testit.clients.ClientConfiguration;
import ru.testit.services.ResultStorage;

import java.util.*;

public class HttpWriter implements Writer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpWriter.class);
    private final ApiClient apiClient;
    private final ResultStorage storage;
    private final ClientConfiguration config;

    public HttpWriter(ClientConfiguration config, ApiClient client, ResultStorage storage) {
        this.config = config;
        this.apiClient = client;
        this.storage = storage;
    }

    @Override
    public void startLaunch() {
        if (!Objects.equals(this.config.getTestRunId(), "null")) {
            return;
        }

        TestRunV2PostShortModel model = new TestRunV2PostShortModel();
        model.setProjectId(UUID.fromString(config.getProjectId()));

        try {
            TestRunV2GetModel response = apiClient.createTestRun(model);
            this.config.setTestRunId(response.getId().toString());

        } catch (ApiException e) {
            LOGGER.error("Can not start the launch: ".concat(e.getMessage()));
        }
    }

    @Override
    public void finishLaunch() {
        try {
            TestRunV2GetModel testRun = apiClient.getTestRun(config.getTestRunId());

            if (testRun.getStateName() != TestRunStateTypeModel.COMPLETED){
                apiClient.completeTestRun(config.getTestRunId());
            }
        } catch (ApiException e) {
            if (e.getResponseBody().contains("the StateName is already Completed")) {
                return;
            }
            LOGGER.error("Can not finish the launch: ".concat(e.getMessage()));
        }
    }

    @Override
    public void writeTest(TestResult testResult) {
        try {
            AutoTestModel test = apiClient.getAutoTestByExternalId(config.getProjectId(), testResult.getExternalId());

            if (test != null) {
                AutoTestPutModel autoTestPutModel = Converter.testResultToAutoTestPutModel(testResult);
                autoTestPutModel.setProjectId(UUID.fromString(config.getProjectId()));
                apiClient.updateAutoTest(autoTestPutModel);

                return;
            }

            AutoTestPostModel model = Converter.testResultToAutoTestPostModel(testResult);
            model.setProjectId(UUID.fromString(config.getProjectId()));

            apiClient.createAutoTest(model);
        } catch (ApiException e) {
            LOGGER.error("Can not write the autotest: ".concat(e.getMessage()));
        }
    }

    @Override
    public void writeClass(ClassContainer container) {
        for (final String testUuid : container.getChildren()) {
            storage.getTestResult(testUuid).ifPresent(
                    test -> {
                        try {
                            AutoTestModel autoTestModel = apiClient.getAutoTestByExternalId(config.getProjectId(), test.getExternalId());

                            if (autoTestModel == null) {
                                return;
                            }

                            AutoTestPutModel autoTestPutModel = Converter.autoTestModelToAutoTestPutModel(autoTestModel);

                            List<AutoTestStepModel> beforeClass = Converter.convertFixture(container.getBeforeClassMethods(), null);
                            List<AutoTestStepModel> beforeEach = Converter.convertFixture(container.getBeforeEachTest(), testUuid);
                            beforeClass.addAll(beforeEach);

                            List<AutoTestStepModel> afterClass = Converter.convertFixture(container.getAfterClassMethods(), null);
                            List<AutoTestStepModel> afterEach = Converter.convertFixture(container.getAfterEachTest(), testUuid);
                            afterClass.addAll(afterEach);

                            autoTestPutModel.setSetup(beforeClass);
                            autoTestPutModel.setTeardown(afterClass);

                            apiClient.updateAutoTest(autoTestPutModel);
                        } catch (ApiException e) {
                            LOGGER.error("Can not write the class: ".concat(e.getMessage()));
                        }
                    }
            );
        }
    }

    @Override
    public void writeTests(MainContainer container) {
        List<AutoTestStepModel> beforeAll = Converter.convertFixture(container.getBeforeMethods(), null);
        List<AutoTestStepModel> afterAll = Converter.convertFixture(container.getAfterMethods(), null);
        List<AttachmentPutModelAutoTestStepResultsModel> beforeResultAll = Converter.convertResultFixture(container.getBeforeMethods(), null);
        List<AttachmentPutModelAutoTestStepResultsModel> afterResultAll = Converter.convertResultFixture(container.getAfterMethods(), null);

        List<AutoTestResultsForTestRunModel> results = new ArrayList<>();

        for (final String classUuid : container.getChildren()) {
            storage.getClassContainer(classUuid).ifPresent(
                    cl -> {
                        List<AttachmentPutModelAutoTestStepResultsModel> beforeResultClass = Converter.convertResultFixture(cl.getBeforeClassMethods(), null);
                        List<AttachmentPutModelAutoTestStepResultsModel> afterResultClass = Converter.convertResultFixture(cl.getAfterClassMethods(), null);

                        for (final String testUuid : cl.getChildren()) {
                            storage.getTestResult(testUuid).ifPresent(
                                    test -> {
                                        try {
                                            AutoTestModel autoTestModel = apiClient.getAutoTestByExternalId(config.getProjectId(), test.getExternalId());

                                            if (autoTestModel == null) {
                                                return;
                                            }

                                            AutoTestPutModel autoTestPutModel = Converter.autoTestModelToAutoTestPutModel(autoTestModel);

                                            List<AutoTestStepModel> beforeFinish = beforeAll;
                                            beforeFinish.addAll(autoTestPutModel.getSetup());
                                            autoTestPutModel.setSetup(beforeFinish);

                                            List<AutoTestStepModel> afterFinish = afterAll;
                                            afterFinish.addAll(autoTestPutModel.getTeardown());
                                            autoTestPutModel.setTeardown(afterFinish);

                                            apiClient.updateAutoTest(autoTestPutModel);


                                            AutoTestResultsForTestRunModel autoTestResultsForTestRunModel =
                                                    Converter.testResultToAutoTestResultsForTestRunModel(test);
                                            autoTestResultsForTestRunModel
                                                    .setConfigurationId(UUID.fromString(config.getConfigurationId()));

                                            List<AttachmentPutModelAutoTestStepResultsModel> beforeResultEach =
                                                    Converter.convertResultFixture(cl.getBeforeEachTest(), testUuid);
                                            List<AttachmentPutModelAutoTestStepResultsModel> beforeResultFinish = new ArrayList<>();
                                            beforeResultFinish.addAll(beforeResultAll);
                                            beforeResultFinish.addAll(beforeResultClass);
                                            beforeResultFinish.addAll(beforeResultEach);

                                            List<AttachmentPutModelAutoTestStepResultsModel> afterResultEach =
                                                    Converter.convertResultFixture(cl.getAfterEachTest(), testUuid);
                                            List<AttachmentPutModelAutoTestStepResultsModel> afterResultFinish = new ArrayList<>();
                                            afterResultFinish.addAll(afterResultAll);
                                            afterResultFinish.addAll(afterResultClass);
                                            afterResultFinish.addAll(afterResultEach);

                                            autoTestResultsForTestRunModel.setSetupResults(beforeResultFinish);
                                            autoTestResultsForTestRunModel.setTeardownResults(afterResultFinish);

                                            results.add(autoTestResultsForTestRunModel);

                                        } catch (ApiException e) {
                                            LOGGER.error("Can not update the autotest: ".concat(e.getMessage()));
                                        }
                                    }
                            );
                        }
                    }
            );
        }

        try {
            apiClient.sendTestResults(config.getTestRunId(), results);
        } catch (ApiException e) {
            LOGGER.error("Can not write the test results: ".concat(e.getMessage()));
        }
    }
}
