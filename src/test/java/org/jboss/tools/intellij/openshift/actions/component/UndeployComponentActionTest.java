/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.openshift.actions.component;

import com.intellij.openapi.actionSystem.AnAction;
import org.jboss.tools.intellij.openshift.actions.ActionTest;

import static org.mockito.Mockito.mock;

public class UndeployComponentActionTest extends ActionTest {
  @Override
  public AnAction getAction() {
    return new UndeployComponentAction();
  }

  @Override
  protected void verifyPushedComponent(boolean visible) {
    assertTrue(visible);
  }
}
