/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.openshift.utils;

import java.io.IOException;
import org.jboss.tools.intellij.openshift.utils.oc.Oc;

public class OdoCluster {

  public static final OdoCluster INSTANCE = new OdoCluster();

  public static final String CLUSTER_URL = System.getenv("CLUSTER_URL");

  public static final String CLUSTER_USER = System.getenv("CLUSTER_USER");

  public static final String CLUSTER_PASSWORD = System.getenv("CLUSTER_PASSWORD");

  public void login(Oc oc) throws IOException {
    if (CLUSTER_URL != null && !oc.getMasterUrl().toString().startsWith(CLUSTER_URL)) {
      oc.login(CLUSTER_URL, CLUSTER_USER, CLUSTER_PASSWORD.toCharArray(), null);
    }
  }

}
