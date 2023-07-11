/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice;

import com.azure.resourcemanager.appservice.models.CsmPublishingProfileOptions;
import com.azure.resourcemanager.appservice.models.HostType;
import com.azure.resourcemanager.appservice.models.HostnameSslState;
import com.azure.resourcemanager.appservice.models.PublishingProfileFormat;
import com.azure.resourcemanager.appservice.models.WebAppBase;
import com.azure.resourcemanager.appservice.models.WebSiteBase;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.file.AppServiceKuduClient;
import com.microsoft.azure.toolkit.lib.appservice.file.IFileClient;
import com.microsoft.azure.toolkit.lib.appservice.file.IProcessClient;
import com.microsoft.azure.toolkit.lib.appservice.model.AppServiceFile;
import com.microsoft.azure.toolkit.lib.appservice.model.CommandOutput;
import com.microsoft.azure.toolkit.lib.appservice.model.DiagnosticConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.ProcessInfo;
import com.microsoft.azure.toolkit.lib.appservice.model.PublishingProfile;
import com.microsoft.azure.toolkit.lib.appservice.model.Runtime;
import com.microsoft.azure.toolkit.lib.appservice.model.TunnelStatus;
import com.microsoft.azure.toolkit.lib.appservice.plan.AppServicePlan;
import com.microsoft.azure.toolkit.lib.appservice.plan.AppServicePlanModule;
import com.microsoft.azure.toolkit.lib.appservice.utils.AppServiceUtils;
import com.microsoft.azure.toolkit.lib.appservice.utils.Utils;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AzResource;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.Startable;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.utils.StreamingLogSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unused")
@Slf4j
public abstract class AppServiceAppBase<
    T extends AppServiceAppBase<T, P, F>,
    P extends AbstractAzResource<P, ?, ?>,
    F extends WebAppBase>
    extends AbstractAzResource<T, P, WebSiteBase> implements Startable, Deletable, StreamingLogSupport {
    protected AppServiceKuduClient kuduManager;

    protected AppServiceAppBase(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull AbstractAzResourceModule<T, P, WebSiteBase> module) {
        super(name, resourceGroupName, module);
    }

    protected AppServiceAppBase(@Nonnull String name, @Nonnull AbstractAzResourceModule<T, P, WebSiteBase> module) {
        super(name, module);
    }

    /**
     * copy constructor
     */
    protected AppServiceAppBase(@Nonnull T origin) {
        super(origin);
        this.kuduManager = origin.kuduManager;
    }

    @Nullable
    public synchronized F getFullRemote() {
        WebSiteBase remote = this.getRemote();
        if (Objects.nonNull(remote) && !(remote instanceof WebAppBase)) {
            this.invalidateCache();
            remote = this.getRemote();
        }
        if (!(remote instanceof WebAppBase)) {
            return null;
        }
        //noinspection unchecked
        return (F) remote;
    }

    // MODIFY
    @AzureOperation(name = "azure/resource.start_resource.resource", params = {"this.getName()"})
    public void start() {
        this.doModify(() -> Objects.requireNonNull(this.getFullRemote()).start(), AzResource.Status.STARTING);
    }

    @AzureOperation(name = "azure/resource.stop_resource.resource", params = {"this.getName()"})
    public void stop() {
        this.doModify(() -> Objects.requireNonNull(this.getFullRemote()).stop(), AzResource.Status.STOPPING);
    }

    @AzureOperation(name = "azure/resource.restart_resource.resource", params = {"this.getName()"})
    public void restart() {
        this.doModify(() -> Objects.requireNonNull(this.getFullRemote()).restart(), AzResource.Status.RESTARTING);
    }

    @Nullable
    public String getHostName() {
        return this.remoteOptional().map(WebSiteBase::defaultHostname).orElse(null);
    }

    @Nullable
    public String getKuduHostName() {
        return this.remoteOptional().flatMap(siteBase -> siteBase.hostnameSslStates().values().stream()
                .filter(sslState -> sslState.hostType() == HostType.REPOSITORY)
                .map(HostnameSslState::name)
                .findFirst())
                .orElse(null);
    }

    @Nullable
    public String getLinuxFxVersion() {
        return Optional.ofNullable(this.getFullRemote()).map(WebAppBase::linuxFxVersion).orElse(null);
    }

    @Nullable
    public PublishingProfile getPublishingProfile() {
        return Optional.ofNullable(this.getFullRemote()).map(WebAppBase::getPublishingProfile).map(AppServiceUtils::fromPublishingProfile).orElse(null);
    }

    @Nullable
    public DiagnosticConfig getDiagnosticConfig() {
        return Optional.ofNullable(this.getFullRemote()).map(WebAppBase::diagnosticLogsConfig).map(AppServiceUtils::fromWebAppDiagnosticLogs).orElse(null);
    }

    @Override
    public Flux<String> streamingLogs(boolean follow, @Nonnull Map<String, String> params) {
        return Optional.ofNullable(this.getFullRemote()).map(WebAppBase::streamAllLogsAsync).orElseGet(Flux::empty);
    }

    @Nonnull
    public Flux<ByteBuffer> getFileContent(String path) {
        return Optional.ofNullable(getFileClient()).map(c -> c.getFileContent(path)).orElseGet(Flux::empty);
    }

    @Nonnull
    public List<? extends AppServiceFile> getFilesInDirectory(String dir) {
        return Optional.ofNullable(getFileClient()).map(c -> c.getFilesInDirectory(dir)).orElseGet(Collections::emptyList);
    }

    @Nullable
    public AppServiceFile getFileByPath(String path) {
        return Optional.ofNullable(getFileClient()).map(c -> c.getFileByPath(path)).orElse(null);
    }

    public void uploadFileToPath(String content, String path) {
        Optional.ofNullable(getFileClient()).ifPresent(c -> c.uploadFileToPath(content, path));
    }

    public void createDirectory(String path) {
        Optional.ofNullable(getFileClient()).ifPresent(c -> c.createDirectory(path));
    }

    public void deleteFile(String path) {
        Optional.ofNullable(getFileClient()).ifPresent(c -> c.deleteFile(path));
    }

    @Nonnull
    public List<ProcessInfo> listProcess() {
        return Optional.ofNullable(getProcessClient()).map(IProcessClient::listProcess).orElseGet(Collections::emptyList);
    }

    @Nullable
    public CommandOutput execute(String command, String dir) {
        return Optional.ofNullable(getProcessClient()).map(c -> c.execute(command, dir)).orElse(null);
    }

    public InputStream listPublishingProfileXmlWithSecrets() {
        final ResourceId resourceId = ResourceId.fromString(getId());
        final String resourceName = StringUtils.equals(resourceId.resourceType(), "slots") ?
            String.format("%s/slots/%s", resourceId.parent().name(), resourceId.name()) : resourceId.name();
        final CsmPublishingProfileOptions csmPublishingProfileOptions = new CsmPublishingProfileOptions().withFormat(PublishingProfileFormat.FTP);
        return Objects.requireNonNull(getFullRemote()).manager().serviceClient().getWebApps()
            .listPublishingProfileXmlWithSecrets(resourceId.resourceGroupName(), resourceName, csmPublishingProfileOptions).toStream();
    }

    @Nullable
    public TunnelStatus getAppServiceTunnelStatus() {
        return Optional.ofNullable(getProcessClient()).map(IProcessClient::getAppServiceTunnelStatus).orElse(null);
    }

    @Nullable
    public AppServicePlan getAppServicePlan() {
        final AppServicePlanModule plans = Azure.az(AzureAppService.class).plans(this.getSubscriptionId());
        return this.remoteOptional().map(WebSiteBase::appServicePlanId).map(plans::get).orElse(null);
    }

    @Nullable
    public Region getRegion() {
        return this.remoteOptional().map(WebSiteBase::regionName).map(Region::fromName).orElse(null);
    }

    @Nullable
    public Map<String, String> getAppSettings() {
        return Optional.ofNullable(this.getFullRemote()).map(WebAppBase::getAppSettings).map(Utils::normalizeAppSettings).orElse(null);
    }

    @Nullable
    public Runtime getRuntime() {
        return Optional.ofNullable(this.getFullRemote()).map(AppServiceUtils::getRuntimeFromAppService).orElse(null);
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull WebSiteBase remote) {
        return remote.state();
    }

    @Nullable
    protected IFileClient getFileClient() {
        return getKuduManager();
    }

    @Nullable
    protected IProcessClient getProcessClient() {
        return getKuduManager();
    }

    @Nullable
    protected AppServiceKuduClient getKuduManager() {
        if (kuduManager == null) {
            kuduManager = Optional.ofNullable(this.getFullRemote()).map(r -> AppServiceKuduClient.getClient(r, this)).orElse(null);
        }
        return kuduManager;
    }

    public boolean isStreamingLogSupported() {
        return false;
    }
}
