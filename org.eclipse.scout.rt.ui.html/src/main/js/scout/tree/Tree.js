// SCOUT GUI
// (c) Copyright 2013-2014, BSI Business Systems Integration AG

scout.Tree = function() {
  scout.Tree.parent.call(this);
  this.$data;
  this.nodes = []; // top-level nodes
  this.nodesMap = {}; // all nodes by id
  this._breadcrumbEnabled = false;
  this.events = new scout.EventSupport();
  this._addAdapterProperties(['menus', 'keyStrokes']);
  this._additionalContainerClasses = ''; // may be used by subclasses to set additional CSS classes
  this._treeItemPaddingLeft = 23;
  this._treeItemCheckBoxPaddingLeft = 29;
  this._treeItemPaddingLevel = 15;
  this.menus = [];
  this.menuBar;
  this.checkedNodes = [];
  this._filters = [];
  this._animationNodeLimit = 25;
  this._keyStrokeSupport = new scout.KeyStrokeSupport(this);
  this._doubleClickSupport = new scout.DoubleClickSupport();
  this._$animationWrapper; // used by _renderExpansion()

  // Flag (0 = false, > 0 = true) to indicate whether child nodes should be added to the tree lazily if
  // they request it.  Default is false, which has the consequences that most UI actions show all nodes.
  // However, certain actions may set this flag (temporarily) to true.
  this._lazyAddChildNodesToTree = 0;
};
scout.inherits(scout.Tree, scout.ModelAdapter);

scout.Tree.prototype._init = function(model, session) {
  scout.Tree.parent.prototype._init.call(this, model, session);
  this._visitNodes(this.nodes, this._initTreeNode.bind(this));
  this.selectedNodes = this._nodesByIds(this.selectedNodes);
  var menuSorter = new scout.MenuItemsOrder(this.session, 'Tree');
  this.menuBar = new scout.MenuBar(this.session, menuSorter);
  this.menuBar.bottom();
  this.addChild(this.menuBar);
};

/**
 * @override ModelAdapter.js
 */
scout.Tree.prototype._initKeyStrokeContext = function(keyStrokeContext) {
  scout.Tree.parent.prototype._initKeyStrokeContext.call(this, keyStrokeContext);

  this._initTreeKeyStrokeContext(keyStrokeContext);
};

scout.Tree.prototype._initTreeKeyStrokeContext = function(keyStrokeContext) {
  var modifierBitMask = scout.keyStrokeModifier.NONE;

  keyStrokeContext.registerKeyStroke([
      new scout.TreeSpaceKeyStroke(this),
      new scout.TreeNavigationUpKeyStroke(this, modifierBitMask),
      new scout.TreeNavigationDownKeyStroke(this, modifierBitMask),
      new scout.TreeCollapseAllKeyStroke(this, modifierBitMask),
      new scout.TreeCollapseOrDrillUpKeyStroke(this, modifierBitMask),
      new scout.TreeExpandOrDrillDownKeyStroke(this, modifierBitMask)
    ]
    .concat(this.menus));

  // Prevent default action and do not propagate ↓ or ↑ keys if ctrl- or alt-modifier is not pressed.
  // Otherwise, an '↑-event' on the first node, or an '↓-event' on the last row will bubble up (because not consumed by tree navigation keystrokes) and cause a superior tree to move its selection;
  // Use case: - outline tree with a detail form that contains a tree;
  //           - preventDefault because of smartfield, so that the cursor is not moved on first or last row;
  keyStrokeContext.registerStopPropagationInterceptor(function(event) {
    if (!event.ctrlKey && !event.altKey && scout.helpers.isOneOf(event.which, scout.keys.UP, scout.keys.DOWN)) {
      event.stopPropagation();
      event.preventDefault();
    }
  });
};

scout.Tree.prototype._syncMenus = function(newMenus, oldMenus) {
  this._keyStrokeSupport.syncMenus(newMenus, oldMenus);
};

scout.Tree.prototype._initTreeNode = function(node, parentNode) {
  this.nodesMap[node.id] = node;
  if (parentNode) {
    node.parentNode = parentNode;
  }
  if (node.checked) {
    this.checkedNodes.push(node);
  }
  scout.defaultValues.applyTo(node, 'TreeNode');
  if (node.childNodes === undefined) {
    node.childNodes = [];
  }
  this._updateMarkChildrenChecked(node, true, node.checked);
};

scout.Tree.prototype.destroy = function() {
  scout.Tree.parent.prototype.destroy.call(this);
  this._visitNodes(this.nodes, this._destroyTreeNode.bind(this));
};

scout.Tree.prototype._destroyTreeNode = function(node, parentNode) {
  delete this.nodesMap[node.id];
  scout.arrays.remove(this.selectedNodes, node); // ensure deleted node is not in selection list anymore (in case the model does not update the selection)

  if (this._onNodeDeleted) { // Necessary for subclasses
    this._onNodeDeleted(node);
  }
};

scout.Tree.prototype._visitNodes = function(nodes, func, parentNode) {
  var i, node;
  if (!nodes) {
    return;
  }

  for (i = 0; i < nodes.length; i++) {
    node = nodes[i];
    func(node, parentNode);
    if (node.childNodes.length > 0) {
      this._visitNodes(node.childNodes, func, node);
    }
  }
};

scout.Tree.prototype._render = function($parent) {
  this.$container = $parent.appendDiv('tree');
  if (this._additionalContainerClasses) {
    this.$container.addClass(this._additionalContainerClasses);
  }

  var layout = new scout.TreeLayout(this);
  this.htmlComp = new scout.HtmlComponent(this.$container, this.session);
  this.htmlComp.setLayout(layout);
  this.htmlComp.pixelBasedSizing = false;

  this.$data = this.$container.appendDiv('tree-data')
    .on('contextmenu', this._onContextMenu.bind(this))
    .on('mousedown', '.tree-node', this._onNodeMouseDown.bind(this))
    .on('mouseup', '.tree-node', this._onNodeMouseUp.bind(this))
    .on('dblclick', '.tree-node', this._onNodeDoubleClick.bind(this))
    .on('mousedown', '.tree-node-control', this._onNodeControlMouseDown.bind(this))
    .on('mouseup', '.tree-node-control', this._onNodeControlMouseUp.bind(this))
    .on('dblclick', '.tree-node-control', this._onNodeControlDoubleClick.bind(this));

  scout.scrollbars.install(this.$data, this.session, {
    axis: 'y'
  });
  this.menuBar.render(this.$container);

  // add drag and drop support
  this.dragAndDropHandler = scout.dragAndDrop.handler(this,
    scout.dragAndDrop.SCOUT_TYPES.FILE_TRANSFER,
    function() {
      return this.dropType;
    }.bind(this),
    function() {
      return this.dropMaximumSize;
    }.bind(this),
    function(event) {
      var target = event.currentTarget;
      return {
        'nodeId': (target.classList.contains('tree-node') ? $(target).data('node').id : '')
      };
    }.bind(this));
  this.dragAndDropHandler.install(this.$data);

  // When nodes are rendered initially, lazy nodes should not be added to the tree.
  this.lazyAddChildNodesToTree(true);
  try {
    this._addNodes(this.nodes);
  } finally {
    this.lazyAddChildNodesToTree(false);
  }

  if (this.selectedNodes.length > 0) {
    this._renderSelection();
  }
  this._updateItemPath();
};

