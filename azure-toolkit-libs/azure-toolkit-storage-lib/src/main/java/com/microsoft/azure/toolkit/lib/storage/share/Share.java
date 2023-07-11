/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.storage.share;

import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.models.ShareProperties;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AbstractEmulatableAzResource;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.storage.StorageAccount;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class Share extends AbstractEmulatableAzResource<Share, StorageAccount, ShareClient>
    implements Deletable, IShareFile {

    private final ShareFileModule subFileModule;

    protected Share(@Nonnull String name, @Nonnull ShareModule module) {
        super(name, module);
        this.subFileModule = new ShareFileModule(this);
    }

    /**
     * copy constructor
     */
    public Share(@Nonnull Share origin) {
        super(origin);
        this.subFileModule = origin.subFileModule;
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.singletonList(this.subFileModule);
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull ShareClient remote) {
        return "";
    }

    @Override
    @Nullable
    public ShareDirectoryClient getClient() {
        return Optional.ofNullable(getShareClient()).map(ShareClient::getRootDirectoryClient).orElse(null);
    }

    @Nullable
    public ShareClient getShareClient() {
        final ShareModule module = (ShareModule) this.getModule();
        final ShareServiceClient fileShareServiceClient = module.getFileShareServiceClient();
        return Optional.ofNullable(fileShareServiceClient).map(c -> c.getShareClient(this.getName())).orElse(null);
    }

    @Override
    public Share getShare() {
        return this;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public String getUrl() {
        return Optional.ofNullable(getShareClient()).map(ShareClient::getShareUrl).orElse("");
    }

    @Nullable
    @Override
    public OffsetDateTime getLastModified() {
        return this.remoteOptional().map(ShareClient::getProperties).map(ShareProperties::getLastModified).orElse(null);
    }

    @Override
    public void download(OutputStream output) {
    }

    @Override
    public void download(Path dest) {
    }
}
