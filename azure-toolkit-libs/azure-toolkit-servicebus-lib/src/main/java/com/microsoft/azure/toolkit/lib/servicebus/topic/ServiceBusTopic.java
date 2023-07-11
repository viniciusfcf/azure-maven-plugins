/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.servicebus.topic;

import com.azure.messaging.servicebus.*;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.resourcemanager.servicebus.ServiceBusManager;
import com.azure.resourcemanager.servicebus.fluent.ServiceBusManagementClient;
import com.azure.resourcemanager.servicebus.fluent.models.SBAuthorizationRuleInner;
import com.azure.resourcemanager.servicebus.fluent.models.SBTopicInner;
import com.azure.resourcemanager.servicebus.models.*;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import com.microsoft.azure.toolkit.lib.servicebus.ServiceBusNamespace;
import com.microsoft.azure.toolkit.lib.servicebus.model.ServiceBusInstance;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class ServiceBusTopic extends ServiceBusInstance<ServiceBusTopic, ServiceBusNamespace, Topic> implements Deletable {
    protected ServiceBusTopic(@Nonnull String name, @Nonnull ServiceBusTopicModule module) {
        super(name, module);
    }

    protected ServiceBusTopic(@Nonnull Topic remote, @Nonnull ServiceBusTopicModule module) {
        super(remote.name(), module);
    }

    @Override
    protected void updateAdditionalProperties(@Nullable Topic newRemote, @Nullable Topic oldRemote) {
        super.updateAdditionalProperties(newRemote, oldRemote);
        this.entityStatus = Optional.ofNullable(newRemote).map(Topic::innerModel).map(SBTopicInner::status).orElse(null);
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull Topic remote) {
        return remote.innerModel().status().toString();
    }

    @Override
    public void updateStatus(EntityStatus status) {
        final SBTopicInner inner = remoteOptional().map(Topic::innerModel).orElse(new SBTopicInner());
        final ServiceBusNamespace namespace = this.getParent();
        Optional.ofNullable(namespace.getParent().getRemote())
                .map(ServiceBusManager::serviceClient)
                .map(ServiceBusManagementClient::getTopics)
                .ifPresent(c -> doModify(() -> c.createOrUpdate(getResourceGroupName(), namespace.getName(), getName(), inner.withStatus(status)), Status.UPDATING));
    }

    @Override
    public void sendMessage(String message) {
        final IAzureMessager messager = AzureMessager.getMessager();
        messager.info(AzureString.format("Sending message to Service Bus Topic (%s)...\n", getName()));
        try (final ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
                .connectionString(getOrCreateConnectionString(Collections.singletonList(AccessRights.SEND)))
                .sender()
                .topicName(getName())
                .buildClient()) {
            senderClient.sendMessage(new ServiceBusMessage(message));
            messager.info("Successfully sent message ");
            messager.success(AzureString.format("\"%s\"", message));
            messager.info(AzureString.format(" to Service Bus Topic (%s)\n", getName()));
        } catch (final Exception e) {
            messager.error(AzureString.format("Failed to send message to Service Bus Topic (%s): %s", getName(), e));
        }
    }

    @Override
    public synchronized void startReceivingMessage() {
        messager = AzureMessager.getMessager();
        messager.info(AzureString.format("Start listening to Service Bus Topic ({0})\n", getName()));
        this.processorClient = new ServiceBusClientBuilder()
                .connectionString(getOrCreateConnectionString(Collections.singletonList(AccessRights.LISTEN)))
                .processor()
                .topicName(getName())
                .subscriptionName(getOrCreateSubscription().name())
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                .processMessage(this::processMessage)
                .processError(this::processError)
                .disableAutoComplete()  // Complete - causes the message to be deleted from the queue or topic.
                .buildProcessorClient();
        processorClient.start();
    }

    @Override
    protected String getOrCreateConnectionString(List<AccessRights> accessRights) {
        final List<TopicAuthorizationRule> connectionStrings = Optional.ofNullable(getRemote())
                .map(topic -> topic.authorizationRules().list().stream()
                        .filter(rule -> new HashSet<>(rule.rights()).containsAll(accessRights))
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());
        if (connectionStrings.size() > 0) {
            return connectionStrings.get(0).getKeys().primaryConnectionString();
        }
        final ServiceBusManager manager = getParent().getParent().getRemote();
        if (Objects.isNull(manager)) {
            throw new AzureToolkitRuntimeException(AzureString.format("resource ({0}) not found", getName()).toString());
        }
        final String accessRightsStr = StringUtils.join(accessRights, "-");
        manager.serviceClient().getTopics().createOrUpdateAuthorizationRule(getResourceGroupName(), getParent().getName(),
                getName(), accessRightsStr, new SBAuthorizationRuleInner().withRights(accessRights));
        return manager.serviceClient().getTopics().listKeys(getResourceGroupName(), getParent().getName(),
                getName(), accessRightsStr).primaryConnectionString();
    }

    private ServiceBusSubscription getOrCreateSubscription() {
        final Topic remoteTopic = this.getRemote();
        if (Objects.isNull(remoteTopic)) {
            throw new AzureToolkitRuntimeException(AzureString.format("resource ({0}) not found", getName()).toString());
        }
        final int subscriptionCount = remoteTopic.subscriptionCount();
        if (subscriptionCount > 0) {
            return remoteTopic.subscriptions().list().stream().collect(Collectors.toList()).get(0);
        }
        final String subName = String.format("sub-%s", Utils.getTimestamp());
        return remoteTopic.subscriptions().define(subName)
                .withMessageMovedToDeadLetterSubscriptionOnMaxDeliveryCount(10)
                .create();
    }
}
