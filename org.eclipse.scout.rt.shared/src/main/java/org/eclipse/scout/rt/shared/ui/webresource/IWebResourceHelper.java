/*
 * Copyright (c) 2010-2019 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
package org.eclipse.scout.rt.shared.ui.webresource;

import org.eclipse.scout.rt.platform.Bean;

import java.net.URL;
import java.util.Optional;

@Bean
public interface IWebResourceHelper {

  Optional<URL> getScriptResource(String path, boolean minified);

  Optional<URL> getWebResource(String path);
}