scout.Tree.prototype._remove = function() {
  // Detach nodes from jQuery objects (because those will be removed)
  this.nodes.forEach(function(node) {
    delete node.$node;
  });
  scout.scrollbars.uninstall(this.$data, this.session);
  scout.Tree.parent.prototype._remove.call(this);
};

scout.Tree.prototype._renderProperties = function() {
  scout.Tree.parent.prototype._renderProperties.call(this);
  this._renderEnabled();
  this._renderMenus();
};

scout.Tree.prototype._renderMenus = function() {
  var menuItems = this._filterMenus(['Tree.EmptySpace', 'Tree.SingleSelection', 'Tree.MultiSelection'], false, true);
  this.menuBar.updateItems(menuItems);
};

scout.Tree.prototype._filterMenus = function(allowedTypes, onlyVisible, enableDisableKeyStroke) {
  allowedTypes = allowedTypes || [];
  if (allowedTypes.indexOf('Tree.SingleSelection') > -1 && this.selectedNodes.length !== 1) {
    scout.arrays.remove(allowedTypes, 'Tree.SingleSelection');
  }
  if (allowedTypes.indexOf('Tree.MultiSelection') > -1 && this.selectedNodes.length <= 1) {
    scout.arrays.remove(allowedTypes, 'Tree.MultiSelection');
  }
  return scout.menus.filter(this.menus, allowedTypes, onlyVisible, enableDisableKeyStroke);
};

scout.Tree.prototype._renderEnabled = function() {
  var enabled = this.enabled;
  this.$data.setEnabled(enabled);
  this.$container.setTabbable(enabled);

  if (this.rendered) {
    // Enable/disable all checkboxes
    this.$nodes().each(function() {
      var $node = $(this),
        node = $node.data('node');

      $node.children('.tree-node-checkbox')
        .children('.check-box')
        .toggleClass('disabled', !(enabled && node.enabled));
    });
  }
};

scout.Tree.prototype._renderTitle = function() {
  // NOP
};

scout.Tree.prototype._renderAutoCheckChildren = function() {
  // NOP
};

scout.Tree.prototype._renderCheckable = function() {
  // NOP
};

scout.Tree.prototype._renderMultiCheck = function() {
  // NOP
};

scout.Tree.prototype.onResize = function() {
  if (this.rendered) {
    this.htmlComp.revalidateLayoutTree();
  }
};

scout.Tree.prototype._updateMarkChildrenChecked = function(node, init, checked, checkChildrenChecked) {
  if (!this.checkable) {
    return;
  }

  if (checkChildrenChecked) {
    var childrenFound = false;
    for (var j = 0; j < node.childNodes.length > 0; j++) {
      var childNode = node.childNodes[j];
      if (childNode.checked || childNode.childrenChecked) {
        node.childrenChecked = true;
        checked = true;
        childrenFound = true;
        if (this.rendered && node.$node) {
          node.$node
            .children('.tree-node-checkbox')
            .children('.check-box')
            .toggleClass('children-checked', true);
        }
        break;
      }
    }
    if (!childrenFound) {
      node.childrenChecked = false;
      if (this.rendered && node.$node) {
        node.$node.children('.tree-node-checkbox')
          .children('.check-box')
          .toggleClass('children-checked', false);
      }
    }
  }

  if (!node.parentNode || node.parentNode.checked) {
    return;
  }

  var stateChanged = false;
  if (!checked && !init) {
    //node was unchecked check siblings
    var hasCheckedSiblings = false;
    for (var i = 0; i < node.parentNode.childNodes.length > 0; i++) {
      var siblingNode = node.parentNode.childNodes[i];
      if (siblingNode.checked || siblingNode.childrenChecked) {
        hasCheckedSiblings = true;
        break;
      }
    }
    if (hasCheckedSiblings !== node.parentNode.childrenChecked) {
      //parentNode.checked should be false
      node.parentNode.childrenChecked = hasCheckedSiblings;
      stateChanged = true;
    }
  }
  if ((checked && !node.parentNode.childrenChecked)) {
    node.parentNode.childrenChecked = true;
    stateChanged = true;
  }
  if (stateChanged) {
    this._updateMarkChildrenChecked(node.parentNode, init, checked);
    if (this.rendered && node.parentNode.$node) {
      if (checked) {
        node.parentNode.$node.children('.tree-node-checkbox')
          .children('.check-box')
          .toggleClass('children-checked', true);
      } else {
        node.parentNode.$node.children('.tree-node-checkbox')
          .children('.check-box')
          .toggleClass('children-checked', false);
      }
    }
  }
};

scout.Tree.prototype.setBreadcrumbEnabled = function(enabled) {
  if (this._breadcrumbEnabled !== enabled) {
    // update scrollbar if mode has changed (from tree to bc or vice versa)
    this.revalidateLayoutTree();
  }
  this._breadcrumbEnabled = enabled;

  if (!enabled) {
    return;
  }

  if (this.selectedNodes.length > 0) {
    var nodeId = this.selectedNodes[0].id,
      expanded = this.selectedNodes[0].expanded,
      node = this.nodesMap[nodeId];

    if (!expanded) {
      this.remoteHandler(this.id, 'nodeAction', {
        nodeId: nodeId
      });
      this.setNodeExpanded(node, true);
    }
  }
};

scout.Tree.prototype.collapseAll = function() {
  var that = this;

  // Collapse root nodes
  this.$data.find('[data-level="0"]').each(function() {
    var $node = $(this);
    that.setNodeExpanded($node.data('node'), false);
  });

  // Collapse all expanded child nodes (only model)
  this._visitNodes(this.nodes, function(node) {
    this.setNodeExpanded(node, false);
  }.bind(this));

};

scout.Tree.prototype.setNodeExpanded = function(node, expanded, opts) {
  opts = opts || {};

  // Optionally collapse all children (recursively)
  if (opts.collapseChildNodes) {
    // Suppress render expansion
    var childOpts = scout.objects.valueCopy(opts);
    childOpts.renderExpansion = false;

    node.childNodes.forEach(function(childNode) {
      if (childNode.expanded) {
        this.setNodeExpanded(childNode, false, childOpts);
      }
    }.bind(this));
  }

  // Set expansion state
  if (node.expanded !== expanded) {
    node.expanded = expanded;

    this.remoteHandler(this.id, 'nodeExpanded', {
      nodeId: node.id,
      expanded: expanded
    });
  }

  // Render expansion
  if (this.rendered && scout.helpers.nvl(opts.renderExpansion, true)) {
    this._renderExpansion(node, null, opts.animateExpansion);
  }
};

