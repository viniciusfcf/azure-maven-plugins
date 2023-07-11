/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.cosmos.sql;

import com.azure.resourcemanager.cosmos.fluent.models.SqlDatabaseGetResultsInner;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.cosmos.CosmosDBAccount;
import com.microsoft.azure.toolkit.lib.cosmos.ICosmosCollection;
import com.microsoft.azure.toolkit.lib.cosmos.ICosmosDatabase;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class SqlDatabase extends AbstractAzResource<SqlDatabase, CosmosDBAccount, SqlDatabaseGetResultsInner> implements Deletable, ICosmosDatabase {

    private final SqlContainerModule containerModule;

    protected SqlDatabase(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull SqlDatabaseModule module) {
        super(name, resourceGroupName, module);
        this.containerModule = new SqlContainerModule(this);
    }

    protected SqlDatabase(@Nonnull SqlDatabase account) {
        super(account);
        this.containerModule = new SqlContainerModule(this);
    }

    protected SqlDatabase(@Nonnull SqlDatabaseGetResultsInner remote, @Nonnull SqlDatabaseModule module) {
        super(remote.name(), module);
        this.containerModule = new SqlContainerModule(this);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.singletonList(containerModule);
    }

    public SqlContainerModule containers() {
        return this.containerModule;
    }

    public Region getRegion() {
        return getParent().getRegion();
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull SqlDatabaseGetResultsInner remote) {
        return Status.RUNNING;
    }

    @Override
    public List<? extends ICosmosCollection> listCollection() {
        return this.containers().list();
    }
}
