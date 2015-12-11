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

import java.lang.reflect.Method;
import java.util.Objects;

import com.gigaspaces.annotation.pojo.SpaceId;

import se.avanzabank.mongodb.util.Require;

/**
 * Routing for space objects works differently for gigaspace objects that has an autogenerated key.
 * This class resolves the routing from suchs keys correctly
 */
public class GigaSpaceAutoGeneratedRoutingKeyExtractor implements RoutingKeyExtractor {

	private final Method method;

	public GigaSpaceAutoGeneratedRoutingKeyExtractor(Method m) {
		Require.that(isApplicable(m));
		this.method = Objects.requireNonNull(m);
	}

	@Override
	public Object getRoutingKey(Object spaceObject) {
		try {
			String key = String.class.cast(method.invoke(spaceObject));
			if (key == null) {
				return null;
			} else if (key.indexOf("^") == -1) {
				return key;
			} else {
				return key.substring(0, key.indexOf("^"));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean isApplicable(Method m) {
		if (!m.isAnnotationPresent(SpaceId.class)) {
			return false;
		}
		return m.getReturnType().equals(String.class) && m.getAnnotation(SpaceId.class).autoGenerate(); 
	}

}
