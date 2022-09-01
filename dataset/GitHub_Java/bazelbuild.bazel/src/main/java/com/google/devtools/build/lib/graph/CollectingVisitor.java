// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.graph;

import java.util.ArrayList;
import java.util.List;

/**
 *  A graph visitor that collects the visited nodes in the order in which
 *  they were visited, and allows them to be accessed as a list.
 */
public class CollectingVisitor<T> extends AbstractGraphVisitor<T> {

  private final List<Node<T>> order = new ArrayList<Node<T>>();

  @Override
  public void visitNode(Node<T> node) {
    order.add(node);
  }

  /**
   *  Returns a reference to (not a copy of) the list of visited nodes in the
   *  order they were visited.
   */
  public List<Node<T>> getVisitedNodes() {
    return order;
  }
}