scout.Tree.prototype._renderExpansion = function(node, $predecessor, animate) {
  animate = scout.helpers.nvl(animate, true);

  var $node = node.$node,
    expanded = node.expanded;

  // Only render if node is rendered to make it possible to expand/collapse currently invisible nodes (used by collapseAll).
  if (!$node || $node.length === 0) {
    return;
  }

  if (expanded === $node.hasClass('expanded')) {
    return;
  }

  // Only expand / collapse if there are child nodes
  if (node.childNodes.length === 0) {
    return true;
  }

  // If there is already an animation is already going on for this node, stop it immediately
  if (this._$animationWrapper) {
    // Note: Do _not_ use finish() here! Although documentation states that it is "similar" to stop(true, true),
    // this does not seem to be the case. Implementations differ slightly in details. The effect is, that when
    // calling stop() the animation stops and the 'complete' callback is executed immediately. However, when calling
    // finish(), the callback is _not_ executed! (This may or may not be a bug in jQuery, I cannot tell...)
    this._$animationWrapper.stop(false, true);
  }

  if (expanded) {
    this._addNodes(node.childNodes, $node, $predecessor);
    this._updateItemPath();

    if (this._breadcrumbEnabled) {
      $node.addClass('expanded');
      return;
    }

    // animated opening
    if (!$node.hasClass('leaf') && !$node.hasClass('expanded')) { // can expand
      if (this.rendered) { // only when rendered (otherwise not necessary, or may even lead to timing issues)
        var $newNodes = scout.Tree.collectSubtree($node, false);
        if (animate && $newNodes.length) {
          this._$animationWrapper = $newNodes.wrapAll('<div class="animation-wrapper">').parent();
          var h = this._$animationWrapper.outerHeight();
          this._$animationWrapper
            .css('height', 0)
            .animateAVCSD('height', h, onAnimationComplete.bind(this, true), this.revalidateLayoutTree.bind(this), 200);
        }
      }
    }
    $node.addClass('expanded');
  } else {
    $node.removeClass('expanded');
    $node.removeClass('show-all');

    // animated closing
    if (this.rendered) { // only when rendered (otherwise not necessary, or may even lead to timing issues)
      var $existingNodes = scout.Tree.collectSubtree($node, false);
      if ($existingNodes.length) {
        $existingNodes.each(function() {
          // unlink '$nodes' from 'nodes' before deleting them
          var node = $(this).data('node');
          if (node) { // FIXME BSH Tree | This if should not be necessary! 'node' should not be undefined, but is sometimes... Check why!
            delete node.$node;
          }
        });
        if (animate) {
          this._$animationWrapper = $existingNodes.wrapAll('<div class="animation-wrapper">)').parent();
          this._$animationWrapper
            .animateAVCSD('height', 0, onAnimationComplete.bind(this, false), this.revalidateLayoutTree.bind(this), 200);
        } else {
          $existingNodes.remove();
          this.invalidateLayoutTree();
        }
      }
    }
  }

  // ----- Helper functions -----

  function onAnimationComplete(expanding) {
    if (expanding) {
      this._$animationWrapper.replaceWith(this._$animationWrapper.contents());
    } else {
      this._$animationWrapper.remove();
    }
    this._$animationWrapper = null;
  }
};

scout.Tree.prototype.scrollTo = function(node) {
  scout.scrollbars.scrollTo(this.$data, node.$node);
};

scout.Tree.prototype.revealSelection = function() {
  if (this.selectedNodes.length > 0) {
    this.scrollTo(this.selectedNodes[0]);
  }
};

scout.Tree.prototype.clearSelection = function() {
  this.selectNodes([]);
};

scout.Tree.prototype.selectNodes = function(nodes, notifyServer, debounceSend) {
  nodes = scout.arrays.ensure(nodes);
  notifyServer = scout.helpers.nvl(notifyServer, true);
  if (scout.arrays.equalsIgnoreOrder(nodes, this.selectedNodes)) {
    return;
  }

  if (this.rendered) {
    this._removeSelection();
  }

  this.selectedNodes = nodes;
  if (notifyServer) {
    var event = new scout.Event(this.id, 'nodesSelected', {
      nodeIds: this._nodesToIds(nodes)
    });

    // Only send the latest selection changed event for a field
    event.coalesce = function(previous) {
      return this.id === previous.id && this.type === previous.type;
    };

    // send delayed to avoid a lot of requests while selecting
    this.session.sendEvent(event, debounceSend ? 250 : 0);
  }

  if (this.rendered) {
    this._renderSelection();
    this._renderMenus();
  }
};

scout.Tree.prototype.deselectNodes = function(nodes) {
  nodes = scout.arrays.ensure(nodes);
  var selectedNodes = this.selectedNodes.slice(); // copy
  if (scout.arrays.removeAll(selectedNodes, nodes)) {
    this.selectNodes(selectedNodes);
  }
};

scout.Tree.prototype._renderSelection = function() {
  var i, node, $node,
    $nodes = [];

  this.selectedNodes.forEach(function(node) {
    $node = node.$node;

    // If $node is currently not displayed (due to a collapsed parent node), expand the parents
    if (!$node) {
      this._expandAllParentNodes(node);
      $node = node.$node;
      if (!$node || $node.length === 0) {
        throw new Error('Still no node found. node=' + node);
      }
    }

    $nodes.push($node);
  }, this);

  // render selection
  for (i = 0; i < $nodes.length; i++) {
    $node = $nodes[i];
    $node.select(true);
    // If node was previously hidden, show it!
    $node.removeClass('hidden');

    // in case of breadcrumb, expand
    if (this._breadcrumbEnabled) {
      this.setNodeExpanded($nodes[i].data('node'), true, {
        renderExpansion: true
      }); // force render expansion
    }
  }

  this._updateItemPath();
  if (this.scrollToSelection) {
    // Execute delayed because tree may be not layouted yet
    setTimeout(this.revealSelection.bind(this));
  }
};

scout.Tree.prototype._removeSelection = function() {
  this.selectedNodes.forEach(function(node) {
    var $node = node.$node;
    if ($node) { // TODO BSH Check if $node can be undefined
      $node.select(false);
    }
  }, this);
};

