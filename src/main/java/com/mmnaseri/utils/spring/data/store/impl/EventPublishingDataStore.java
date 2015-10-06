package com.mmnaseri.utils.spring.data.store.impl;

import com.mmnaseri.utils.spring.data.domain.RepositoryMetadata;
import com.mmnaseri.utils.spring.data.store.DataStore;
import com.mmnaseri.utils.spring.data.store.DataStoreEvent;
import com.mmnaseri.utils.spring.data.store.DataStoreEventListener;
import com.mmnaseri.utils.spring.data.store.DataStoreEventPublisher;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Mohammad Milad Naseri (m.m.naseri@gmail.com)
 * @since 1.0 (10/6/15)
 */
public class EventPublishingDataStore<K extends Serializable, E> implements DataStore<K, E>, DataStoreEventPublisher {

    private final DataStore<K, E> delegate;
    private final RepositoryMetadata repositoryMetadata;
    private final Map<Class<? extends DataStoreEvent>, List<DataStoreEventListener<?>>> listeners;

    public EventPublishingDataStore(DataStore<K, E> delegate, RepositoryMetadata repositoryMetadata) {
        this.delegate = delegate;
        this.repositoryMetadata = repositoryMetadata;
        listeners = new ConcurrentHashMap<Class<? extends DataStoreEvent>, List<DataStoreEventListener<?>>>();
    }

    @Override
    public boolean hasKey(K key) {
        return delegate.hasKey(key);
    }

    @Override
    public void save(K key, E entity) {
        final boolean entityIsNew = !delegate.hasKey(key);
        if (entityIsNew) {
            publishEvent(new BeforeInsertDataStoreEvent(repositoryMetadata, this, entity));
        } else {
            publishEvent(new BeforeUpdateDataStoreEvent(repositoryMetadata, this, entity));
        }
        delegate.save(key, entity);
        if (entityIsNew) {
            publishEvent(new AfterInsertDataStoreEvent(repositoryMetadata, this, entity));
        } else {
            publishEvent(new AfterUpdateDataStoreEvent(repositoryMetadata, this, entity));
        }
    }

    @Override
    public void delete(K key) {
        if (!delegate.hasKey(key)) {
            return;
        }
        final E entity = delegate.retrieve(key);
        publishEvent(new BeforeDeleteDataStoreEvent(repositoryMetadata, this, entity));
        delegate.delete(key);
        publishEvent(new AfterDeleteDataStoreEvent(repositoryMetadata, this, entity));
    }

    @Override
    public E retrieve(K key) {
        return delegate.retrieve(key);
    }

    @Override
    public Collection<E> retrieveAll() {
        return delegate.retrieveAll();
    }

    @Override
    public Class<E> getEntityType() {
        return delegate.getEntityType();
    }

    public <V extends DataStoreEvent> void addEventListener(DataStoreEventListener<V> listener) {
        final SmartDataStoreEventListener<V> smartListener = new SmartDataStoreEventListener<V>(listener);
        if (!listeners.containsKey(smartListener.getEventType())) {
            listeners.put(smartListener.getEventType(), new CopyOnWriteArrayList<DataStoreEventListener<?>>());
        }
        final List<DataStoreEventListener<?>> eventListeners = listeners.get(smartListener.getEventType());
        eventListeners.add(smartListener);
    }

    @Override
    public void publishEvent(DataStoreEvent event) {
        for (Class<? extends DataStoreEvent> eventType : listeners.keySet()) {
            if (eventType.isInstance(event)) {
                final List<DataStoreEventListener<?>> eventListeners = listeners.get(eventType);
                for (DataStoreEventListener eventListener : eventListeners) {
                    //noinspection unchecked
                    eventListener.onEvent(event);
                }
            }
        }
    }

}
