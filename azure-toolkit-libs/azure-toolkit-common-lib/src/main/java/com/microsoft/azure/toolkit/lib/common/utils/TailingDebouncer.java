/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.utils;

import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class TailingDebouncer implements Debouncer {
    private final Runnable debounced;
    private final int delay;
    private Subscription timer;

    public TailingDebouncer(final Runnable debounced, final int delayInMillis) {
        this.debounced = debounced;
        this.delay = delayInMillis;
    }

    @Override
    public synchronized void debounce(int delay) {
        if (this.isPending()) {
            this.timer.unsubscribe();
        }
        delay = delay < 0 ? this.delay : delay;
        this.timer = Observable.timer(delay, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .subscribe(ignore -> {
                this.debounced.run();
                this.clearTimer();
            }, (e) -> this.clearTimer());
    }

    @Override
    public synchronized void debounce() {
        this.debounce(this.delay);
    }

    public synchronized void cancel() {
        if (this.isPending()) {
            this.timer.unsubscribe();
        }
    }

    public synchronized boolean isPending() {
        return Objects.nonNull(this.timer) && !this.timer.isUnsubscribed();
    }

    private synchronized void clearTimer() {
        this.timer = null;
    }
}