scout.Tree.prototype._computeTreeItemPaddingLeft = function(level, selected) {
  if (this.checkable) {
    return level * this._treeItemPaddingLevel + this._treeItemPaddingLeft + this._treeItemCheckBoxPaddingLeft;
  }
  return level * this._treeItemPaddingLevel + this._treeItemPaddingLeft;
};

scout.Tree.prototype._expandAllParentNodes = function(node) {
  var i, $parentNode, currNode = node,
    parentNodes = [];

  currNode = node;
  while (currNode.parentNode) {
    parentNodes.push(currNode.parentNode);
    currNode = currNode.parentNode;
  }

  for (i = parentNodes.length - 1; i >= 0; i--) {
    if (!parentNodes[i].expanded) {
      $parentNode = parentNodes[i].$node;
      if (!$parentNode) {
        throw new Error('Illegal state, $parentNode should be displayed. Rendered: ' + this.rendered + ', parentNode: ' + parentNodes[i]);
      }
      this.setNodeExpanded(parentNodes[i], true, {
        renderExpansion: true
      }); // force render expansion
    }
  }
};

scout.Tree.prototype._updateChildNodeIndex = function(nodes, startIndex) {
  for (var i = scout.helpers.nvl(startIndex, 0); i < nodes.length; i++) {
    nodes[i].childNodeIndex = i;
  }
};

scout.Tree.prototype._onNodesInserted = function(nodes, parentNodeId) {
  var parentNode, $parentNode;
  //Append continous node blocks

  if (parentNodeId >= 0) {
    parentNode = this.nodesMap[parentNodeId];
    if (!parentNode) {
      throw new Error('Parent node could not be found. Id: ' + parentNodeId);
    }
  }
  nodes.sort(function(a, b) {
    return a.childNodeIndex - b.childNodeIndex;
  });
  this._visitNodes(nodes, this._initTreeNode.bind(this), parentNode);

  var $predecessor;
  // Update parent with new child nodes
  if (parentNode) {
    if (parentNode.childNodes && parentNode.childNodes.length > 0) {
      nodes.forEach(function(entry) {
        scout.arrays.insert(parentNode.childNodes, entry, entry.childNodeIndex);
      }.bind(this));
      this._updateChildNodeIndex(parentNode.childNodes, nodes[0].childNodeIndex);
    } else {
      scout.arrays.pushAll(parentNode.childNodes, nodes);
    }

    if (this.rendered && parentNode.$node) {
      $parentNode = parentNode.$node;
      if (nodes[0].childNodeIndex === 0) {
        $predecessor = $parentNode;
      } else {
        $predecessor = calcPredecessor(parentNode.childNodes[nodes[0].childNodeIndex - 1]);
      }
      if (parentNode.expanded) {
        // If parent is already expanded just add the nodes at the end.
        // Otherwise render the expansion
        if ($parentNode.hasClass('expanded')) {
          this._addNodes(nodes, $parentNode, $predecessor);
        } else {
          this._renderExpansion(parentNode, $predecessor);
        }
      }
    }
  } else {
    if (this.nodes && this.nodes.length > 0) {
      nodes.forEach(function(entry) {
        scout.arrays.insert(this.nodes, entry, entry.childNodeIndex);
      }.bind(this));
      this._updateChildNodeIndex(this.nodes, nodes[0].childNodeIndex);

      if (nodes[0].childNodeIndex !== 0) {
        $predecessor = calcPredecessor(this.nodes[nodes[0].childNodeIndex - 1]);
      }
    } else {
      scout.arrays.pushAll(this.nodes, nodes);
    }

    if (this.rendered) {
      this._addNodes(nodes, undefined, $predecessor);
    }
  }

  // ----- Helper functions -----

  function calcPredecessor(nodeBefore) {
    if (!nodeBefore) {
      return undefined;
    }
    if (nodeBefore && (!nodeBefore.childNodes || nodeBefore.childNodes.length === 0) && nodeBefore.$node) {
      return nodeBefore.$node;
    }
    var $node = calcPredecessor(nodeBefore.childNodes[nodeBefore.childNodes.length - 1]);
    if ($node) {
      return $node;
    } else if (nodeBefore.$node) {
      return nodeBefore.$node;
    }
    return undefined;
  }
};

scout.Tree.prototype._onNodesUpdated = function(nodes, parentNodeId) {
  // Update model
  var anyPropertiesChanged = false;
  for (var i = 0; i < nodes.length; i++) {
    var updatedNode = nodes[i];
    var oldNode = this.nodesMap[updatedNode.id];

    scout.defaultValues.applyTo(updatedNode, 'TreeNode');
    var propertiesChanged = this._applyUpdatedNodeProperties(oldNode, updatedNode);
    anyPropertiesChanged = anyPropertiesChanged || propertiesChanged;

    if (this.rendered && propertiesChanged) {
      this._decorateNode(oldNode);
    }
  }

  if (this.rendered && anyPropertiesChanged) {
    this._updateItemPath();
  }
};

scout.Tree.prototype._onNodesDeleted = function(nodeIds, parentNodeId) {
  var parentNode, i, nodeId, node, deletedNodes = [];

  if (parentNodeId >= 0) {
    parentNode = this.nodesMap[parentNodeId];
    if (!parentNode) {
      throw new Error('Parent node could not be found. Id: ' + parentNodeId);
    }
  }

  for (i = 0; i < nodeIds.length; i++) {
    nodeId = nodeIds[i];
    node = this.nodesMap[nodeId];
    if (parentNode) {
      if (node.parentNode !== parentNode) {
        throw new Error('Unexpected parent. Node.parent: ' + node.parentNode + ', parentNode: ' + parentNode);
      }
      scout.arrays.remove(parentNode.childNodes, node);
    } else {
      scout.arrays.remove(this.nodes, node);
    }
    this._destroyTreeNode(node, node.parentNode);
    deletedNodes.push(node);
    this._updateMarkChildrenChecked(node, false, false);

    //remove children from node map
    this._visitNodes(node.childNodes, this._destroyTreeNode.bind(this));
  }

  //remove node from html document
  if (this.rendered) {
    this._removeNodes(deletedNodes, parentNodeId);
  }
};

scout.Tree.prototype._onAllChildNodesDeleted = function(parentNodeId) {
  var parentNode, nodes;

  if (parentNodeId >= 0) {
    parentNode = this.nodesMap[parentNodeId];
    if (!parentNode) {
      throw new Error('Parent node could not be found. Id: ' + parentNodeId);
    }
  }
  if (parentNode) {
    nodes = parentNode.childNodes;
    parentNode.childNodes = [];
  } else {
    nodes = this.nodes;
    this.nodes = [];
  }
  this._visitNodes(nodes, updateNodeMap.bind(this));

  // remove node from html document
  if (this.rendered) {
    this._removeNodes(nodes, parentNodeId);
  }

  // --- Helper functions ---

  // Update model and nodemap
  function updateNodeMap(node, parentNode) {
    this._destroyTreeNode(node, parentNode);
    this._updateMarkChildrenChecked(node, false, false);
  }
};

