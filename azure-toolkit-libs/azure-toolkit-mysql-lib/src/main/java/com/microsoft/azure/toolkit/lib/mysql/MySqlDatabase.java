/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.mysql;

import com.azure.resourcemanager.mysqlflexibleserver.models.Database;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.database.JdbcUrl;
import com.microsoft.azure.toolkit.lib.database.entity.IDatabase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class MySqlDatabase extends AbstractAzResource<MySqlDatabase, MySqlServer, Database> implements IDatabase {

    protected MySqlDatabase(@Nonnull String name, @Nonnull MySqlDatabaseModule module) {
        super(name, module);
    }

    protected MySqlDatabase(@Nonnull Database remote, @Nonnull MySqlDatabaseModule module) {
        super(remote.name(), module);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull Database remote) {
        return Status.ONLINE;
    }

    @Nullable
    @Override
    public String getCollation() {
        return this.remoteOptional().map(Database::collation).orElse(null);
    }

    @Nonnull
    @Override
    public MySqlServer getServer() {
        return this.getParent();
    }

    @Nullable
    public String getCharset() {
        return this.remoteOptional().map(Database::charset).orElse(null);
    }

    @Nonnull
    public JdbcUrl getJdbcUrl() {
        return JdbcUrl.mysql(this.getParent().getFullyQualifiedDomainName(), this.getName());
    }
}
