/*
 * Copyright (c) 2010-2022 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
package org.eclipse.scout.rt.dataobject.testing.signature;

import java.util.Set;

import org.eclipse.scout.rt.dataobject.IDoEntity;
import org.eclipse.scout.rt.dataobject.enumeration.IEnum;
import org.eclipse.scout.rt.dataobject.id.IId;
import org.eclipse.scout.rt.platform.ApplicationScoped;

/**
 * The data object signature test might need additional configuration, e.g. supported types for attribute nodes.
 */
@ApplicationScoped
public interface IDataObjectSignatureTestCustomizer {

  /**
   * Supported types for data object attributes nodes.
   * <p>
   * Subclasses of {@link IDoEntity}, {@link IEnum} and {@link IId} are handled explicitly (considered supported) and
   * must not be part of the supported types.
   */
  Set<Class<?>> supportedTypes();

  /**
   * Allows contributing custom validation on attribute type.
   * <p>
   * If a subclass of {@link IDoEntity}, {@link IEnum} or {@link IId} should not be accepted, override this method and
   * return a non-null error message.
   */
  default String validateAttributeType(String attributeName, Class<?> attributeTypeClazz, Class<? extends IDoEntity> containingEntityClass) {
    return null;
  }
}