scout.Tree.prototype._onNodesSelected = function(nodeIds) {
  var nodes = this._nodesByIds(nodeIds);
  this.selectNodes(nodes, false);
};

scout.Tree.prototype._onNodeExpanded = function(nodeId, expanded, recursive) {
  var node = this.nodesMap[nodeId];
  expandNodeInternal.call(this, node);
  if (recursive) {
    this._visitNodes(node.childNodes, function(childNode) {
      expandNodeInternal.call(this, childNode);
    }.bind(this));
  }

  // --- Helper functions ---

  function expandNodeInternal(node) {
    node.expanded = expanded;
    if (this.rendered) {
      // When the model chooses to expand a node, respect the child node's "lazyAddToTree" property.
      // This has the effect that a double click on a table row does not expand all child nodes of
      // the table page's node.
      this.lazyAddChildNodesToTree(true);
      try {
        this._renderExpansion(node);
      } finally {
        this.lazyAddChildNodesToTree(false);
      }
    }
  }
};

scout.Tree.prototype._onNodeChanged = function(nodeId, cell) {
  var node = this.nodesMap[nodeId];

  scout.defaultValues.applyTo(cell, 'TreeNode');
  node.text = cell.text;
  node.cssClass = cell.cssClass;
  node.iconId = cell.iconId;
  node.tooltipText = cell.tooltipText;
  node.foregroundColor = cell.foregroundColor;
  node.backgroundColor = cell.backgroundColor;
  node.font = cell.font;

  if (this.rendered) {
    this._decorateNode(node);
  }
};

scout.Tree.prototype._onNodesChecked = function(nodes) {
  for (var i = 0; i < nodes.length; i++) {
    this.checkNode(this.nodesMap[nodes[i].id], nodes[i].checked, true);
  }
};

scout.Tree.prototype._onChildNodeOrderChanged = function(parentNodeId, childNodeIds) {
  var i,
    parentNode = this.nodesMap[parentNodeId],
    $lastChildNode = parentNode.childNodes[parentNode.childNodes.length - 1].$node;

  // Sort model nodes
  var newPositionsMap = {};
  for (i = 0; i < childNodeIds.length; i++) {
    newPositionsMap[childNodeIds[i]] = i;
  }
  parentNode.childNodes.sort(compare.bind(this));

  // Render sorted nodes
  if (this.rendered && $lastChildNode) {
    // Find the last affected node DIV
    $lastChildNode = scout.Tree.collectSubtree($lastChildNode).last();

    // Insert a marker DIV
    var $marker = $lastChildNode.afterDiv();
    for (i = 0; i < parentNode.childNodes.length; i++) {
      var node = parentNode.childNodes[i];
      var $node = node.$node;
      if ($node) {
        // Move the element in DOM tree. Note: Inserting the element at the new position is sufficient
        // in jQuery. There is no need to remove() it at the old position. Also, removing would break
        // the application, because remove() detaches all listeners!
        scout.Tree.collectSubtree($node).insertBefore($marker);
      }
    }
    $marker.remove();
  }

  function compare(node1, node2) {
    var pos1 = newPositionsMap[node1.id];
    var pos2 = newPositionsMap[node2.id];
    if (pos1 < pos2) {
      return -1;
    }
    if (pos1 > pos2) {
      return 1;
    }
    return 0;
  }
};

/**
 * @param parentNodeId optional. If provided, this node's state will be updated (e.g. it will be collapsed)
 * @param $parentNode optional. If not provided, parentNodeId will be used to find $parentNode.
 */
scout.Tree.prototype._removeNodes = function(nodes, parentNodeId, $parentNode) {
  if (nodes.length === 0) {
    return;
  }

  for (var i = 0; i < nodes.length; i++) {
    var node = nodes[i];
    if (node.childNodes.length > 0) {
      this._removeNodes(node.childNodes, node.id, node.$node);
    }
    if (node.$node) {
      node.$node.remove();
      delete node.$node;
    }
  }

  //If every child node was deleted mark node as collapsed (independent of the model state)
  //--> makes it consistent with addNodes and expand (expansion is not allowed if there are no child nodes)
  var parentNode;
  if (!$parentNode && parentNodeId >= 0) {
    parentNode = this.nodesMap[parentNodeId];
    $parentNode = (parentNode ? parentNode.$node : undefined);
  }
  if ($parentNode) {
    if (!parentNode) {
      parentNode = $parentNode.data('node');
    }
    var childNodesOfParent = parentNode.childNodes;
    if (!childNodesOfParent || childNodesOfParent.length === 0) {
      $parentNode.removeClass('expanded');
      $parentNode.removeClass('show-all');
    }
  }

  this.invalidateLayoutTree();
};

scout.Tree.prototype._addNodes = function(nodes, $parent, $predecessor) {
  if (!nodes || nodes.length === 0) {
    return;
  }

  $predecessor = $predecessor || $parent;
  var parentNode = ($parent ? $parent.data('node') : null);
  var hasHiddenNodes = false;

  for (var i = 0; i < nodes.length; i++) {
    var node = nodes[i];

    var $node = this._$buildNode(node, $parent);

    // If node wants to be lazy added to the tree, and the tree has the flag _lazyAddChildNodesToTree
    // set (may change dynamically, depending on the current state), hide the DOM element, except
    // the node is expanded, in which case we never hide it. (The last check provides a cheap
    // way to retain most of the state when the page is reloaded).
    if (parentNode && this._lazyAddChildNodesToTree && node.lazyAddToTree && !node.expanded) {
      $node.addClass('hidden');
      hasHiddenNodes = true;
    }
    // append first node and successors
    if ($predecessor) {
      $node.insertAfter($predecessor);
    } else {
      $node.prependTo(this.$data);
    }

    // if model demands children, create them
    if (node.expanded && node.childNodes.length > 0) {
      $predecessor = this._addNodes(node.childNodes, $node);
    } else {
      $predecessor = $node;
    }
  }

  // Set the 'show-all' state on the parent node when not all child nodes are visible.
  if (parentNode) {
    parentNode.$node.toggleClass('show-all', hasHiddenNodes && parentNode.expanded);
  }

  this.invalidateLayoutTree();

  //return the last created node
  return $predecessor;
};

scout.Tree.prototype._$buildNode = function(node, $parent) {
  var level = $parent ? parseFloat($parent.attr('data-level')) + 1 : 0;

  var $node = $.makeDiv('tree-node')
    .data('node', node)
    .attr('data-nodeid', node.id)
    .attr('data-level', level)
    .css('padding-left', this._computeTreeItemPaddingLeft(level));
  node.$node = $node;

  this._decorateNode(node);
  this._renderTreeItemControl($node);

  if (this.checkable) {
    this._renderTreeItemCheckbox(node);
  }

  return $node;
};

