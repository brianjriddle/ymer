/*
 * Copyright 2015 Avanza Bank AB
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
package se.avanzabank.mongodb.support.mirror;

import java.util.Collection;

import org.openspaces.core.cluster.ClusterInfoAware;

import com.gigaspaces.datasource.BulkDataPersister;
import com.gigaspaces.datasource.ManagedDataSource;

import se.avanzabank.mongodb.util.LifecycleAware;
/**
 * Mixin interface. <p>
 *
 * @author Elias Lindholm (elilin)
 *
 */
public interface ManagedDataSourceAndBulkDataPersister extends ManagedDataSource<Object>, BulkDataPersister, ClusterInfoAware, SpaceObjectReloader, LifecycleAware {
	<T> Collection<T> loadObjects(Class<T> spaceType, T template);
}
