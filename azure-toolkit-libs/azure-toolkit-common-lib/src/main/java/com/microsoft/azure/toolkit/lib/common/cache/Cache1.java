/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@SuppressWarnings("UnusedReturnValue")
public class Cache1<T> {
    private static final String KEY = "CACHE1_KEY";
    private final LoadingCache<String, Optional<T>> cache;
    private final Supplier<T> supplier;
    @Getter
    @Nullable
    private String status = null;
    private BiConsumer<T, T> onNewValue = (n, o) -> {
    };
    private Consumer<String> onNewStatus = s -> {
    };
    private T last = null;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Cache1(@Nonnull Supplier<T> supplier) {
        this.supplier = supplier;
        this.cache = Caffeine.newBuilder().build(key -> Cache1.this.load());
    }

    public Cache1<T> onValueChanged(BiConsumer<T, T> onNewValue) {
        this.onNewValue = onNewValue;
        return this;
    }

    public Cache1<T> onStatusChanged(Consumer<String> onNewStatus) {
        this.onNewStatus = onNewStatus;
        return this;
    }

    private Optional<T> load() {
        try {
            this.setStatus(Status.LOADING);
            final T value = this.last = supplier.get();
            final Optional<T> result = Optional.ofNullable(value);
            String s = Status.OK;
            try {
                result.ifPresent(newValue -> this.onNewValue.accept(newValue, null));
            } catch (final Throwable ignored) {
                s = Status.UNKNOWN;
            }
            this.setStatus(s);
            return result;
        } catch (final Throwable e) {
            log.error(e.getMessage(), e);
            this.setStatus(Status.UNKNOWN);
            throw e;
        }
    }

    @Nullable
    @SneakyThrows
    public synchronized T update(@Nonnull Callable<T> body, String status) {
        final T oldValue = this.getIfPresent();
        this.invalidate();
        try {
            return this.cache.get(KEY, (key) -> {
                try {
                    this.setStatus(Optional.ofNullable(status).orElse(Status.UPDATING));
                    final T value = this.last = body.call();
                    final Optional<T> result = Optional.ofNullable(value);
                    final T newValue = result.orElse(null);
                    String s = Status.OK;
                    if (!Objects.equals(newValue, oldValue)) {
                        try {
                            this.onNewValue.accept(newValue, oldValue);
                        } catch (final Throwable ignored) {
                            s = Status.UNKNOWN;
                        }
                    }
                    this.setStatus(s);
                    return result;
                } catch (final Throwable e) {
                    this.setStatus(Status.UNKNOWN);
                    throw (e instanceof AzureToolkitRuntimeException) ? (AzureToolkitRuntimeException) e : new AzureToolkitRuntimeException(e);
                }
            }).orElse(null);
        } catch (final Throwable e) {
            log.error(e.getMessage(), e);
            throw e.getCause();
        }
    }

    @Nullable
    public T update(@Nonnull Runnable body, String status) {
        return this.update(() -> {
            body.run();
            return load().orElse(null);
        }, status);
    }

    @Nullable
    public T getIfPresent() {
        return this.getIfPresent(false);
    }

    @Nullable
    @SuppressWarnings("OptionalAssignedToNull")
    public T getIfPresent(boolean loadIfAbsent) {
        final Optional<T> opt = this.cache.getIfPresent(KEY);
        if (opt == null) {
            if (loadIfAbsent) {
                AzureTaskManager.getInstance().runOnPooledThread(this::refresh);
            }
            return this.last;
        }
        return opt.orElse(null);
    }

    public void refresh() {
        this.cache.refresh(KEY);
    }

    @Nullable
    public T get() {
        try {
            return CompletableFuture.supplyAsync(() -> { // prevent interruption of cache loading thread from e.g. debouncing thread
                try {
                    return this.cache.get(KEY).orElse(null);
                } catch (final IllegalStateException e) {
                    if ("Recursive update".equalsIgnoreCase(e.getMessage())) {
                        return this.last;
                    }
                    throw e;
                }
            }, executor).join();
        } catch (final CompletionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }

    public void invalidate() {
        this.cache.invalidateAll();
    }

    private void setStatus(String status) {
        this.status = status;
        Optional.ofNullable(this.onNewStatus).ifPresent(c -> c.accept(status));
    }

    public static interface Status {
        String LOADING = "Loading";
        String UPDATING = "Updating";
        String OK = "OK";
        String UNKNOWN = "Unknown";
    }
}