scout.Tree.prototype._decorateNode = function(node) {
  var formerClasses,
    $node = node.$node;
  if (!$node) {
    // This node is not yet rendered, nothing to do
    return;
  }

  formerClasses = 'tree-node';
  if ($node.isSelected()) {
    formerClasses += ' selected';
  }
  $node.removeClass();
  $node.addClass(formerClasses);
  $node.addClass(node.cssClass);
  $node.toggleClass('leaf', !! node.leaf);
  $node.toggleClass('expanded', ( !! node.expanded && node.childNodes.length > 0));
  $node.setEnabled( !! node.enabled);

  // Replace only the "text part" of the node, leave control and checkbox untouched
  var preservedChildren = $node.children('.tree-node-control,.tree-node-checkbox').detach();
  $node.empty();
  if (node.htmlEnabled) {
    $node.html(node.text);
  } else {
    $node.textOrNbsp(node.text);
  }
  $node.prepend(preservedChildren);

  scout.helpers.legacyStyle(node, $node);

  if (scout.strings.hasText(node.tooltipText)) {
    $node.attr('title', node.tooltipText);
  }

  // apply node filter
  if (this._applyFiltersForNode(node)) {
    var newInvisibleNodes = [];
    if (!node.filterAccepted) {
      newInvisibleNodes.push(node);
    }
    this._nodesFiltered(newInvisibleNodes);
  }
  this._renderNodeFilterAccepted(node);

  this.dragAndDropHandler.install($node);
  // TODO BSH More attributes...
  // iconId
  // tooltipText

  // If parent node is marked as 'show-all', check if any hidden child nodes remain.
  if (node.parentNode && node.parentNode.$node.hasClass('show-all')) {
    var hasHiddenNodes = node.parentNode.childNodes.some(function(childNode) {
      if (!childNode.$node || childNode.$node.hasClass('hidden')) {
        return true;
      }
      return false;
    });
    if (!hasHiddenNodes) {
      // Remove 'show-all' from parent
      node.parentNode.$node.removeClass('show-all');
    }
  }
};

scout.Tree.prototype._renderNodeChecked = function(node) {
  if (!node.$node) {
    // if node is not rendered, do nothing
    return;
  }

  var $checkbox = node.$node
    .children('.tree-node-checkbox')
    .children('.check-box')
    .toggleClass('checked', node.checked);
};

scout.Tree.prototype.checkNode = function(node, checked, suppressSend) {
  var updatedNodes = [];
  if (!this.enabled || !this.checkable || !node.enabled || node.checked === checked) {
    return updatedNodes;
  }
  if (!this.multiCheck && checked) {
    for (var i = 0; i < this.checkedNodes.length; i++) {
      this.checkedNodes[i].checked = false;
      this._updateMarkChildrenChecked(this.checkedNodes[i], false, false, true);
      updatedNodes.push(this.checkedNodes[i]);
      if (this.rendered) {
        this._renderNodeChecked(this.checkedNodes[i]);
      }
    }
    this.checkedNodes = [];
  }
  node.checked = checked;
  if (node.checked) {
    this.checkedNodes.push(node);
  }
  updatedNodes.push(node);
  this._updateMarkChildrenChecked(node, false, checked, true);
  if (!suppressSend) {
    updatedNodes = updatedNodes.concat(this.checkChildren(node, checked));
    this._sendNodesChecked(updatedNodes);
  }
  if (this.rendered) {
    this._renderNodeChecked(node);
  }
  return updatedNodes;
};

scout.Tree.prototype.checkChildren = function(node, checked) {
  var updatedNodes = [];
  if (this.autoCheckChildren && node) {
    for (var i = 0; i < node.childNodes.length; i++) {
      updatedNodes = updatedNodes.concat(this.checkNode(node.childNodes[i], checked, true));
    }
  }
  return updatedNodes;
};

scout.Tree.prototype._sendNodesChecked = function(nodes) {
  var data = {
    nodes: []
  };

  for (var i = 0; i < nodes.length; i++) {
    data.nodes.push({
      nodeId: nodes[i].id,
      checked: nodes[i].checked
    });
  }

  this.remoteHandler(this.id, 'nodesChecked', data);
};

scout.Tree.prototype._renderTreeItemControl = function($node) {
  var $control = $node.prependDiv('tree-node-control');
  if (this.checkable) {
    $control.addClass('checkable');
  }
};

scout.Tree.prototype._renderTreeItemCheckbox = function(node) {
  var $node = node.$node,
    $controlItem = $node.prependDiv('tree-node-checkbox');
  var $checkboxDiv = $.makeDiv('check-box')
    .appendTo($controlItem)
    .toggleClass('checked', node.checked)
    .toggleClass('disabled', !(this.enabled && node.enabled));

  if (node.childrenChecked) {
    $checkboxDiv.toggleClass('children-checked', true);
  } else {
    $checkboxDiv.toggleClass('children-checked', false);
  }
};

scout.Tree.prototype._onContextMenu = function(event) {
  if (this.$data.is(event.target)) {
    this._showContextMenu(event, ['Tree.EmptySpace']);
  } else {
    this._showContextMenu(event, ['Tree.SingleSelection', 'Tree.MultiSelection']);
  }
};

scout.Tree.prototype._showContextMenu = function(event, allowedTypes) {
  event.preventDefault();
  event.stopPropagation();
  var func = function func(event, allowedTypes) {
    var filteredMenus = this._filterMenus(allowedTypes, true),
      $part = $(event.currentTarget);
    if (filteredMenus.length === 0) {
      return; // at least one menu item must be visible
    }
    var popup = new scout.ContextMenuPopup(this.session, {
      menuItems: filteredMenus,
      location: {
        x: event.pageX,
        y: event.pageY
      },
      $anchor: $part
    });
    popup.render();
  }.bind(this);

  scout.menus.showContextMenuWithWait(this.session, func, event, allowedTypes);
};

scout.Tree.prototype._onNodeMouseDown = function(event) {
  this._doubleClickSupport.mousedown(event);
  if (this._doubleClickSupport.doubleClicked()) {
    //don't execute on double click events
    return false;
  }

  var $node = $(event.currentTarget);
  var node = $node.data('node');

  this.selectNodes(node);

  if (this.checkable && this._isCheckboxClicked(event)) {
    this.checkNode(node, !node.checked);
  }
  return true;
};

scout.Tree.prototype._onNodeMouseUp = function(event) {
  if (this._doubleClickSupport.doubleClicked()) {
    //don't execute on double click events
    return false;
  }

  var $node = $(event.currentTarget);
  var node = $node.data('node');

  this.remoteHandler(this.id, 'nodeClicked', {
    nodeId: node.id
  });
  return true;
};

