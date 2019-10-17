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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import io.fabric8.openshift.client.OpenShiftClient;
import org.jboss.tools.intellij.openshift.actions.OdoAction;
import org.jboss.tools.intellij.openshift.tree.LazyMutableTreeNode;
import org.jboss.tools.intellij.openshift.tree.application.*;
import org.jboss.tools.intellij.openshift.ui.url.CreateURLDialog;
import org.jboss.tools.intellij.openshift.utils.odo.Component;
import org.jboss.tools.intellij.openshift.utils.odo.ComponentState;
import org.jboss.tools.intellij.openshift.utils.odo.Odo;
import org.jboss.tools.intellij.openshift.utils.UIHelper;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CreateURLAction extends OdoAction {
  public CreateURLAction() {
    super(ComponentNode.class);
  }

  @Override
  public boolean isVisible(Object selected) {
    boolean visible = super.isVisible(selected);
    if (visible) {
      visible = ((Component)((ComponentNode)selected).getUserObject()).getState() == ComponentState.PUSHED;
    }
    return visible;
  }

  @Override
  public void actionPerformed(AnActionEvent anActionEvent, TreePath path, Object selected, Odo odo) {
    ComponentNode componentNode = (ComponentNode) selected;
    Component component = (Component) componentNode.getUserObject();
    LazyMutableTreeNode applicationNode = (LazyMutableTreeNode) ((TreeNode) selected).getParent();
    LazyMutableTreeNode projectNode = (LazyMutableTreeNode) applicationNode.getParent();
    CompletableFuture.runAsync(() -> {
      try {
        final OpenShiftClient client = ((ApplicationsRootNode)componentNode.getRoot()).getClient();
        if (createURL(odo, client, projectNode.toString(), applicationNode.toString(), component.getPath(), component.getName())) {
          componentNode.reload();
        }
      } catch (IOException e) {
        UIHelper.executeInUI(() -> Messages.showErrorDialog("Error: " + e.getLocalizedMessage(), "Create URL"));
      }
    });
  }

  public static List<Integer> loadServicePorts(Odo odo, OpenShiftClient client, String project, String application, String component) {
    return odo.getServicePorts(client, project, application, component);
  }

  public static boolean createURL(Odo odo, OpenShiftClient client, String project, String application, String context, String name) throws IOException {
    boolean done = false;
    List<Integer> ports = loadServicePorts(odo, client, project, application, name);
    if (!ports.isEmpty()) {
      CreateURLDialog dialog = UIHelper.executeInUI(() -> {
        CreateURLDialog dialog1 = new CreateURLDialog(null);
        dialog1.setPorts(ports);
        dialog1.show();
        return dialog1;
      });
      if (dialog.isOK()) {
        Integer port = dialog.getSelectedPort();
        String urlName = dialog.getName();
        if (port != null) {
          odo.createURL(project, application, context, name, urlName, port);
          done = true;
        }
      }
    } else {
      throw new IOException("Can't create url for component without ports");
    }
    return done;
  }
}
