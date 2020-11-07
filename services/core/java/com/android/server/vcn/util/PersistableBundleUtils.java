/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.vcn.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.ParcelUuid;
import android.os.PersistableBundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** @hide */
public class PersistableBundleUtils {
    private static final String LIST_KEY_FORMAT = "LIST_ITEM_%d";
    private static final String COLLECTION_SIZE_KEY = "COLLECTION_LENGTH";
    private static final String MAP_KEY_FORMAT = "MAP_KEY_%d";
    private static final String MAP_VALUE_FORMAT = "MAP_VALUE_%d";

    private static final String PARCEL_UUID_KEY = "PARCEL_UUID";

    /**
     * Functional interface to convert an object of the specified type to a PersistableBundle.
     *
     * @param <T> the type of the source object
     */
    public interface Serializer<T> {
        /**
         * Converts this object to a PersistableBundle.
         *
         * @return the PersistableBundle representation of this object
         */
        PersistableBundle toPersistableBundle(T obj);
    }

    /**
     * Functional interface used to create an object of the specified type from a PersistableBundle.
     *
     * @param <T> the type of the resultant object
     */
    public interface Deserializer<T> {
        /**
         * Creates an instance of specified type from a PersistableBundle representation.
         *
         * @param in the PersistableBundle representation
         * @return an instance of the specified type
         */
        T fromPersistableBundle(PersistableBundle in);
    }

    /**
     * Converts a ParcelUuid to a PersistableBundle.
     *
     * <p>To avoid key collisions, NO additional key/value pairs should be added to the returned
     * PersistableBundle object.
     *
     * @param uuid a ParcelUuid instance to persist
     * @return the PersistableBundle instance
     */
    public static PersistableBundle fromParcelUuid(ParcelUuid uuid) {
        final PersistableBundle result = new PersistableBundle();

        result.putString(PARCEL_UUID_KEY, uuid.toString());

        return result;
    }

    /**
     * Converts from a PersistableBundle to a ParcelUuid.
     *
     * @param bundle the PersistableBundle containing the ParcelUuid
     * @return the ParcelUuid instance
     */
    public static ParcelUuid toParcelUuid(PersistableBundle bundle) {
        return ParcelUuid.fromString(bundle.getString(PARCEL_UUID_KEY));
    }

    /**
     * Converts from a list of Persistable objects to a single PersistableBundle.
     *
     * <p>To avoid key collisions, NO additional key/value pairs should be added to the returned
     * PersistableBundle object.
     *
     * @param <T> the type of the objects to convert to the PersistableBundle
     * @param in the list of objects to be serialized into a PersistableBundle
     * @param serializer an implementation of the {@link Serializer} functional interface that
     *     converts an object of type T to a PersistableBundle
     */
    @NonNull
    public static <T> PersistableBundle fromList(
            @NonNull List<T> in, @NonNull Serializer<T> serializer) {
        final PersistableBundle result = new PersistableBundle();

        result.putInt(COLLECTION_SIZE_KEY, in.size());
        for (int i = 0; i < in.size(); i++) {
            final String key = String.format(LIST_KEY_FORMAT, i);
            result.putPersistableBundle(key, serializer.toPersistableBundle(in.get(i)));
        }
        return result;
    }

    /**
     * Converts from a PersistableBundle to a list of objects.
     *
     * @param <T> the type of the objects to convert from a PersistableBundle
     * @param in the PersistableBundle containing the persisted list
     * @param deserializer an implementation of the {@link Deserializer} functional interface that
     *     builds the relevant type of objects.
     */
    @NonNull
    public static <T> List<T> toList(
            @NonNull PersistableBundle in, @NonNull Deserializer<T> deserializer) {
        final int listLength = in.getInt(COLLECTION_SIZE_KEY);
        final ArrayList<T> result = new ArrayList<>(listLength);

        for (int i = 0; i < listLength; i++) {
            final String key = String.format(LIST_KEY_FORMAT, i);
            final PersistableBundle item = in.getPersistableBundle(key);

            result.add(deserializer.fromPersistableBundle(item));
        }
        return result;
    }