scout.Tree.prototype._isCheckboxClicked = function(event) {
  return $(event.target).is('.check-box');
};

scout.Tree.prototype._onNodeDoubleClick = function(event) {
  var $node = $(event.currentTarget);
  var node = $node.data('node');
  var expanded = !$node.hasClass('expanded');

  if (this._breadcrumbEnabled) {
    return;
  }

  this.remoteHandler(this.id, 'nodeAction', {
    nodeId: node.id
  });

  this.setNodeExpanded(node, expanded);
};

scout.Tree.prototype._onNodeControlMouseDown = function(event) {
  this._doubleClickSupport.mousedown(event);
  if (this._doubleClickSupport.doubleClicked()) {
    //don't execute on double click events
    return false;
  }

  var $node = $(event.currentTarget).parent();
  var node = $node.data('node');
  var expanded = !$node.hasClass('expanded');
  var expansionOpts = {};

  // Click on "show all" control shows all nodes
  if ($node.hasClass('show-all')) {
    if (event.ctrlKey || event.shiftKey) {
      // Collapse
      expanded = false;
      expansionOpts.collapseChildNodes = true;
    } else {
      // Show all nodes
      this._showAllNodes(node);
      return false;
    }
  }

  this.selectNodes(node);
  this.setNodeExpanded(node, expanded, expansionOpts);

  // prevent bubbling to _onNodeMouseDown()
  $.suppressEvent(event);
  // ...but return true, so Outline.js can override this method and check if selection has been changed or not
  return true;
};

scout.Tree.prototype._onNodeControlMouseUp = function(event) {
  // prevent bubbling to _onNodeMouseUp()
  return false;
};

scout.Tree.prototype._onNodeControlDoubleClick = function(event) {
  // prevent bubbling to _onNodeDoubleClick()
  return false;
};

scout.Tree.prototype._showAllNodes = function(parentNode) {
  parentNode.$node.removeClass('show-all');

  var updateFunc = function() {
    this.revalidateLayoutTree();
    this.revealSelection();
  }.bind(this);

  // Show all nodes for this parent
  for (var i = 0; i < parentNode.childNodes.length; i++) {
    var childNode = parentNode.childNodes[i];

    // skip if already visible
    if (childNode.$node.css('display') !== 'none') {
      continue;
    }

    childNode.$node.removeClass('hidden');

    // only animate small trees
    if (parentNode.childNodes.length < 100) {
      var h = childNode.$node.outerHeight(),
        p = childNode.$node.css('padding-top');

      // make height 0
      childNode.$node
        .outerHeight(0)
        .css('padding-top', '0')
        .css('padding-bottom', '0');

      // animate to original height
      childNode.$node
        .animateAVCSD('padding-top', p, null, null, 200)
        .animateAVCSD('padding-bottom', p, null, null, 200)
        .animateAVCSD('height', h, null, updateFunc, 200);
    }

    // only first animated element should handle scrollbar and visibility of selection
    updateFunc = null;
  }

  // without animation
  this.revalidateLayoutTree();
  this.revealSelection();
};

scout.Tree.prototype._updateItemPath = function() {
  var $selectedNodes, $node, level;

  // first remove and select selected
  this.$data.find('.tree-node').removeClass('parent children group');

  // if no selection: mark all top elements as children
  if (this.selectedNodes.length === 0) {
    this.$data.children().addClass('children');
    return;
  }

  // find direct children
  $selectedNodes = this.$selectedNodes();
  $node = $selectedNodes.next();
  level = parseFloat($selectedNodes.attr('data-level'));
  while ($node.length > 0) {
    if ($node.hasClass('animation-wrapper')) {
      $node = $node.children().first();
    }
    var l = parseFloat($node.attr('data-level'));
    if (l === level + 1) {
      $node.addClass('children');
    } else if (l === level) {
      break;
    }
    if ($node.next().length === 0 && $node.parent().hasClass('animation-wrapper')) {
      // If there is no next node but we are inside an animationWrapper, step out the wrapper
      $node = $node.parent();
    }
    $node = $node.next();
  }

  // find parents
  var $ultimate;
  if ($selectedNodes.parent().hasClass('animation-wrapper')) {
    //If node expansion animation is in progress, the nodes are wrapped by a div
    $node = $selectedNodes.parent().prev();
  } else {
    $node = $selectedNodes.prev();
  }

  while ($node.length > 0) {
    var k = parseFloat($node.attr('data-level'));
    if (k < level) {
      if ($node.data('node').nodeType == 'table' && !this._breadcrumbEnabled) {
        break;
      }

      $node.addClass('parent');
      level = k;
      $ultimate = $node;
    }
    if ($node.parent().hasClass('animation-wrapper')) {
      $node = $node.parent();
    }
    $node = $node.prev();
  }

  // find group with same ultimate parent
  $ultimate = $ultimate || $selectedNodes;
  $node = $ultimate;
  level = $node.attr('data-level');
  while ($node.length > 0) {
    $node.addClass('group');
    if ($node.next().length === 0 && $node.parent().hasClass('animation-wrapper')) {
      // If there is no next node but we are inside an animationWrapper, step out the wrapper
      $node = $node.parent();
    }
    $node = $node.next();
    if ($node.hasClass('animation-wrapper')) {
      $node = $node.children().first();
    }

    var m = parseFloat($node.attr('data-level'));
    if (m <= level) {
      break;
    }
  }
};

scout.Tree.prototype.$selectedNodes = function() {
  return this.$data.find('.selected');
};

scout.Tree.prototype.$nodes = function() {
  return this.$data.find('.tree-node');
};

/**
 * @param filter object with createKey() and accept()
 */
scout.Tree.prototype.addFilter = function(filter) {
  if (this._filters.indexOf(filter) < 0) {
    this._filters.push(filter);
  }
};

scout.Tree.prototype.removeFilter = function(filter) {
  scout.arrays.remove(this._filters, filter);
};

scout.Tree.prototype.filter = function() {
  var i, useAnimation,
    that = this,
    nodeCount = 0,
    nodesToHide = [],
    nodesToShow = [];

  // Filter rows
  this._visitNodes(this.nodes, function(node) {
    var $node = node.$node;
    if (!$node) {
      // filter may only be called for rendered nodes because the filter may want to use the actual html content
      // if a node is collapsed, it is not rendered -> filter is called when node gets expanded
      return;
    }
    that._applyFiltersForNode(node);
    if (node.filterAccepted) {
      if ($node.hasClass('invisible')) {
        nodesToShow.push(node);
      }
      nodeCount++;
    } else {
      if (!$node.hasClass('invisible')) {
        nodesToHide.push(node);
      }
    }
  });

  // Show / hide nodes that changed their state during filtering
  useAnimation = ((nodesToShow.length + nodesToHide.length) <= that._animationNodeLimit);
  nodesToHide.forEach(function(node) {
    that.hideNode(node.$node, useAnimation);
  });
  nodesToShow.forEach(function(node) {
    that.showNode(node.$node, useAnimation);
  });

  this._nodesFiltered(nodesToHide);
};

