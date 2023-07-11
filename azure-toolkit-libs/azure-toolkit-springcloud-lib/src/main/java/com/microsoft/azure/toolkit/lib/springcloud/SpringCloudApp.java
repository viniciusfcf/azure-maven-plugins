/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.springcloud;

import com.azure.resourcemanager.appplatform.models.PersistentDisk;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.microsoft.azure.toolkit.lib.common.cache.Cache1;
import com.microsoft.azure.toolkit.lib.common.event.AzureEventBus;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.common.model.Startable;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.springcloud.model.SpringCloudPersistentDisk;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
public class SpringCloudApp extends AbstractAzResource<SpringCloudApp, SpringCloudCluster, SpringApp>
    implements Startable, Deletable {

    @Nonnull
    private final SpringCloudDeploymentModule deploymentModule;
    @Nonnull
    private Cache1<SpringCloudDeployment> activeDeployment = new Cache1<>(() -> this.remoteOptional().map(SpringApp::activeDeploymentName)
        .map(name -> this.deployments().get(name, this.getResourceGroupName())).orElse(null));

    protected SpringCloudApp(@Nonnull String name, @Nonnull SpringCloudAppModule module) {
        super(name, module);
        this.deploymentModule = new SpringCloudDeploymentModule(this);
    }

    /**
     * copy constructor
     */
    protected SpringCloudApp(@Nonnull SpringCloudApp origin) {
        super(origin);
        this.deploymentModule = origin.deploymentModule;
        this.activeDeployment = origin.activeDeployment;
    }

    protected SpringCloudApp(@Nonnull SpringApp remote, @Nonnull SpringCloudAppModule module) {
        super(remote.name(), module);
        this.deploymentModule = new SpringCloudDeploymentModule(this);
    }

    @Override
    public void invalidateCache() {
        super.invalidateCache();
        this.activeDeployment.invalidate();
    }

    @Override
    protected void updateAdditionalProperties(final SpringApp newRemote, final SpringApp oldRemote) {
        super.updateAdditionalProperties(newRemote, oldRemote);
        this.activeDeployment.get();
        AzureEventBus.emit("resource.refreshed.resource", this);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.singletonList(deploymentModule);
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull SpringApp remote) {
        final SpringCloudDeployment activeDeployment = this.getActiveDeployment();
        if (Objects.isNull(activeDeployment)) {
            return Status.INACTIVE;
        }
        return activeDeployment.getStatus();
    }

    @Nonnull
    public SpringCloudDeploymentModule deployments() {
        return this.deploymentModule;
    }

    // MODIFY
    @AzureOperation(name = "azure/resource.start_resource.resource", params = {"this.name()"})
    public void start() {
        this.doModify(() -> Objects.requireNonNull(this.getActiveDeployment()).start(), Status.STARTING);
        this.refresh();
    }

    @AzureOperation(name = "azure/resource.stop_resource.resource", params = {"this.name()"})
    public void stop() {
        this.doModify(() -> Objects.requireNonNull(this.getActiveDeployment()).stop(), Status.STOPPING);
        this.refresh();
    }

    @AzureOperation(name = "azure/resource.restart_resource.resource", params = {"this.name()"})
    public void restart() {
        this.doModify(() -> Objects.requireNonNull(this.getActiveDeployment()).restart(), Status.RESTARTING);
        this.refresh();
    }

    // READ
    public boolean isPublicEndpointEnabled() {
        return this.remoteOptional().map(SpringApp::isPublic).orElse(false);
    }

    @Nullable
    public synchronized String getActiveDeploymentName() {
        return Optional.ofNullable(this.getActiveDeployment()).map(AbstractAzResource::getName).orElse(null);
    }

    @Nullable
    public SpringCloudDeployment getActiveDeployment() {
        return this.activeDeployment.get();
    }

    @Nullable
    public SpringCloudDeployment getCachedActiveDeployment() {
        return this.activeDeployment.getIfPresent();
    }

    @Nullable
    public String getApplicationUrl() {
        final String url = Optional.ofNullable(this.getRemote()).map(SpringApp::url).orElse(null);
        return StringUtils.isBlank(url) || "None".equalsIgnoreCase(url) ? null : url;
    }

    @SuppressWarnings("unused")
    @Nullable
    public String getTestUrl() {
        if (this.getParent().isConsumptionTier()) {
            return null;
        }
        return Optional.ofNullable(this.getTestEndpoint())
            .map(e -> String.format("%s/%s/%s", e, this.getName(), Objects.requireNonNull(this.getRemote()).activeDeploymentName()))
            .orElse(null);
    }

    @Nullable
    public String getTestEndpoint() {
        return Optional.ofNullable(this.getRemote()).map(SpringApp::activeDeploymentName)
            .map(d -> Objects.requireNonNull(this.getRemote()).parent().listTestKeys().primaryTestEndpoint())
            .orElse(null);
    }

    @Nullable
    public SpringCloudPersistentDisk getPersistentDisk() {
        final PersistentDisk disk = Optional.ofNullable(this.getRemote()).map(SpringApp::persistentDisk).orElse(null);
        return Optional.ofNullable(disk).filter(d -> d.sizeInGB() > 0)
            .map(d -> SpringCloudPersistentDisk.builder()
                .sizeInGB(disk.sizeInGB())
                .mountPath(disk.mountPath())
                .usedInGB(disk.usedInGB()).build())
            .orElse(null);
    }

    public boolean isPersistentDiskEnabled() {
        return !this.getParent().isEnterpriseTier() && Objects.nonNull(this.getPersistentDisk());
    }
}