    /**
     * Converts from a Map of Persistable objects to a single PersistableBundle.
     *
     * <p>To avoid key collisions, NO additional key/value pairs should be added to the returned
     * PersistableBundle object.
     *
     * @param <K> the type of the map-key to convert to the PersistableBundle
     * @param <V> the type of the map-value to convert to the PersistableBundle
     * @param in the Map of objects implementing the {@link Persistable} interface
     * @param keySerializer an implementation of the {@link Serializer} functional interface that
     *     converts a map-key of type T to a PersistableBundle
     * @param valueSerializer an implementation of the {@link Serializer} functional interface that
     *     converts a map-value of type E to a PersistableBundle
     */
    @NonNull
    public static <K, V> PersistableBundle fromMap(
            @NonNull Map<K, V> in,
            @NonNull Serializer<K> keySerializer,
            @NonNull Serializer<V> valueSerializer) {
        final PersistableBundle result = new PersistableBundle();

        result.putInt(COLLECTION_SIZE_KEY, in.size());
        int i = 0;
        for (Entry<K, V> entry : in.entrySet()) {
            final String keyKey = String.format(MAP_KEY_FORMAT, i);
            final String valueKey = String.format(MAP_VALUE_FORMAT, i);
            result.putPersistableBundle(keyKey, keySerializer.toPersistableBundle(entry.getKey()));
            result.putPersistableBundle(
                    valueKey, valueSerializer.toPersistableBundle(entry.getValue()));

            i++;
        }

        return result;
    }

    /**
     * Converts from a PersistableBundle to a Map of objects.
     *
     * <p>In an attempt to preserve ordering, the returned map will be a LinkedHashMap. However, the
     * guarantees on the ordering can only ever be as strong as the map that was serialized in
     * {@link fromMap()}. If the initial map that was serialized had no ordering guarantees, the
     * deserialized map similarly may be of a non-deterministic order.
     *
     * @param <K> the type of the map-key to convert from a PersistableBundle
     * @param <V> the type of the map-value to convert from a PersistableBundle
     * @param in the PersistableBundle containing the persisted Map
     * @param keyDeserializer an implementation of the {@link Deserializer} functional interface
     *     that builds the relevant type of map-key.
     * @param valueDeserializer an implementation of the {@link Deserializer} functional interface
     *     that builds the relevant type of map-value.
     * @return An instance of the parsed map as a LinkedHashMap (in an attempt to preserve
     *     ordering).
     */
    @NonNull
    public static <K, V> LinkedHashMap<K, V> toMap(
            @NonNull PersistableBundle in,
            @NonNull Deserializer<K> keyDeserializer,
            @NonNull Deserializer<V> valueDeserializer) {
        final int mapSize = in.getInt(COLLECTION_SIZE_KEY);
        final LinkedHashMap<K, V> result = new LinkedHashMap<>(mapSize);

        for (int i = 0; i < mapSize; i++) {
            final String keyKey = String.format(MAP_KEY_FORMAT, i);
            final String valueKey = String.format(MAP_VALUE_FORMAT, i);
            final PersistableBundle keyBundle = in.getPersistableBundle(keyKey);
            final PersistableBundle valueBundle = in.getPersistableBundle(valueKey);

            final K key = keyDeserializer.fromPersistableBundle(keyBundle);
            final V value = valueDeserializer.fromPersistableBundle(valueBundle);
            result.put(key, value);
        }
        return result;
    }

    /**
     * Ensures safe reading and writing of {@link PersistableBundle}s to and from disk.
     *
     * <p>This class will enforce exclusion between reads and writes using the standard semantics of
     * a ReadWriteLock. Specifically, concurrent readers ARE allowed, but reads/writes from/to the
     * file are mutually exclusive. In other words, for an unbounded number n, the acceptable states
     * are n readers, OR 1 writer (but not both).
     */
    public static class LockingReadWriteHelper {
        private final ReadWriteLock mDiskLock = new ReentrantReadWriteLock();
        private final String mPath;

        public LockingReadWriteHelper(@NonNull String path) {
            mPath = Objects.requireNonNull(path, "fileName was null");
        }

        /**
         * Reads the {@link PersistableBundle} from the disk.
         *
         * @return the PersistableBundle, if the file existed, or null otherwise
         */
        @Nullable
        public PersistableBundle readFromDisk() throws IOException {
            try {
                mDiskLock.readLock().lock();
                final File file = new File(mPath);
                if (!file.exists()) {
                    return null;
                }

                try (FileInputStream fis = new FileInputStream(file)) {
                    return PersistableBundle.readFromStream(fis);
                }
            } finally {
                mDiskLock.readLock().unlock();
            }
        }

        /**
         * Writes a {@link PersistableBundle} to disk.
         *
         * @param bundle the {@link PersistableBundle} to write to disk
         */
        public void writeToDisk(@NonNull PersistableBundle bundle) throws IOException {
            Objects.requireNonNull(bundle, "bundle was null");

            try {
                mDiskLock.writeLock().lock();
                final File file = new File(mPath);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bundle.writeToStream(fos);
                }
            } finally {
                mDiskLock.writeLock().unlock();
            }
        }
    }
}
