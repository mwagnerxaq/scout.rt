/*******************************************************************************
 * Copyright (c) 2011 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.scout.rt.ui.rap.form.fields;

import org.eclipse.scout.rt.client.ui.form.fields.IFormField;
import org.eclipse.scout.rt.ui.rap.basic.IRwtScoutComposite;
import org.eclipse.scout.rt.ui.rap.ext.ILabelComposite;

/**
 * <h3>IRwtScoutFormField</h3> ...
 * 
 * @since 3.7.0 June 2011
 */
public interface IRwtScoutFormField<T extends IFormField> extends IRwtScoutComposite<T> {
  String CLIENT_PROPERTY_SCOUT_OBJECT = "org.eclipse.scout.rt.object";

  ILabelComposite getUiLabel();

}