scout.Tree.prototype._nodesFiltered = function(invisibleNodes) {
  // non visible nodes must be deselected
  this.deselectNodes(invisibleNodes);
};

scout.Tree.prototype._nodeAcceptedByFilters = function(node) {
  for (var i = 0; i < this._filters.length; i++) {
    var filter = this._filters[i];
    if (!filter.accept(node.$node)) {
      return false;
    }
  }
  return true;
};

/**
 * @returns {Boolean} true if node state has changed, false if not
 */
scout.Tree.prototype._applyFiltersForNode = function(node) {
  if (this._nodeAcceptedByFilters(node)) {
    if (!node.filterAccepted) {
      node.filterAccepted = true;
      return true;
    }
  } else {
    if (node.filterAccepted) {
      // flag is necessary to get correct filter count even when animation is still in progress
      // and to store filter state to prevent unnecessary events
      node.filterAccepted = false;
      return true;
    }
  }
  return false;
};

scout.Tree.prototype._renderNodeFilterAccepted = function(node) {
  if (node.filterAccepted) {
    this.showNode(node.$node);
  } else {
    this.hideNode(node.$node);
  }
};

scout.Tree.prototype.showNode = function($node, useAnimation) {
  var that = this,
    node = $node.data('node');
  if (!$node.hasClass('invisible')) {
    return;
  }

  if (useAnimation) {
    $node.stop().slideDown({
      duration: 250,
      complete: function() {
        $node.removeClass('invisible');
        that.invalidateLayoutTree();
      }
    });
  } else {
    $node.showFast();
    $node.removeClass('invisible');
    that.invalidateLayoutTree();
  }
};

scout.Tree.prototype.hideNode = function($node, useAnimation) {
  var that = this,
    node = $node.data('node');
  if ($node.hasClass('invisible')) {
    return;
  }

  if (useAnimation) {
    $node.stop().slideUp({
      duration: 250,
      complete: function() {
        $node.addClass('invisible');
        that.invalidateLayoutTree();
      }
    });
  } else {
    $node.hideFast();
    $node.addClass('invisible');
    that.invalidateLayoutTree();
  }
};

scout.Tree.prototype._nodesToIds = function(nodes) {
  return nodes.map(function(node) {
    return node.id;
  });
};

scout.Tree.prototype._nodesByIds = function(ids) {
  return ids.map(function(id) {
    return this.nodesMap[id];
  }.bind(this));
};

scout.Tree.prototype._onRequestFocus = function() {
  this.session.focusManager.requestFocus(this.$container);
};

scout.Tree.prototype._onScrollToSelection = function() {
  this.revealSelection();
};

/**
 * Called by _onNodesUpdated for every updated node. The function is expected to apply
 * all updated properties from the updatedNode to the oldNode. May be overridden by
 * subclasses so update their specific node properties.
 *
 * @param oldNode
 *          The target node to be updated
 * @param updatedNode
 *          The new node with potentially updated properties. Default values are already applied!
 * @returns
 *          true if at least one property has changed, false otherwise. This value is used to
 *          determine if the node has to be rendered again.
 */
scout.Tree.prototype._applyUpdatedNodeProperties = function(oldNode, updatedNode) {
  // Note: We only update _some_ of the properties, because everything else will be handled
  // with separate events. --> See also: JsonTree.java/handleModelNodesUpdated()
  var propertiesChanged = false;
  if (oldNode.leaf !== updatedNode.leaf) {
    oldNode.leaf = updatedNode.leaf;
    propertiesChanged = true;
  }
  if (oldNode.enabled !== updatedNode.enabled) {
    oldNode.enabled = updatedNode.enabled;
    oldNode.$node.children('.tree-node-checkbox')
      .children('.check-box')
      .toggleClass('disabled', !(this.enabled && oldNode.enabled));
    propertiesChanged = true;
  }
  return propertiesChanged;
};

scout.Tree.prototype.lazyAddChildNodesToTree = function(lazyAddChildNodesToTree) {
  this._lazyAddChildNodesToTree += (lazyAddChildNodesToTree ? 1 : -1);
};

scout.Tree.prototype.onModelAction = function(event) {
  if (event.type === 'nodesInserted') {
    this._onNodesInserted(event.nodes, event.commonParentNodeId);
  } else if (event.type === 'nodesUpdated') {
    this._onNodesUpdated(event.nodes, event.commonParentNodeId);
  } else if (event.type === 'nodesDeleted') {
    this._onNodesDeleted(event.nodeIds, event.commonParentNodeId);
  } else if (event.type === 'allChildNodesDeleted') {
    this._onAllChildNodesDeleted(event.commonParentNodeId);
  } else if (event.type === 'nodesSelected') {
    this._onNodesSelected(event.nodeIds);
  } else if (event.type === 'nodeExpanded') {
    this._onNodeExpanded(event.nodeId, event.expanded, event.recursive);
  } else if (event.type === 'nodeChanged') {
    this._onNodeChanged(event.nodeId, event);
  } else if (event.type === 'nodesChecked') {
    this._onNodesChecked(event.nodes);
  } else if (event.type === 'childNodeOrderChanged') {
    this._onChildNodeOrderChanged(event.parentNodeId, event.childNodeIds);
  } else if (event.type === 'requestFocus') {
    this._onRequestFocus();
  } else if (event.type === 'scrollToSelection') {
    this._onScrollToSelection();
  } else {
    scout.Tree.parent.prototype.onModelAction.call(this, event);
  }
};

/* --- STATIC HELPERS ------------------------------------------------------------- */

/**
 * @memberOf scout.Tree
 */
scout.Tree.collectSubtree = function($rootNode, includeRootNodeInResult) {
  if (!$rootNode) {
    return $();
  }
  var rootLevel = parseFloat($rootNode.attr('data-level'));
  // Find first node after the root element that has the same or a lower level
  var $nextNode = $rootNode.next();
  while ($nextNode.length > 0) {
    var level = parseFloat($nextNode.attr('data-level'));
    if (isNaN(level) || level <= rootLevel) {
      break;
    }
    $nextNode = $nextNode.next();
  }

  // The result set consists of all nodes between the root node and the found node
  var $result = $rootNode.nextUntil($nextNode);
  if (includeRootNodeInResult === undefined || includeRootNodeInResult) {
    $result = $result.add($rootNode);
  }
  return $result;
};
