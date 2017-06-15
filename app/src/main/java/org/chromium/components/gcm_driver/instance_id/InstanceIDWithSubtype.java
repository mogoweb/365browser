// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.components.gcm_driver.instance_id;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.gms.iid.InstanceID;

import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * InstanceID wrapper that allows multiple InstanceIDs to be created, depending
 * on the provided subtype. Only for platforms-within-platforms like browsers.
 */
public class InstanceIDWithSubtype extends InstanceID {
    private final String mSubtype;

    /** Cached instances. May be accessed from multiple threads; synchronize on InstanceID.class. */
    @VisibleForTesting
    @SuppressFBWarnings("MS_MUTABLE_COLLECTION_PKGPROTECT")
    public static final Map<String, InstanceIDWithSubtype> sSubtypeInstances = new HashMap<>();

    /** Fake subclasses can set this so getInstance creates instances of them. */
    @VisibleForTesting
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    protected static FakeFactory sFakeFactoryForTesting;

    protected InstanceIDWithSubtype(Context context, String subtype) {
        super(context, subtype, null /* options */);
        mSubtype = subtype;
    }

    /**
     * Returns an instance of this class. Unlike {@link InstanceID#getInstance(Context)}, it is not
     * a singleton, but instead a different instance will be returned for each {@code subtype}.
     */
    public static InstanceIDWithSubtype getInstance(Context context, String subtype) {
        if (TextUtils.isEmpty(subtype)) {
            throw new IllegalArgumentException("subtype must not be empty");
        }
        context = context.getApplicationContext();

        // Synchronize on the base class, to match the synchronized statements in
        // InstanceID.getInstance.
        synchronized (InstanceID.class) {
            if (sSubtypeInstances.isEmpty() && sFakeFactoryForTesting == null) {
                // The static InstanceID.getInstance method performs some one-time initialization
                // logic that is also necessary for users of this sub-class. To work around this,
                // first get (but don't use) the default InstanceID.
                InstanceID.getInstance(context);
            }

            InstanceIDWithSubtype existing = sSubtypeInstances.get(subtype);
            if (existing == null) {
                if (sFakeFactoryForTesting != null) {
                    existing = sFakeFactoryForTesting.create(context, subtype);
                } else {
                    existing = new InstanceIDWithSubtype(context, subtype);
                }
                sSubtypeInstances.put(subtype, existing);
            }
            return existing;
        }
    }

    @Override
    public void deleteInstanceID() throws IOException {
        // Synchronize on the base class, to match getInstance.
        synchronized (InstanceID.class) {
            sSubtypeInstances.remove(mSubtype);
            super.deleteInstanceID();
        }
    }

    public String getSubtype() {
        return mSubtype;
    }

    /** Fake subclasses can set {@link #sFakeFactoryForTesting} to an implementation of this. */
    @VisibleForTesting
    public interface FakeFactory {
        public InstanceIDWithSubtype create(Context context, String subtype);
    }
}