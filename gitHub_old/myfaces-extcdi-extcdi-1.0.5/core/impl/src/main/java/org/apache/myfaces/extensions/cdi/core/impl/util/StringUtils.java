/*
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
package org.apache.myfaces.extensions.cdi.core.impl.util;

import javax.enterprise.inject.Typed;

/**
 * Helper for handling strings
 */
@Typed()
public class StringUtils
{
    private StringUtils()
    {
        // prevent instantiation
    }

    /**
     * Replaces all upper-case characters of the given key with an underscore and
     * the lower-case version of the character
     * @param baseKey current key
     * @return the transformed version of the given key
     */
    public static String replaceUpperCaseCharactersWithUnderscores(String baseKey)
    {
        StringBuilder dynamicKey = new StringBuilder(baseKey.length());

        Character current;
        for(int i = 0; i < baseKey.length(); i++)
        {
            current = baseKey.charAt(i);
            if(Character.isUpperCase(current))
            {
                dynamicKey.append("_");
                dynamicKey.append(Character.toLowerCase(current));
            }
            else
            {
                dynamicKey.append(current);
            }
        }
        return dynamicKey.toString();
    }
}
