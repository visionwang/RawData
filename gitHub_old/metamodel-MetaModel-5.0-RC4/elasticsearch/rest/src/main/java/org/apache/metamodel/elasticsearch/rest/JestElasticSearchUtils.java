/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.metamodel.elasticsearch.rest;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.metamodel.data.DataSetHeader;
import org.apache.metamodel.data.DefaultRow;
import org.apache.metamodel.data.Row;
import org.apache.metamodel.elasticsearch.common.ElasticSearchDateConverter;
import org.apache.metamodel.query.SelectItem;
import org.apache.metamodel.schema.Column;
import org.apache.metamodel.schema.ColumnType;
import org.apache.metamodel.util.NumberComparator;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Shared/common util functions for the ElasticSearch MetaModel module.
 */
final class JestElasticSearchUtils {
    public static Row createRow(JsonObject source, String documentId, DataSetHeader header) {
        final Object[] values = new Object[header.size()];
        for (int i = 0; i < values.length; i++) {
            final SelectItem selectItem = header.getSelectItem(i);
            final Column column = selectItem.getColumn();

            assert column != null;
            assert !selectItem.hasFunction();

            if (column.isPrimaryKey()) {
                values[i] = documentId;
            } else {
                values[i] = getDataFromColumnType(source.get(column.getName()), column.getType());
            }
        }

        return new DefaultRow(header, values);
    }

    private static Object getDataFromColumnType(JsonElement field, ColumnType type) {
        if (field == null || field.isJsonNull()) {
            return null;
        }

        if (field.isJsonObject()) {
            return new Gson().fromJson(field, Map.class);
        }
        if (field.isJsonArray()) {
            return new Gson().fromJson(field, List.class);
        }

        if (type.isNumber()) {
            // Pretty terrible workaround to avoid LazilyParsedNumber
            // (which is happily output, but not recognized by Jest/GSON).
            return NumberComparator.toNumber(field.getAsString());
        } else if (type.isTimeBased()) {
            final Date valueToDate = ElasticSearchDateConverter.tryToConvert(field.getAsString());
            if (valueToDate == null) {
                return field.getAsString();
            } else {
                return valueToDate;
            }
        } else if (type.isBoolean()) {
            return field.getAsBoolean();
        } else {
            return field.getAsString();
        }
    }
}
