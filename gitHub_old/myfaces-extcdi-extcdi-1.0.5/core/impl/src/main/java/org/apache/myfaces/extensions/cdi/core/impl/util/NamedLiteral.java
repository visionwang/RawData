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

import javax.inject.Named;
import javax.enterprise.util.AnnotationLiteral;

/**
 * Literal for the {@link javax.inject.Named} annotation.
 */
public class NamedLiteral extends AnnotationLiteral<Named> implements Named
{
    private static final long serialVersionUID = -275279485237713614L;

    private final String name;

    /**
     * Constructor for creating an instance with the given name
     * @param name name which should be used
     */
    public NamedLiteral(String name)
    {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public String value()
    {
        return name;
    }
}
