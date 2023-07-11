/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.storage.queue;

import com.azure.storage.queue.QueueClient;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AbstractEmulatableAzResource;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.storage.StorageAccount;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class Queue extends AbstractEmulatableAzResource<Queue, StorageAccount, QueueClient>
    implements Deletable {

    protected Queue(@Nonnull String name, @Nonnull QueueModule module) {
        super(name, module);
    }

    /**
     * copy constructor
     */
    public Queue(@Nonnull Queue origin) {
        super(origin);
    }

    protected Queue(@Nonnull QueueClient remote, @Nonnull QueueModule module) {
        super(remote.getQueueName(), module.getParent().getResourceGroupName(), module);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull QueueClient remote) {
        return "";
    }
}
