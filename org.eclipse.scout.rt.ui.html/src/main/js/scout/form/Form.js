scout.Form = function() {
  scout.Form.parent.call(this);
  this.rootGroupBox;
  this._addAdapterProperties(['rootGroupBox', 'views', 'dialogs', 'messageBoxes']);
  this._locked;
  this._$glassPane;
  this._formController;
};
scout.inherits(scout.Form, scout.ModelAdapter);

scout.Form.prototype.init = function(model, session) {
  scout.Form.parent.prototype.init.call(this, model, session);
  // FIXME BSH Improve this logic - how about a mid-sized menubar? See also: GroupBox.js/init()
  if (this.isDialog() || this.searchForm) {
    this.rootGroupBox.menuBar.bottom();
    this.rootGroupBox.menuBar.large();
  }

  this._formController = new scout.FormController(this, session,
      function() { // callback to access dialogs attached to this Form.
        return this.dialogs;
      }.bind(this),
      function() { // callback to access views attached to this Form.
        return this.views;
      }.bind(this)
    );
};

scout.Form.prototype._render = function($parent) {
  if (this.isDialog()) {
    // FIXME BSH Try to consolidate management of glass-panes in desktop (but: Session.showFatalMessage())
    this._$glassPane = scout.fields.new$Glasspane(this.session.uiSessionId).appendTo($parent);
    $parent = this._$glassPane;
  }

  this.$container = $('<div>')
    .appendTo($parent)
    .addClass(this.displayHint === 'dialog' ? 'dialog' : 'form') // FIXME AWE: (modal dialog) rename class 'form' to view so we can use the displayHint as class-name
  .data('model', this);

  if (this.isDialog()) {
    var $handle = this.$container.appendDiv('drag-handle');
    this.$container.makeDraggable($handle);

    if (this.closable) {
      this.$container.appendDiv('closable')
        .on('click', function() {
          this.session.send(this.id, 'formClosing');
        }.bind(this));
    }
    this.$container.resizable({
      resize: function(event, ui) {
        this.htmlComp.revalidateLayout();
      }.bind(this)
    });
    this._updateDialogTitle();
  }

  this.htmlComp = new scout.HtmlComponent(this.$container, this.session);
  this.htmlComp.setLayout(new scout.FormLayout(this));
  this.htmlComp.pixelBasedSizing = false;
  this.rootGroupBox.render(this.$container);

  if (this._locked) {
    this.disable();
  }

  if (this.isDialog()) {
    this.$container.addClass('shown');
    $.log.warn('startInstall');
    this._$glassPane.installFocusContext('auto', this.session.uiSessionId);
  }

  // Display all Forms registered on this Form.
  this._formController.showAll();
};
scout.Form.prototype._renderProperties = function() {
  this._renderInitialFocus(this.initialFocus);
};

scout.Form.prototype._updateDialogTitle = function() {
  if (this.title || this.subTitle) {
    var $titles = getOrAppendChildDiv(this.$container, 'title-box');
    // Render title
    if (this.title) {
      getOrAppendChildDiv($titles, 'title').text(this.title);
    } else {
      removeChildDiv($titles, 'title');
    }
    // Render subTitle
    if (this.title) {
      getOrAppendChildDiv($titles, 'sub-title').text(this.subTitle);
    } else {
      removeChildDiv($titles, 'sub-title');
    }
  } else {
    removeChildDiv(this.$container, 'title-box');
  }
  // Layout could have been changed, e.g. if subtitle becomes visible
  this.invalidateLayoutTree();

  // ----- Helper functions -----

  function getOrAppendChildDiv($parent, cssClass) {
    var $div = $parent.children('.' + cssClass);
    if ($div.length === 0) {
      $div = $parent.appendDiv(cssClass);
    }
    return $div;
  }

  function removeChildDiv($parent, cssClass) {
    $parent.children('.' + cssClass).remove();
  }
};

scout.Form.prototype.isDialog = function() {
  return this.displayHint === 'dialog';
};

scout.Form.prototype._isClosable = function() {
  var i, btn,
    systemButtons = this.rootGroupBox.systemButtons;
  for (i = 0; i < systemButtons.length; i++) {
    btn = systemButtons[i];
    if (btn.visible &&
      btn.systemType === scout.Button.SystemType.CANCEL ||
      btn.systemType === scout.Button.SystemType.CLOSE) {
      return true;
    }
  }
  return false;
};

scout.Form.prototype.onResize = function() {
  $.log.trace('(Form#onResize) window was resized -> layout Form container');
  var htmlComp = scout.HtmlComponent.get(this.$container);
  var $parent = this.$container.parent();
  var parentSize = new scout.Dimension($parent.width(), $parent.height());
  htmlComp.setSize(parentSize);
};

scout.Form.prototype.appendTo = function($parent) {
  this.$container.appendTo($parent);
};

scout.Form.prototype._remove = function() {
  if (this._$glassPane) {
    this._$glassPane.fadeOutAndRemove();
  }

  if (this.tab) {
    this.session.desktop._removeTab(this.tab);
  }

  scout.Form.parent.prototype._remove.call(this);
};

scout.Form.prototype._renderTitle = function() {
  if (this.isDialog()) {
    this._updateDialogTitle();
  }
};

scout.Form.prototype._renderSubTitle = function() {
  if (this.isDialog()) {
    this._updateDialogTitle();
  }
};

scout.Form.prototype._renderIconId = function() {
  // TODO render icon
};

scout.Form.prototype._renderInitialFocus = function(formFieldId) {
  var formField = this.session.getOrCreateModelAdapter(formFieldId, this);
  if (formField) {
    formField.$field.focus();
  }
};

scout.Form.prototype._onFormClosed = function(event) {
  this.destroy();
};

scout.Form.prototype._onRequestFocus = function(formFieldId) {
  var formField = this.session.getOrCreateModelAdapter(formFieldId, this);
  if (formField) {
    formField.$field.focus();
  }
};

scout.Form.prototype.onModelAction = function(event) {
  if (event.type === 'formClosed') {
    this._onFormClosed(event);
  } else if (event.type === 'requestFocus') {
    this._onRequestFocus(event.formField);
  } else if (event.type === 'formShow') {
    this._formController.addAndShow(event.form);
  } else if (event.type === 'formHide') {
    this._formController.removeAndHide(event.form);
  } else if (event.type === 'formActivate') {
    this._formController.activateForm(event.form);
  } else {
    $.log.warn('Model event not handled. Widget: Form. Event: ' + event.type + '.');
  }
};
