/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.core.mapper;

import java.lang.reflect.Type;
import java.util.Optional;

import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.enums.EnumByName;
import org.jdbi.v3.core.enums.EnumByOrdinal;
import org.jdbi.v3.core.enums.EnumStrategy;
import org.jdbi.v3.core.enums.Enums;

import static org.jdbi.v3.core.generic.GenericTypes.getErasedType;

/**
 * Produces enum column mappers, which map enums from varchar columns using {@link Enum#valueOf(Class, String)}.
 *
 * @see Enums#setEnumStrategy(EnumStrategy)
 * @see EnumByName
 * @see EnumByOrdinal
 * @see EnumMapper#byName(Class)
 * @deprecated Use {@link Enums#setEnumStrategy(EnumStrategy) getConfig(Enums.class).setEnumStrategy(BY_NAME)} instead.
 */
@Deprecated(since = "3.7.0") // TODO jdbi4: delete
public class EnumByNameMapperFactory implements ColumnMapperFactory {
    @Override
    @SuppressWarnings("unchecked")
    public Optional<ColumnMapper<?>> build(Type type, ConfigRegistry config) {
        Class<?> clazz = getErasedType(type);

        return Enum.class.isAssignableFrom(clazz)
                ? Optional.of(EnumMapper.byName(clazz.asSubclass(Enum.class)))
                : Optional.empty();
    }
}
