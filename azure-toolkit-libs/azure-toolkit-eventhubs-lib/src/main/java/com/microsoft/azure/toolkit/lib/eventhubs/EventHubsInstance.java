package com.microsoft.azure.toolkit.lib.eventhubs;

import com.azure.resourcemanager.eventhubs.fluent.models.EventhubInner;
import com.azure.resourcemanager.eventhubs.models.EntityStatus;
import com.azure.resourcemanager.eventhubs.models.EventHub;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EventHubsInstance extends AbstractAzResource<EventHubsInstance, EventHubsNamespace, EventHub> {
    @Nullable
    private EntityStatus status;
    protected EventHubsInstance(@Nonnull String name, @Nonnull EventHubsInstanceModule module) {
        super(name, module);
    }

    protected EventHubsInstance(@Nonnull EventHub remote, @Nonnull EventHubsInstanceModule module) {
        super(remote.name(), module);
    }

    @Override
    protected void updateAdditionalProperties(@Nullable EventHub newRemote, @Nullable EventHub oldRemote) {
        super.updateAdditionalProperties(newRemote, oldRemote);
        this.status = Optional.ofNullable(newRemote).map(EventHub::innerModel).map(EventhubInner::status).orElse(null);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public String loadStatus(@Nonnull EventHub remote) {
        return remote.innerModel().status().toString();
    }

    public boolean isActive() {
        return Objects.equals(this.status, EntityStatus.ACTIVE);
    }

    public boolean isDisabled() {
        return Objects.equals(this.status, EntityStatus.DISABLED);
    }

    public boolean isSendDisabled() {
        return Objects.equals(this.status, EntityStatus.SEND_DISABLED);
    }
}
